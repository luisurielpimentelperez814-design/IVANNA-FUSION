/*
 * IVANNA-FUSION / Ω_in
 * omega_effect.cpp — Audio Effect Plugin (productor del ring buffer SPSC)
 *
 * ARQUITECTURA (3 capas):
 *   1. Este archivo (libomega_effect.so): vive dentro de audioserver,
 *      intercepta el audio del sistema. SOLO mueve datos al/del ring
 *      buffer compartido. JAMÁS ejecuta inferencia de IA aquí — eso
 *      bloquearía el hilo de tiempo real de AudioFlinger y produciría
 *      xruns (cortes de audio).
 *   2. omega_daemon (root, proceso separado): lee del ring buffer de
 *      entrada, ejecuta la inferencia Ω_in, escribe en el ring buffer
 *      de salida.
 *   3. APK (OmegaMagiskBridge.kt): controla parámetros vía Unix Domain
 *      Socket, no participa en el hot path de audio.
 *
 * REGLA DE ORO: si el daemon no entrega un bloque de salida a tiempo
 * (ring_out vacío), este efecto pasa el audio de entrada sin modificar
 * (bypass) en vez de silenciar o bloquear. Nunca se espera/duerme aquí.
 *
 * Nota sobre el binding JNI vs Effect HAL: las funciones
 * Java_com_ivannafusion_OmegaEffect_* que ya consumía la app (nativeInit,
 * nativeSetActive, etc.) se conservan para no romper compatibilidad con
 * código Kotlin existente, pero ahora delegan en el mismo estado y ring
 * buffers compartidos con omega_daemon, en vez de procesar localmente.
 */

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstring>
#include <fcntl.h>
#include <sys/mman.h>
#include <unistd.h>

#include "omega_shared.h"

#define LOG_TAG "OmegaEffect"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr const char* kShmPath = "/data/local/tmp/omega_shared_mem";

OmegaSharedState* g_shared = nullptr;
int               g_shm_fd  = -1;

std::atomic<bool>  g_is_active{false};
std::atomic<float> g_intensity{0.8f};
std::atomic<float> g_vocoder_mix{0.8f};
std::atomic<uint32_t> g_consecutive_underruns{0};

bool mapSharedMemory() {
    if (g_shared != nullptr) return true;

    g_shm_fd = open(kShmPath, O_RDWR);
    if (g_shm_fd < 0) {
        return false;
    }

    void* mapped = mmap(nullptr, sizeof(OmegaSharedState),
                         PROT_READ | PROT_WRITE, MAP_SHARED, g_shm_fd, 0);
    if (mapped == MAP_FAILED) {
        LOGE("mmap falló al mapear memoria compartida con omega_daemon");
        close(g_shm_fd);
        g_shm_fd = -1;
        return false;
    }

    g_shared = static_cast<OmegaSharedState*>(mapped);
    LOGI("omega_effect: memoria compartida mapeada correctamente");
    return true;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_OmegaEffect_nativeInit(JNIEnv*, jobject) {
    LOGI("Inicializando Omega Effect (productor del ring buffer SPSC)");
    g_is_active.store(false);
    mapSharedMemory();
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeRelease(JNIEnv*, jobject) {
    LOGI("Liberando Omega Effect");
    g_is_active.store(false);
    if (g_shared != nullptr) {
        munmap(g_shared, sizeof(OmegaSharedState));
        g_shared = nullptr;
    }
    if (g_shm_fd >= 0) {
        close(g_shm_fd);
        g_shm_fd = -1;
    }
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeSetActive(JNIEnv*, jobject, jboolean active) {
    g_is_active.store(active);
    if (g_shared != nullptr) g_shared->is_processing.store(active);
    LOGI("Efecto activo: %s", active ? "SI" : "NO");
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeSetIntensity(JNIEnv*, jobject, jfloat intensity) {
    g_intensity.store(intensity);
    if (g_shared != nullptr) g_shared->intensity.store(intensity);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeSetVocoderMix(JNIEnv*, jobject, jfloat mix) {
    g_vocoder_mix.store(mix);
    if (g_shared != nullptr) g_shared->vocoder_mix.store(mix);
}

// process(): único punto en el hot path. Nunca bloquea, nunca hace
// malloc/free, nunca ejecuta inferencia. Push no bloqueante al ring de
// entrada, pop no bloqueante del ring de salida; si no hay bloque
// procesado disponible, el buffer ya contiene el audio original
// (bypass automático, sin silencios).
JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeProcessAudio(
        JNIEnv* env, jobject, jfloatArray audio_buffer, jint buffer_size) {

    jfloat* buffer = env->GetFloatArrayElements(audio_buffer, nullptr);
    if (buffer == nullptr) return;

    if (!g_is_active.load(std::memory_order_relaxed)) {
        env->ReleaseFloatArrayElements(audio_buffer, buffer, JNI_ABORT);
        return;
    }

    if (g_shared == nullptr && !mapSharedMemory()) {
        env->ReleaseFloatArrayElements(audio_buffer, buffer, JNI_ABORT);
        return;
    }

    if (g_shared->bypass_enabled.load(std::memory_order_relaxed)) {
        env->ReleaseFloatArrayElements(audio_buffer, buffer, JNI_ABORT);
        return;
    }

    int samples = (buffer_size < OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS)
                      ? buffer_size
                      : OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS;

    g_shared->ring_in.tryPush(buffer, samples, &g_shared->input_buffer[0][0]);

    bool got_processed = g_shared->ring_out.tryPop(
        buffer, samples, &g_shared->output_buffer[0][0]);

    if (got_processed) {
        g_consecutive_underruns.store(0, std::memory_order_relaxed);
    } else {
        g_consecutive_underruns.fetch_add(1, std::memory_order_relaxed);
    }

    env->ReleaseFloatArrayElements(audio_buffer, buffer, 0);
}

} // extern "C"

#include <jni.h>
#include <android/log.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <atomic>
#include <cstring>
#include <thread>
#include <chrono>

#include "omega_shared.h"

#define LOG_TAG "OmegaDaemon"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Estado global del daemon
static OmegaSharedState* g_shared = nullptr;
static int g_shm_fd = -1;
static std::atomic<bool> g_running{false};
static std::thread g_process_thread;

// Buffer temporal de procesamiento
static float g_process_buf[OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS];

// Inicializar memoria compartida usando mmap con archivo
bool init_shared_memory() {
    const char* shm_path = "/data/local/tmp/omega_shared_mem";
    
    // Crear/abrir archivo para mmap
    g_shm_fd = open(shm_path, O_CREAT | O_RDWR, 0666);
    if (g_shm_fd < 0) {
        LOGE("Error abriendo archivo de memoria compartida: %s", shm_path);
        return false;
    }
    
    // Ajustar tamaño del archivo
    if (ftruncate(g_shm_fd, sizeof(OmegaSharedState)) < 0) {
        LOGE("Error ajustando tamaño del archivo");
        close(g_shm_fd);
        return false;
    }
    
    // Mapear el archivo en memoria
    g_shared = static_cast<OmegaSharedState*>(
        mmap(nullptr, sizeof(OmegaSharedState), PROT_READ | PROT_WRITE, MAP_SHARED, g_shm_fd, 0)
    );
    
    if (g_shared == MAP_FAILED) {
        LOGE("Error mapeando memoria compartida");
        close(g_shm_fd);
        g_shared = nullptr;
        return false;
    }
    
    // Inicializar estado si es nuevo
    new (g_shared) OmegaSharedState();
    
    LOGI("Memoria compartida inicializada correctamente");
    return true;
}

// Liberar memoria compartida
void cleanup_shared_memory() {
    if (g_shared != nullptr && g_shared != MAP_FAILED) {
        munmap(g_shared, sizeof(OmegaSharedState));
        g_shared = nullptr;
    }
    if (g_shm_fd >= 0) {
        close(g_shm_fd);
        g_shm_fd = -1;
    }
}

// Hilo de procesamiento de audio
void process_audio_thread() {
    LOGI("Hilo de procesamiento iniciado");
    
    while (g_running.load()) {
        if (!g_shared->is_processing.load() || g_shared->bypass_enabled.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
            continue;
        }
        
        uint32_t write_pos = g_shared->write_pos.load();
        uint32_t read_pos = g_shared->read_pos.load();
        
        if (write_pos == read_pos) {
            // No hay datos para procesar
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
            continue;
        }
        
        // Copiar datos del buffer de entrada
        int total_samples = OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS;
        std::memcpy(g_process_buf, g_shared->input_buffer[read_pos % OMEGA_BUFFER_SLOTS], 
                    total_samples * sizeof(float));
        
        // TODO: Implementar procesamiento real aquí
        // - Sliced Wasserstein
        // - Complex 1D CNN
        // - Vocoder Tiny
        
        float intensity = g_shared->intensity.load();
        for (int i = 0; i < total_samples; i++) {
            g_process_buf[i] *= intensity;
        }
        
        // Copiar resultado al buffer de salida
        std::memcpy(g_shared->output_buffer[read_pos % OMEGA_BUFFER_SLOTS], 
                    g_process_buf, total_samples * sizeof(float));
        
        // Avanzar posición de lectura
        g_shared->read_pos.store(read_pos + 1);
        
        // Actualizar latencia
        g_shared->current_latency_ms.store(10.0f); // Placeholder
    }
    
    LOGI("Hilo de procesamiento detenido");
}

extern "C" {

// Iniciar el daemon
JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeStart(JNIEnv* env, jobject thiz) {
    if (g_running.load()) {
        LOGI("Daemon ya está corriendo");
        return JNI_TRUE;
    }
    
    if (!init_shared_memory()) {
        return JNI_FALSE;
    }
    
    g_running.store(true);
    g_process_thread = std::thread(process_audio_thread);
    
    LOGI("Daemon Omega iniciado");
    return JNI_TRUE;
}

// Detener el daemon
JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeStop(JNIEnv* env, jobject thiz) {
    if (!g_running.load()) {
        return;
    }
    
    g_running.store(false);
    
    if (g_process_thread.joinable()) {
        g_process_thread.join();
    }
    
    cleanup_shared_memory();
    
    LOGI("Daemon Omega detenido");
}

// Establecer estado de procesamiento
JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeSetProcessing(JNIEnv* env, jobject thiz, jboolean active) {
    if (g_shared != nullptr) {
        g_shared->is_processing.store(active);
    }
}

// Establecer intensidad
JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeSetIntensity(JNIEnv* env, jobject thiz, jfloat intensity) {
    if (g_shared != nullptr) {
        g_shared->intensity.store(intensity);
    }
}

// Obtener telemetría
JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeGetTemperature(JNIEnv* env, jobject thiz) {
    if (g_shared != nullptr) {
        return g_shared->current_temperature.load();
    }
    return 0.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeGetLatency(JNIEnv* env, jobject thiz) {
    if (g_shared != nullptr) {
        return g_shared->current_latency_ms.load();
    }
    return 0.0f;
}

} // extern "C"

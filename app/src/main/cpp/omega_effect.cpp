#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstring>

#define LOG_TAG "OmegaEffect"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Estado global del efecto
static std::atomic<bool> g_is_active{false};
static std::atomic<float> g_intensity{0.8f};
static std::atomic<float> g_vocoder_mix{0.8f};

extern "C" {

// Inicializar el efecto
JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_OmegaEffect_nativeInit(JNIEnv* env, jobject thiz) {
    LOGI("Inicializando Omega Effect");
    g_is_active.store(false);
    return JNI_TRUE;
}

// Liberar recursos
JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeRelease(JNIEnv* env, jobject thiz) {
    LOGI("Liberando Omega Effect");
    g_is_active.store(false);
}

// Activar/desactivar procesamiento
JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeSetActive(JNIEnv* env, jobject thiz, jboolean active) {
    g_is_active.store(active);
    LOGI("Efecto activo: %s", active ? "SI" : "NO");
}

// Ajustar intensidad
JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeSetIntensity(JNIEnv* env, jobject thiz, jfloat intensity) {
    g_intensity.store(intensity);
}

// Ajustar mezcla del vocoder
JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeSetVocoderMix(JNIEnv* env, jobject thiz, jfloat mix) {
    g_vocoder_mix.store(mix);
}

// Procesar buffer de audio (llamado desde Java/Kotlin)
JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEffect_nativeProcessAudio(
    JNIEnv* env, 
    jobject thiz, 
    jfloatArray audio_buffer, 
    jint buffer_size
) {
    if (!g_is_active.load()) {
        return; // Bypass si no está activo
    }

    jfloat* buffer = env->GetFloatArrayElements(audio_buffer, nullptr);
    if (buffer == nullptr) {
        return;
    }

    float intensity = g_intensity.load();
    float vocoder_mix = g_vocoder_mix.load();

    // TODO: Implementar procesamiento real aquí
    // Por ahora, solo aplicamos un gain simple como placeholder
    for (int i = 0; i < buffer_size; i++) {
        buffer[i] *= intensity;
    }

    env->ReleaseFloatArrayElements(audio_buffer, buffer, 0);
}

} // extern "C"

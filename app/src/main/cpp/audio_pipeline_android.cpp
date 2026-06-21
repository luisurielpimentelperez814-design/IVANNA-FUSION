#include <jni.h>
#include <android/log.h>
#include <string>
#include <atomic>

#define LOG_TAG "OmegaEngineNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Estado global del motor
static std::atomic<bool> is_processing{false};
static std::atomic<float> vocoder_mix{0.8f};
static std::atomic<float> device_temp{35.0f};

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_OmegaEngineBridge_nativeInit(JNIEnv* env, jobject thiz) {
    LOGI("Inicializando motor Omega_in Edge AI");
    is_processing.store(false);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEngineBridge_nativeRelease(JNIEnv* env, jobject thiz) {
    LOGI("Liberando recursos del motor Omega_in");
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEngineBridge_nativeSetProcessing(JNIEnv* env, jobject thiz, jboolean state) {
    is_processing.store(state);
    LOGI("Estado de procesamiento: %s", state ? "ACTIVO" : "INACTIVO");
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEngineBridge_nativeSetVocoderMix(JNIEnv* env, jobject thiz, jfloat mix) {
    vocoder_mix.store(mix);
    LOGI("Mezcla del vocoder ajustada a: %.2f", mix);
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_OmegaEngineBridge_nativeGetDeviceTemp(JNIEnv* env, jobject thiz) {
    return device_temp.load();
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaEngineBridge_nativeProcessAudio(
    JNIEnv* env, 
    jobject thiz, 
    jfloatArray audio_buffer, 
    jint buffer_size
) {
    if (!is_processing.load()) {
        return;
    }

    jfloat* buffer = env->GetFloatArrayElements(audio_buffer, nullptr);
    
    // TODO: Implementar pipeline Omega_in aquí
    // 1. STFT
    // 2. Sliced Wasserstein
    // 3. Complex 1D CNN (fase)
    // 4. Vocoder Tiny
    
    env->ReleaseFloatArrayElements(audio_buffer, buffer, 0);
}

} // extern "C"

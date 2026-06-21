#include <jni.h>
#include <android/log.h>

#define LOG_TAG "IVANNA_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_ivannafusion_AudioEngine_getNativeVersion(JNIEnv* env, jobject thiz) {
    LOGI("getNativeVersion called");
    return env->NewStringUTF("1.0.0");
}

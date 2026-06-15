#include <jni.h>
#include <android/log.h>
#include <sys/mman.h>
#include <unistd.h>

#define LOG_TAG "IVANNA-SHM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_memfdCreate(JNIEnv*, jobject, jstring, jint) {
    // Ya no se usa, pero mantenemos la función para evitar UnsatisfiedLinkError
    return -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_nativeFtruncate(JNIEnv*, jobject, jint, jlong) {
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_ShmManager_nativeMlock(JNIEnv*, jobject, jlong address, jlong length) {
    return mlock(reinterpret_cast<void*>(address), (size_t)length);
}

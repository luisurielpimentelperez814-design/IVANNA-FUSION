#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <android/log.h>

#define LOG_TAG "IVANNA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// --- MOTOR DSP INTEGRADO (Biquad EQ + Compressor) ---

struct BiquadFilter {
    float a0, a1, a2, b1, b2;
    float x1, x2, y1, y2;
    BiquadFilter() : a0(1), a1(0), a2(0), b1(0), b2(0), x1(0), x2(0), y1(0), y2(0) {}
    
    void setLowShelf(float freq, float sampleRate, float gainDB, float Q) {
        float A = powf(10.0f, gainDB / 40.0f);
        float w0 = 2.0f * M_PI * freq / sampleRate;
        float alpha = sinf(w0) / (2.0f * Q);
        float cosw0 = cosf(w0);
        float beta = sqrtf(A) / Q;
        
        float b0 = A * ((A + 1) - (A - 1) * cosw0 + beta * sinf(w0));
        float b1 = 2.0f * A * ((A - 1) - (A + 1) * cosw0);
        float b2 = A * ((A + 1) - (A - 1) * cosw0 - beta * sinf(w0));
        float a0 = (A + 1) + (A - 1) * cosw0 + beta * sinf(w0);
        float a1 = -2.0f * ((A - 1) + (A + 1) * cosw0);
        float a2 = (A + 1) + (A - 1) * cosw0 - beta * sinf(w0);
        
        this->a0 = b0/a0; this->a1 = b1/a0; this->a2 = b2/a0;
        this->b1 = a1/a0; this->b2 = a2/a0;
        x1=x2=y1=y2=0;
    }

    float process(float in) {
        float out = a0 * in + a1 * x1 + a2 * x2 - b1 * y1 - b2 * y2;
        x2 = x1; x1 = in; y2 = y1; y1 = out;
        return out;
    }
};

struct Compressor {
    float threshold, ratio, attack, release, makeup;
    float envelope;
    Compressor() : threshold(-20), ratio(4), attack(10), release(100), makeup(0), envelope(0) {}
        float process(float in) {
        float level = fabsf(in);
        float coeff = (level > envelope) ? 
            (1.0f - expf(-1.0f / (attack * 0.001f * 48000.0f))) : 
            (1.0f - expf(-1.0f / (release * 0.001f * 48000.0f)));
        envelope = coeff * level + (1.0f - coeff) * envelope;
        
        float gainReduction = 1.0f;
        if (envelope > threshold) {
            float over = envelope - threshold;
            float compressed = threshold + over / ratio;
            gainReduction = powf(10.0f, (compressed - envelope) / 20.0f);
        }
        return in * gainReduction * powf(10.0f, makeup / 20.0f);
    }
};

// --- ESTADO GLOBAL ---
static std::vector<BiquadFilter> eqFilters(8);
static Compressor compressor;
static float currentSampleRate = 48000.0f;
static bool isInitialized = false;

// --- FUNCIONES JNI ---

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_AudioEngine_nativeInit(JNIEnv* env, jobject thiz, jint sampleRate, jint channels) {
    currentSampleRate = (float)sampleRate;
    for(int i=0; i<8; i++) eqFilters[i].setLowShelf(1000.0f, currentSampleRate, 0.0f, 1.41f);
    isInitialized = true;
    LOGI("✅ DSP Engine initialized at %d Hz", sampleRate);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeProcessAudio(JNIEnv* env, jobject thiz, jfloatArray input, jfloatArray output, jint numFrames) {
    if (!isInitialized) return;
    
    jfloat* inBuf = env->GetFloatArrayElements(input, NULL);
    jfloat* outBuf = env->GetFloatArrayElements(output, NULL);
    
    for (int i = 0; i < numFrames * 2; i += 2) { // Stereo interleaved
        float L = inBuf[i];
        float R = inBuf[i+1];
        
        // Apply EQ (Band 0 as example for both channels)
        L = eqFilters[0].process(L);
        R = eqFilters[0].process(R);
        
        // Apply Compressor        L = compressor.process(L);
        R = compressor.process(R);
        
        outBuf[i] = L;
        outBuf[i+1] = R;
    }
    
    env->ReleaseFloatArrayElements(input, inBuf, 0);
    env->ReleaseFloatArrayElements(output, outBuf, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeSetEQGain(JNIEnv* env, jobject thiz, jint band, jfloat gainDB) {
    if (band >= 0 && band < 8) {
        eqFilters[band].setLowShelf(1000.0f, currentSampleRate, gainDB, 1.41f);
        LOGI("EQ Band %d set to %.1f dB", band, gainDB);
    }
}

// Stubs para el resto de funciones para evitar crash
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQFreq(JNIEnv*, jobject, jint, jfloat) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQQ(JNIEnv*, jobject, jint, jfloat) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQBypass(JNIEnv*, jobject, jint, jboolean) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorThreshold(JNIEnv*, jobject, jfloat f) { compressor.threshold = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorRatio(JNIEnv*, jobject, jfloat f) { compressor.ratio = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorAttack(JNIEnv*, jobject, jfloat f) { compressor.attack = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorRelease(JNIEnv*, jobject, jfloat f) { compressor.release = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorKnee(JNIEnv*, jobject, jfloat) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorMakeup(JNIEnv*, jobject, jfloat f) { compressor.makeup = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorBypass(JNIEnv*, jobject, jboolean) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetExciterDrive(JNIEnv*, jobject, jfloat) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetExciterMix(JNIEnv*, jobject, jfloat) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetExciterBypass(JNIEnv*, jobject, jboolean) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetFFTEffect(JNIEnv*, jobject, jboolean) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeReset(JNIEnv*, jobject) {}

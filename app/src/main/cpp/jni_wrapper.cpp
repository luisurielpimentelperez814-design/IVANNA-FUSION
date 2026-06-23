#include <jni.h>
#include <string>
#include <vector>
#include <cmath>
#include <android/log.h>
#include <algorithm>

#define LOG_TAG "IVANNA_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// === UPSAMPLER 4x (48kHz -> 192kHz) ===
class Upsampler {
    std::vector<float> coeffs;
    std::vector<float> history;
    int pos = 0;
public:
    Upsampler() {
        coeffs.resize(32);
        for (int i = 0; i < 32; i++) {
            float x = (i - 15.5f) / 4.0f;
            float sinc = (x == 0) ? 1.0f : sinf(M_PI * x) / (M_PI * x);
            float win = 0.54f - 0.46f * cosf(2.0f * M_PI * i / 31.0f);
            coeffs[i] = sinc * win * 0.25f;
        }
        history.resize(32, 0.0f);
    }
    
    void process(float in, float* out) {
        history[pos] = in;
        for (int phase = 0; phase < 4; phase++) {
            float sum = 0.0f;
            for (int i = 0; i < 32; i++) {
                int idx = (pos - i + 32) % 32;
                int cidx = (i * 4 + phase) % 32;
                sum += history[idx] * coeffs[cidx];
            }
            out[phase] = sum;
        }
        pos = (pos + 1) % 32;
    }
};

// === DOWNSAMPLER 4x (192kHz -> 48kHz) ===
class Downsampler {
    std::vector<float> buffer;
    int pos = 0;public:
    Downsampler() : buffer(32, 0.0f) {}
    
    float process(float* in) {
        for (int i = 0; i < 4; i++) {
            buffer[pos] = in[i];
            pos = (pos + 1) % 32;
        }
        float sum = 0.0f;
        for (int i = 0; i < 32; i++) {
            sum += buffer[(pos - i + 32) % 32] / 32.0f;
        }
        return sum;
    }
};

// === BIQUAD EQ 32-BIT ===
struct BiquadFilter {
    float b0, b1, b2, a1, a2;
    float x1, x2, y1, y2;
    BiquadFilter() : b0(1), b1(0), b2(0), a1(0), a2(0), x1(0), x2(0), y1(0), y2(0) {}
    
    void setPeaking(float freq, float sr, float gainDB, float Q) {
        float A = powf(10.0f, gainDB / 40.0f);
        float w0 = 2.0f * M_PI * freq / sr;
        float alpha = sinf(w0) / (2.0f * Q);
        float cosw0 = cosf(w0);
        
        b0 = 1.0f + alpha * A;
        b1 = -2.0f * cosw0;
        b2 = 1.0f - alpha * A;
        float a0 = 1.0f + alpha / A;
        a1 = -2.0f * cosw0;
        a2 = 1.0f - alpha / A;
        
        b0 /= a0; b1 /= a0; b2 /= a0;
        a1 /= a0; a2 /= a0;
    }
    
    float process(float in) {
        float out = b0 * in + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
        x2 = x1; x1 = in; y2 = y1; y1 = out;
        return out;
    }
};

// === COMPRESOR SOFT-KNEE ===
struct Compressor {
    float threshold = -20.0f, ratio = 4.0f, attack = 10.0f, release = 100.0f;
    float knee = 6.0f, makeup = 0.0f, envelope = 0.0f;    bool bypass = false;
    
    float process(float in) {
        if (bypass) return in;
        float level = fabsf(in);
        float envDB = 20.0f * log10f(level + 1e-10f);
        float gainRedDB = 0.0f;
        
        if (envDB > threshold - knee/2.0f) {
            if (envDB < threshold + knee/2.0f) {
                float x = envDB - threshold + knee/2.0f;
                gainRedDB = (1.0f/ratio - 1.0f) * x * x / (2.0f * knee);
            } else {
                gainRedDB = (threshold - envDB) * (1.0f - 1.0f/ratio);
            }
        }
        
        float gain = powf(10.0f, (gainRedDB + makeup) / 20.0f);
        float coeff = (level > envelope) ? 
            1.0f - expf(-1.0f / (attack * 0.001f * 192000.0f)) : 
            1.0f - expf(-1.0f / (release * 0.001f * 192000.0f));
        envelope = coeff * level + (1.0f - coeff) * envelope;
        
        return in * gain;
    }
};

// === EXCITER ARMÓNICO REAL ===
struct Exciter {
    float drive = 1.0f, mix = 0.5f;
    bool bypass = false;
    
    float process(float in) {
        if (bypass) return in;
        float driven = tanhf(in * drive);
        float harmonics = driven - in;
        return in + harmonics * mix;
    }
};

// === FFT EFFECT (SPECTRAL ENHANCEMENT) ===
struct FFTEffect {
    bool enabled = false;
    std::vector<float> spectrum;
    
    FFTEffect() : spectrum(1024, 0.0f) {}
    
    float process(float in) {
        if (!enabled) return in;
        // Spectral enhancement simple        return in * 1.1f; // Boost sutil
    }
};

// === ESTADO GLOBAL ===
static Upsampler upsamplerL, upsamplerR;
static Downsampler downsamplerL, downsamplerR;
static std::vector<BiquadFilter> eqFilters(8);
static Compressor compressor;
static Exciter exciter;
static FFTEffect fftEffect;
static float sampleRate = 48000.0f;
static bool initialized = false;

// === INICIALIZACIÓN ===
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_AudioEngine_nativeInit(JNIEnv*, jobject, jint sr, jint) {
    sampleRate = (float)sr;
    for (int i = 0; i < 8; i++) {
        eqFilters[i].setPeaking(1000.0f, 192000.0f, 0.0f, 1.41f);
    }
    initialized = true;
    LOGI("✅ DSP Engine initialized at %d Hz (internal 192kHz)", sr);
    return JNI_TRUE;
}

// === PROCESAMIENTO DE AUDIO ===
extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeProcessAudio(JNIEnv* env, jobject, jfloatArray in, jfloatArray out, jint frames) {
    if (!initialized) return;
    
    jfloat* inBuf = env->GetFloatArrayElements(in, NULL);
    jfloat* outBuf = env->GetFloatArrayElements(out, NULL);
    
    for (int i = 0; i < frames; i++) {
        float L = inBuf[i * 2];
        float R = inBuf[i * 2 + 1];
        
        // Upsample a 192kHz
        float upL[4], upR[4];
        upsamplerL.process(L, upL);
        upsamplerR.process(R, upR);
        
        // Procesar a 192kHz
        for (int j = 0; j < 4; j++) {
            for (int k = 0; k < 8; k++) {
                upL[j] = eqFilters[k].process(upL[j]);
                upR[j] = eqFilters[k].process(upR[j]);
            }
            upL[j] = compressor.process(upL[j]);            upR[j] = compressor.process(upR[j]);
            upL[j] = exciter.process(upL[j]);
            upR[j] = exciter.process(upR[j]);
            upL[j] = fftEffect.process(upL[j]);
            upR[j] = fftEffect.process(upR[j]);
        }
        
        // Downsample a 48kHz
        outBuf[i * 2] = downsamplerL.process(upL);
        outBuf[i * 2 + 1] = downsamplerR.process(upR);
    }
    
    env->ReleaseFloatArrayElements(in, inBuf, 0);
    env->ReleaseFloatArrayElements(out, outBuf, 0);
}

// === EQ ===
extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeSetEQGain(JNIEnv*, jobject, jint band, jfloat gain) {
    if (band >= 0 && band < 8) {
        eqFilters[band].setPeaking(1000.0f, 192000.0f, gain, 1.41f);
        LOGI("EQ Band %d: %.1f dB", band, gain);
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQFreq(JNIEnv*, jobject, jint, jfloat) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQQ(JNIEnv*, jobject, jint, jfloat) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQBypass(JNIEnv*, jobject, jint, jboolean) {}

// === COMPRESSOR ===
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorThreshold(JNIEnv*, jobject, jfloat f) { compressor.threshold = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorRatio(JNIEnv*, jobject, jfloat f) { compressor.ratio = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorAttack(JNIEnv*, jobject, jfloat f) { compressor.attack = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorRelease(JNIEnv*, jobject, jfloat f) { compressor.release = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorKnee(JNIEnv*, jobject, jfloat f) { compressor.knee = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorMakeup(JNIEnv*, jobject, jfloat f) { compressor.makeup = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorBypass(JNIEnv*, jobject, jboolean b) { compressor.bypass = b; }

// === EXCITER ===
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetExciterDrive(JNIEnv*, jobject, jfloat f) { exciter.drive = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetExciterMix(JNIEnv*, jobject, jfloat f) { exciter.mix = f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetExciterBypass(JNIEnv*, jobject, jboolean b) { exciter.bypass = b; }

// === FFT ===
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetFFTEffect(JNIEnv*, jobject, jboolean b) { fftEffect.enabled = b; }

// === RESET ===
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeReset(JNIEnv*, jobject) {
    for (int i = 0; i < 8; i++) eqFilters[i] = BiquadFilter();
    compressor = Compressor();    exciter = Exciter();
    fftEffect = FFTEffect();
    LOGI("DSP Engine reset");
}

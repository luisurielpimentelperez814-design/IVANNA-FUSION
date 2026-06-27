// ivanna_dsp.cpp — IVANNA FUSION PRO DSP Native Layer
// JNI bridge para com.ivanna.fusion.pro.DSPBridge
//
// FIX: nativeProcess ya NO hace new[]/delete[] por llamada.
// Los buffers de scratch L/R se pre-alloc en el contexto del DSP (Init)
// y se reutilizan cada llamada. Elimina 4 allocs de heap por frame de audio.
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include <jni.h>
#include <cmath>
#include <cstring>
#include <cstdlib>
#include <mutex>
#include <android/log.h>

#define LOG_TAG "IVANNA_DSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ─── Versión ──────────────────────────────────────────────────────────────────
static constexpr const char* kVersion = "2.1.0-FIXHEAP";

// ─── DSP Params ───────────────────────────────────────────────────────────────
namespace ivanna {

struct DSPParams {
    float drive    = 1.f;
    float wet      = 0.5f;
    float mix      = 0.7f;
    float alpha    = 1.f;
    float beta     = 0.5f;
    float gamma    = 1.f;
    float freq     = 1000.f;
    float resonance= 0.7f;
    float low      = 0.f;
    float mid      = 0.f;
    float high     = 0.f;
    float presence = 0.f;
    float master   = 1.f;
};

// ─── Biquad IIR ───────────────────────────────────────────────────────────────
struct Biquad {
    float b0=1,b1=0,b2=0,a1=0,a2=0;
    float x1=0,x2=0,y1=0,y2=0;

    void setPeaking(float freq, float sr, float gainDB, float Q) {
        float A     = std::pow(10.f, gainDB / 40.f);
        float w0    = 2.f * M_PI * freq / sr;
        float alpha = std::sin(w0) / (2.f * Q);
        float cw    = std::cos(w0);
        float a0    = 1.f + alpha / A;
        b0 = (1.f + alpha * A) / a0;
        b1 = (-2.f * cw)       / a0;
        b2 = (1.f - alpha * A) / a0;
        a1 = (-2.f * cw)       / a0;
        a2 = (1.f - alpha / A) / a0;
    }

    void setLowShelf(float freq, float sr, float gainDB) {
        float A  = std::pow(10.f, gainDB / 40.f);
        float w0 = 2.f * M_PI * freq / sr;
        float cw = std::cos(w0), sw = std::sin(w0);
        float beta = std::sqrt(A) / 0.7f;
        float a0 = (A+1) + (A-1)*cw + beta*sw;
        b0 = A*((A+1) - (A-1)*cw + beta*sw) / a0;
        b1 = 2*A*((A-1) - (A+1)*cw)         / a0;
        b2 = A*((A+1) - (A-1)*cw - beta*sw) / a0;
        a1 = -2*((A-1) + (A+1)*cw)          / a0;
        a2 = ((A+1) + (A-1)*cw - beta*sw)   / a0;
    }

    void setHighShelf(float freq, float sr, float gainDB) {
        float A  = std::pow(10.f, gainDB / 40.f);
        float w0 = 2.f * M_PI * freq / sr;
        float cw = std::cos(w0), sw = std::sin(w0);
        float beta = std::sqrt(A) / 0.7f;
        float a0 = (A+1) - (A-1)*cw + beta*sw;
        b0 = A*((A+1) + (A-1)*cw + beta*sw) / a0;
        b1 = -2*A*((A-1) + (A+1)*cw)        / a0;
        b2 = A*((A+1) + (A-1)*cw - beta*sw) / a0;
        a1 = 2*((A-1) - (A+1)*cw)           / a0;
        a2 = ((A+1) - (A-1)*cw - beta*sw)   / a0;
    }

    inline float process(float in) {
        float out = b0*in + b1*x1 + b2*x2 - a1*y1 - a2*y2;
        x2=x1; x1=in; y2=y1; y1=out;
        return out;
    }
    void reset() { x1=x2=y1=y2=0; }
};

// ─── ParametricEQ (8 bandas) ──────────────────────────────────────────────────
class ParametricEQ {
    Biquad bands[8];
    float  sr = 48000.f;
public:
    void setParams(const DSPParams& p) {
        // Bandas distribuidas: low/mid/high/presence mapean a shelves + peakings
        bands[0].setLowShelf (60.f,   sr, p.low * 12.f);
        bands[1].setPeaking  (120.f,  sr, p.low * 6.f,  1.f);
        bands[2].setPeaking  (500.f,  sr, p.mid * 9.f,  1.2f);
        bands[3].setPeaking  (1000.f, sr, p.mid * 6.f,  0.9f);
        bands[4].setPeaking  (2000.f, sr, p.freq / 200.f - 5.f, p.resonance);
        bands[5].setPeaking  (4000.f, sr, p.high * 6.f, 1.2f);
        bands[6].setPeaking  (8000.f, sr, p.presence * 8.f, 1.f);
        bands[7].setHighShelf(12000.f,sr, p.high * 4.f);
    }
    void setSampleRate(int sampleRate) { sr = (float)sampleRate; }
    float process(float in) {
        for (auto& b : bands) in = b.process(in);
        return in;
    }
    void reset() { for (auto& b : bands) b.reset(); }
};

// ─── Compressor ───────────────────────────────────────────────────────────────
class Compressor {
    float env=0, sr=48000.f;
    float threshold=-20.f, ratio=4.f, attack=0.01f, release=0.1f, makeup=0.f;
public:
    void setParams(const DSPParams& p) {
        threshold = -24.f + p.alpha * 12.f;
        ratio     = 1.f + p.beta * 7.f;
        attack    = 0.001f + (1.f - p.drive) * 0.05f;
        release   = 0.05f + p.wet * 0.3f;
        makeup    = p.master * 6.f;
    }
    void setSampleRate(int sampleRate) { sr = (float)sampleRate; }
    float process(float in) {
        float lvl = std::abs(in);
        float coeff = (lvl > env)
            ? 1.f - std::exp(-1.f / (attack  * sr))
            : 1.f - std::exp(-1.f / (release * sr));
        env = coeff * lvl + (1.f - coeff) * env;
        float dBIn = (env > 1e-7f) ? 20.f * std::log10(env) : -140.f;
        float gr   = (dBIn > threshold) ? (threshold - dBIn) * (1.f - 1.f/ratio) : 0.f;
        return in * std::pow(10.f, (gr + makeup) / 20.f);
    }
    void reset() { env = 0; }
};

// ─── HarmonicExciter ──────────────────────────────────────────────────────────
class HarmonicExciter {
    float lastIn=0;
public:
    void setParams(const DSPParams& p) { (void)p; }
    float process(float in, float drive, float mix) {
        float excited = std::tanh(in * drive) + 0.3f * std::tanh(in * drive * 2.f);
        float transient = std::abs(in) - std::abs(lastIn);
        float adaptMix = mix * (1.f - std::min(0.8f, std::abs(transient) * 5.f));
        lastIn = in;
        return in + (excited - in) * adaptMix;
    }
    void reset() { lastIn = 0; }
};

// ─── StereoWidener ────────────────────────────────────────────────────────────
class StereoWidener {
    float delayBuf[256] = {};
    int   wp = 0;
    float width = 0.3f;
public:
    void setParams(const DSPParams& p) { width = p.gamma * 0.6f; }
    void process(float& L, float& R) {
        float M = (L + R) * 0.5f;
        float S = (L - R) * 0.5f;
        float delayed = delayBuf[wp];
        delayBuf[wp] = S;
        wp = (wp + 1) & 255;
        S = S + delayed * width;
        L = M + S;
        R = M - S;
    }
};

// ─── GainStage ────────────────────────────────────────────────────────────────
class GainStage {
    float inputGain=1.f, outputGain=1.f;
public:
    void setParams(const DSPParams& p) {
        inputGain  = p.drive;
        outputGain = p.master;
    }
    void processInput (float* L, float* R, int n) {
        for (int i=0; i<n; i++) { L[i]*=inputGain; R[i]*=inputGain; }
    }
    void processOutput(float* L, float* R, int n) {
        for (int i=0; i<n; i++) { L[i]*=outputGain; R[i]*=outputGain; }
    }
};

// ─── Contexto principal ───────────────────────────────────────────────────────
// Capacidad de scratch pre-alocada: 4096 frames * 2 canales es suficiente
// para cualquier buffer size que AAudio/AudioTrack entregue en Android.
static constexpr int kMaxFrames = 4096;

struct DSPContext {
    std::mutex     mtx;
    int            sampleRate = 48000;
    bool           initialized = false;
    DSPParams      params;

    ParametricEQ   peq;
    Compressor     comp;
    HarmonicExciter exciter;
    StereoWidener  widener;
    GainStage      gain;

    // Scratch buffers — pre-allocated, reutilizados por nativeProcess
    // ANTES: new float[numFrames] / delete[] en cada llamada (bug)
    // AHORA: buffers fijos en el struct, cero heap en hot path
    float scratchL[kMaxFrames];
    float scratchR[kMaxFrames];
};

static DSPContext* gCtx = nullptr;

} // namespace ivanna

// ─── JNI ──────────────────────────────────────────────────────────────────────
using namespace ivanna;

extern "C" JNIEXPORT void JNICALL
Java_com_ivanna_fusion_pro_DSPBridge_nativeInit(JNIEnv*, jobject, jint sampleRate) {
    if (!gCtx) gCtx = new (std::nothrow) DSPContext();
    if (!gCtx) { LOGE("OOM allocating DSPContext"); return; }

    std::lock_guard<std::mutex> lock(gCtx->mtx);
    gCtx->sampleRate = sampleRate;
    gCtx->peq.setSampleRate(sampleRate);
    gCtx->comp.setSampleRate(sampleRate);

    // Aplicar params por defecto a todos los módulos
    gCtx->peq.setParams(gCtx->params);
    gCtx->comp.setParams(gCtx->params);
    gCtx->exciter.setParams(gCtx->params);
    gCtx->widener.setParams(gCtx->params);
    gCtx->gain.setParams(gCtx->params);

    gCtx->initialized = true;
    LOGI("DSP init @ %d Hz", sampleRate);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivanna_fusion_pro_DSPBridge_nativeSetParams(
        JNIEnv*, jobject,
        jfloat drive, jfloat wet, jfloat mix,
        jfloat alpha, jfloat beta, jfloat gamma_,
        jfloat freq, jfloat resonance,
        jfloat low, jfloat mid, jfloat high,
        jfloat presence, jfloat master) {
    if (!gCtx || !gCtx->initialized) return;
    std::lock_guard<std::mutex> lock(gCtx->mtx);
    gCtx->params = { drive, wet, mix, alpha, beta, gamma_,
                     freq, resonance, low, mid, high, presence, master };
    gCtx->peq.setParams(gCtx->params);
    gCtx->comp.setParams(gCtx->params);
    gCtx->exciter.setParams(gCtx->params);
    gCtx->widener.setParams(gCtx->params);
    gCtx->gain.setParams(gCtx->params);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivanna_fusion_pro_DSPBridge_nativeProcess(JNIEnv* env, jobject,
                                                    jfloatArray buf, jint numFrames) {
    if (!gCtx || !gCtx->initialized) return;
    if (numFrames <= 0 || numFrames > kMaxFrames) return;

    jfloat* data = env->GetFloatArrayElements(buf, nullptr);
    if (!data) return;

    std::lock_guard<std::mutex> lock(gCtx->mtx);

    // Deinterleave — usa scratch pre-alocado, cero new/delete
    float* L = gCtx->scratchL;
    float* R = gCtx->scratchR;
    for (int i = 0; i < numFrames; i++) {
        L[i] = data[i * 2];
        R[i] = data[i * 2 + 1];
    }

    // Pipeline DSP
    gCtx->gain.processInput(L, R, numFrames);
    for (int i = 0; i < numFrames; i++) {
        L[i] = gCtx->exciter.process(gCtx->comp.process(gCtx->peq.process(L[i])),
                                     gCtx->params.drive, gCtx->params.mix);
        R[i] = gCtx->exciter.process(gCtx->comp.process(gCtx->peq.process(R[i])),
                                     gCtx->params.drive, gCtx->params.mix);
        gCtx->widener.process(L[i], R[i]);
    }
    gCtx->gain.processOutput(L, R, numFrames);

    // Reinterleave
    for (int i = 0; i < numFrames; i++) {
        data[i * 2]     = L[i];
        data[i * 2 + 1] = R[i];
    }

    env->ReleaseFloatArrayElements(buf, data, 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivanna_fusion_pro_DSPBridge_nativeReset(JNIEnv*, jobject) {
    if (!gCtx) return;
    std::lock_guard<std::mutex> lock(gCtx->mtx);
    gCtx->peq.reset();
    gCtx->comp.reset();
    gCtx->exciter.reset();
    gCtx->initialized = false;
    LOGI("DSP reset");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ivanna_fusion_pro_DSPBridge_nativeVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF(kVersion);
}

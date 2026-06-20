/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 *
 * Implementación JNI para com.ivannafusion.IvannaNativeLib.
 * Antes de este archivo, las 27 funciones "external fun" de esa clase
 * no tenían ningún símbolo Java_com_ivannafusion_IvannaNativeLib_* en el
 * binario nativo, por lo que cualquier llamada lanzaba UnsatisfiedLinkError.
 *
 * Este motor mantiene estado simple en memoria (no DSP real todavía);
 * el objetivo es exponer una superficie JNI completa y estable para que
 * la UI (Compose) pueda operar sin excepciones, y servir de base para
 * conectar procesamiento real banda por banda más adelante.
 */

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstring>
#include <cmath>

#define LOG_TAG "IVANNA-NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kNumBands = 10;

std::atomic<bool> g_enabled{false};
std::atomic<int>  g_presetId{0};

// EQ / Compresor por banda
float g_eqGain[kNumBands]      = {0};
float g_eqThreshold[kNumBands] = {0};
float g_eqRatio[kNumBands]     = {1.0f};
float g_eqGainReduction[kNumBands] = {0};

float g_compThreshold[kNumBands] = {0};
float g_compRatio[kNumBands]     = {1.0f};
float g_compAttack[kNumBands]    = {0};
float g_compRelease[kNumBands]   = {0};

// Surround / Widener / Bass / Upscaler
std::atomic<float> g_surroundWidth{0.5f};
std::atomic<float> g_surroundLevel{0.5f};
std::atomic<float> g_surroundHeight{0.0f};
std::atomic<float> g_surroundRoom{0.0f};
std::atomic<float> g_widenerWidth{0.0f};
std::atomic<float> g_bassAmount{0.0f};
std::atomic<float> g_bassFrequency{80.0f};
std::atomic<float> g_upscalerAmount{0.0f};
std::atomic<float> g_upscalerCeiling{0.0f};

// Loudness (placeholders hasta integrar medición real)
std::atomic<float> g_momentaryLoudness{-70.0f};
std::atomic<float> g_shortTermLoudness{-70.0f};
std::atomic<float> g_integratedLoudness{-70.0f};
std::atomic<float> g_peakLevel{-100.0f};
std::atomic<float> g_correlation{0.0f};
std::atomic<float> g_loudnessRange{0.0f};

// Convolver
std::atomic<bool>  g_convolverEnabled{false};
std::atomic<int>   g_convolverPreset{0};
std::atomic<float> g_convolverMix{0.5f};

// AI
std::atomic<bool>  g_aiEnabled{false};
std::atomic<bool>  g_aiAutoAdapt{false};
std::atomic<float> g_aiSensitivity{0.5f};
std::atomic<float> g_aiConfidence{0.0f};
std::atomic<float> g_aiTempo{0.0f};

inline int clampBand(int b) {
    if (b < 0) return 0;
    if (b >= kNumBands) return kNumBands - 1;
    return b;
}

} // namespace

extern "C" {

// ── Basic ──────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeSetEnabled(JNIEnv*, jobject, jboolean enabled) {
    g_enabled.store(enabled);
}

JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeIsEnabled(JNIEnv*, jobject) {
    return g_enabled.load();
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeSetPreset(JNIEnv*, jobject, jint id) {
    g_presetId.store(id);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeReset(JNIEnv*, jobject) {
    g_enabled.store(false);
    g_presetId.store(0);
    for (int i = 0; i < kNumBands; i++) {
        g_eqGain[i] = 0; g_eqThreshold[i] = 0; g_eqRatio[i] = 1.0f; g_eqGainReduction[i] = 0;
        g_compThreshold[i] = 0; g_compRatio[i] = 1.0f; g_compAttack[i] = 0; g_compRelease[i] = 0;
    }
    g_surroundWidth.store(0.5f);
    g_surroundLevel.store(0.5f);
    g_surroundHeight.store(0.0f);
    g_surroundRoom.store(0.0f);
    g_widenerWidth.store(0.0f);
    g_bassAmount.store(0.0f);
    g_bassFrequency.store(80.0f);
    g_upscalerAmount.store(0.0f);
    g_upscalerCeiling.store(0.0f);
    g_convolverEnabled.store(false);
    g_convolverMix.store(0.5f);
    g_aiEnabled.store(false);
    g_aiAutoAdapt.store(false);
    g_aiSensitivity.store(0.5f);
    LOGI("nativeReset: estado restablecido");
}

// ── EQ ─────────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeEqSetGain(JNIEnv*, jobject, jint b, jfloat g) {
    g_eqGain[clampBand(b)] = g;
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeEqGetGain(JNIEnv*, jobject, jint b) {
    return g_eqGain[clampBand(b)];
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeEqSetThreshold(JNIEnv*, jobject, jint b, jfloat t) {
    g_eqThreshold[clampBand(b)] = t;
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeEqSetRatio(JNIEnv*, jobject, jint b, jfloat r) {
    g_eqRatio[clampBand(b)] = r;
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeEqGetGainReduction(JNIEnv*, jobject, jint b) {
    return g_eqGainReduction[clampBand(b)];
}

// ── Compressor ─────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeCompSetThreshold(JNIEnv*, jobject, jint b, jfloat t) {
    g_compThreshold[clampBand(b)] = t;
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeCompSetRatio(JNIEnv*, jobject, jint b, jfloat r) {
    g_compRatio[clampBand(b)] = r;
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeCompSetAttack(JNIEnv*, jobject, jint b, jfloat a) {
    g_compAttack[clampBand(b)] = a;
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeCompSetRelease(JNIEnv*, jobject, jint b, jfloat r) {
    g_compRelease[clampBand(b)] = r;
}

// ── Surround ───────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeSurroundSetWidth(JNIEnv*, jobject, jfloat w) {
    g_surroundWidth.store(w);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeSurroundSetLevel(JNIEnv*, jobject, jfloat l) {
    g_surroundLevel.store(l);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeSurroundSetHeight(JNIEnv*, jobject, jfloat h) {
    g_surroundHeight.store(h);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeSurroundSetRoom(JNIEnv*, jobject, jfloat r) {
    g_surroundRoom.store(r);
}

// ── Widener ────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeWidenerSetWidth(JNIEnv*, jobject, jfloat w) {
    g_widenerWidth.store(w);
}

// ── Bass ───────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeBassSetAmount(JNIEnv*, jobject, jfloat a) {
    g_bassAmount.store(a);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeBassSetFrequency(JNIEnv*, jobject, jfloat f) {
    g_bassFrequency.store(f);
}

// ── Upscaler ───────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeUpscalerSetAmount(JNIEnv*, jobject, jfloat a) {
    g_upscalerAmount.store(a);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeUpscalerSetCeiling(JNIEnv*, jobject, jfloat c) {
    g_upscalerCeiling.store(c);
}

// ── Loudness ───────────────────────────────────────────────────────────────
JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeGetMomentaryLoudness(JNIEnv*, jobject) {
    return g_momentaryLoudness.load();
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeGetShortTermLoudness(JNIEnv*, jobject) {
    return g_shortTermLoudness.load();
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeGetIntegratedLoudness(JNIEnv*, jobject) {
    return g_integratedLoudness.load();
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeGetPeakLevel(JNIEnv*, jobject) {
    return g_peakLevel.load();
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeGetCorrelation(JNIEnv*, jobject) {
    return g_correlation.load();
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeGetLoudnessRange(JNIEnv*, jobject) {
    return g_loudnessRange.load();
}

// ── Convolver ──────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeConvolverSetEnabled(JNIEnv*, jobject, jboolean e) {
    g_convolverEnabled.store(e);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeConvolverLoadPreset(JNIEnv*, jobject, jint id) {
    g_convolverPreset.store(id);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeConvolverSetMix(JNIEnv*, jobject, jfloat m) {
    g_convolverMix.store(m);
}

// ── AutoEQ ─────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeAutoeqApply(JNIEnv* env, jobject, jstring name) {
    const char* cname = env->GetStringUTFChars(name, nullptr);
    LOGI("AutoEQ aplicado: %s", cname ? cname : "(null)");
    if (cname) env->ReleaseStringUTFChars(name, cname);
}

// ── AI ─────────────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeAiSetEnabled(JNIEnv*, jobject, jboolean e) {
    g_aiEnabled.store(e);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeAiSetAutoAdapt(JNIEnv*, jobject, jboolean a) {
    g_aiAutoAdapt.store(a);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeAiSetSensitivity(JNIEnv*, jobject, jfloat s) {
    g_aiSensitivity.store(s);
}

JNIEXPORT jstring JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeAiGetDetectedGenre(JNIEnv* env, jobject) {
    return env->NewStringUTF("unknown");
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeAiGetConfidence(JNIEnv*, jobject) {
    return g_aiConfidence.load();
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeAiGetTempo(JNIEnv*, jobject) {
    return g_aiTempo.load();
}

JNIEXPORT jstring JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeAiGetCurrentCurveName(JNIEnv* env, jobject) {
    return env->NewStringUTF("Flat");
}

JNIEXPORT jstring JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeAiGetCurrentCurveDescription(JNIEnv* env, jobject) {
    return env->NewStringUTF("Sin coloración aplicada");
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeAiApplyCurrentCurve(JNIEnv*, jobject) {
    LOGI("nativeAiApplyCurrentCurve invocado");
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeAiSaveAsPreset(JNIEnv* env, jobject, jstring name) {
    const char* cname = env->GetStringUTFChars(name, nullptr);
    LOGI("Preset AI guardado: %s", cname ? cname : "(null)");
    if (cname) env->ReleaseStringUTFChars(name, cname);
}

} // extern "C"

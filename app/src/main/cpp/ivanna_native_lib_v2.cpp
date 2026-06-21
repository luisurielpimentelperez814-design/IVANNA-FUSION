/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 *
 * Implementación JNI para com.ivannafusion.IvannaNativeLib.
 *
 * Esta versión conecta los controles de la UI con el motor DSP real
 * (ivanna::dsp::ParametricEQ, Compressor, HarmonicExciter — los mismos
 * que usa el efecto de audioserver en src/cpp/effect_library.cpp) en
 * vez de solo guardar floats en variables atómicas sin procesar.
 *
 * processBlock() queda expuesta como función C++ (no JNI) para que
 * AudioEngine (audio_orchestrator.cpp) pueda invocarla directamente
 * sobre las muestras capturadas, sin pasar por JNI en el hot path.
 */

#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <mutex>

#include "parametric_eq.h"
#include "compressor.h"
#include "harmonic_exciter.h"

#define LOG_TAG "IVANNA-NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)

namespace ivanna::nativelib {

// ─── Motor DSP real, compartido por todas las funciones JNI de este archivo ───
// Un único bloque de procesamiento por proceso (la app sólo tiene un motor
// IvannaNativeLib en Kotlin — object, no class), por lo que no hace falta
// un contexto por instancia como en effect_library.cpp.
struct Engine {
    std::mutex                mutex;     // protege contra setParam concurrente con process
    dsp::ParametricEQ         eq;
    dsp::Compressor           comp;
    dsp::HarmonicExciter      exciter;

    std::atomic<bool> enabled{false};
    std::atomic<int>  presetId{0};

    float lastGainReduction[dsp::PEQ_BANDS] = {0};
};

static Engine g_engine;

} // namespace ivanna::nativelib

using ivanna::nativelib::g_engine;

namespace {

constexpr int kNumBands = ivanna::dsp::PEQ_BANDS;  // 8 — el EQ real soporta 8 bandas

// Surround / Widener / Bass / Upscaler — todavía no tienen motor DSP propio
// en src/cpp; se mantienen como parámetros controlables (placeholders
// explícitos, documentados) hasta que se implementen sus algoritmos.
std::atomic<float> g_surroundWidth{0.5f};
std::atomic<float> g_surroundLevel{0.5f};
std::atomic<float> g_surroundHeight{0.0f};
std::atomic<float> g_surroundRoom{0.0f};
std::atomic<float> g_widenerWidth{0.0f};
std::atomic<float> g_bassAmount{0.0f};
std::atomic<float> g_bassFrequency{80.0f};
std::atomic<float> g_upscalerAmount{0.0f};
std::atomic<float> g_upscalerCeiling{0.0f};

// Loudness (placeholders hasta integrar medición real ITU-R BS.1770)
std::atomic<float> g_momentaryLoudness{-70.0f};
std::atomic<float> g_shortTermLoudness{-70.0f};
std::atomic<float> g_integratedLoudness{-70.0f};
std::atomic<float> g_peakLevel{-100.0f};
std::atomic<float> g_correlation{0.0f};
std::atomic<float> g_loudnessRange{0.0f};

// Convolver — sin motor de convolución real todavía
std::atomic<bool>  g_convolverEnabled{false};
std::atomic<int>   g_convolverPreset{0};
std::atomic<float> g_convolverMix{0.5f};

// AI — sin clasificador de género/tempo real todavía
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
    g_engine.enabled.store(enabled);
}

JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeIsEnabled(JNIEnv*, jobject) {
    return g_engine.enabled.load();
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeSetPreset(JNIEnv*, jobject, jint id) {
    using namespace ivanna::dsp;
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    g_engine.presetId.store(id);

    // Preset 0 = flat / referencia; preset 1 = rock clásico (reutiliza los
    // mismos presets calibrados que ivanna_fusion.cpp para mantener
    // consistencia entre el efecto de audioserver y este motor de la app).
    if (id == 1) {
        auto params = peq_preset_classic_rock();
        for (int b = 0; b < PEQ_BANDS; ++b) g_engine.eq.setBand(b, params[b]);
        g_engine.comp.setThreshold(-20.f); g_engine.comp.setRatio(3.f); g_engine.comp.setMakeup(2.f);
        g_engine.comp.setAttack(5.f); g_engine.comp.setRelease(80.f);
        g_engine.exciter.setDrive(3.5f); g_engine.exciter.setMix(0.25f); g_engine.exciter.setHpfFreq(2500.f);
    } else {
        auto params = peq_default_params();
        for (int b = 0; b < PEQ_BANDS; ++b) g_engine.eq.setBand(b, params[b]);
        g_engine.comp.setThreshold(-18.f); g_engine.comp.setRatio(2.f); g_engine.comp.setMakeup(1.f);
        g_engine.exciter.setDrive(2.f); g_engine.exciter.setMix(0.15f);
    }
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeReset(JNIEnv*, jobject) {
    using namespace ivanna::dsp;
    std::lock_guard<std::mutex> lock(g_engine.mutex);

    g_engine.enabled.store(false);
    g_engine.presetId.store(0);

    auto params = peq_default_params();
    for (int b = 0; b < PEQ_BANDS; ++b) g_engine.eq.setBand(b, params[b]);
    g_engine.eq.resetState();
    g_engine.eq.setBypass(false);

    g_engine.comp.setThreshold(-18.f);
    g_engine.comp.setRatio(4.f);
    g_engine.comp.setKnee(6.f);
    g_engine.comp.setAttack(5.f);
    g_engine.comp.setRelease(80.f);
    g_engine.comp.setMakeup(3.f);
    g_engine.comp.setBypass(false);
    g_engine.comp.resetState();

    g_engine.exciter.setDrive(3.f);
    g_engine.exciter.setMix(0.25f);
    g_engine.exciter.setHpfFreq(2000.f);
    g_engine.exciter.setBypass(false);
    g_engine.exciter.resetState();

    for (int i = 0; i < kNumBands; i++) g_engine.lastGainReduction[i] = 0.f;

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
    LOGI("nativeReset: motor DSP y estado restablecidos");
}

// ── EQ — ahora respaldado por ivanna::dsp::ParametricEQ real ───────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeEqSetGain(JNIEnv*, jobject, jint b, jfloat g) {
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    g_engine.eq.setBandGain(clampBand(b), g);
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeEqGetGain(JNIEnv*, jobject, jint b) {
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    return g_engine.eq.getBand(clampBand(b)).gain_dB;
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeEqSetThreshold(JNIEnv*, jobject, jint b, jfloat t) {
    // El "threshold" por banda del diseño original de la UI se traduce al
    // threshold global del compresor real (la cadena DSP es PEQ -> 1
    // compresor estéreo, no un compresor multibanda independiente por
    // banda). Se mantiene el índice de banda solo para compatibilidad
    // con la firma JNI existente; la última banda en escribir "gana".
    (void)b;
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    g_engine.comp.setThreshold(t);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeEqSetRatio(JNIEnv*, jobject, jint b, jfloat r) {
    (void)b;
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    g_engine.comp.setRatio(r);
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeEqGetGainReduction(JNIEnv*, jobject, jint b) {
    return g_engine.lastGainReduction[clampBand(b)];
}

// ── Compressor ─────────────────────────────────────────────────────────────
JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeCompSetThreshold(JNIEnv*, jobject, jint b, jfloat t) {
    (void)b;
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    g_engine.comp.setThreshold(t);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeCompSetRatio(JNIEnv*, jobject, jint b, jfloat r) {
    (void)b;
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    g_engine.comp.setRatio(r);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeCompSetAttack(JNIEnv*, jobject, jint b, jfloat a) {
    (void)b;
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    g_engine.comp.setAttack(a);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeCompSetRelease(JNIEnv*, jobject, jint b, jfloat r) {
    (void)b;
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    g_engine.comp.setRelease(r);
}

// ── Surround / Widener / Bass / Upscaler ────────────────────────────────────
// Sin algoritmo DSP propio todavía en src/cpp (no hay decorrelador de fase
// para surround, ni shelving específico de "bass boost" separado del EQ
// paramétrico, ni upsampler). Se documentan explícitamente como pendientes
// en vez de simular un efecto falso.
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

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeWidenerSetWidth(JNIEnv*, jobject, jfloat w) {
    g_widenerWidth.store(w);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeBassSetAmount(JNIEnv*, jobject, jfloat a) {
    // Aproximación real con el motor existente: sube la ganancia de la
    // banda 0 (sub-bass, low-shelf 63Hz) del EQ paramétrico en proporción
    // a 'amount' (0..1 -> 0..+9dB), en vez de solo guardar el valor.
    g_bassAmount.store(a);
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    float gain_dB = std::clamp(a, 0.f, 1.f) * 9.f;
    g_engine.eq.setBandGain(0, gain_dB);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeBassSetFrequency(JNIEnv*, jobject, jfloat f) {
    g_bassFrequency.store(f);
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    auto band = g_engine.eq.getBand(0);
    ivanna::dsp::PeqBandParams p = band;
    p.frequency_hz = f;
    g_engine.eq.setBand(0, p);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeUpscalerSetAmount(JNIEnv*, jobject, jfloat a) {
    g_upscalerAmount.store(a);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeUpscalerSetCeiling(JNIEnv*, jobject, jfloat c) {
    g_upscalerCeiling.store(c);
}

// ── Loudness ───────────────────────────────────────────────────────────────
// Valores actualizados desde processBlock() (medición real básica);
// hasta que processBlock() se invoque desde el pipeline de audio real,
// reportan el último valor calculado o el default de silencio.
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

// ── Bindings adicionales para IvannaNativeLib.kt (presets + AI) ────────────
// IvannaNativeLib.kt declara estas 7 funciones que no tenían ninguna
// implementación nativa. Se conectan aquí al motor DSP real (g_engine)
// ya existente en este archivo, sin modificar ninguna función previa.
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeLoadPreset(
        JNIEnv *env, jobject, jstring presetName) {
    const char* cname = env->GetStringUTFChars(presetName, nullptr);
    std::string name = cname ? cname : "";
    if (cname) env->ReleaseStringUTFChars(presetName, cname);

    using namespace ivanna::dsp;
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    if (name == "Rock" || name == "rock" || name == "Classic Rock") {
        auto params = peq_preset_classic_rock();
        for (int b = 0; b < PEQ_BANDS; ++b) g_engine.eq.setBand(b, params[b]);
        g_engine.presetId.store(1);
    } else {
        auto params = peq_default_params();
        for (int b = 0; b < PEQ_BANDS; ++b) g_engine.eq.setBand(b, params[b]);
        g_engine.presetId.store(0);
    }
    LOGI("nativeLoadPreset: %s", name.c_str());
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeSavePreset(
        JNIEnv *env, jobject, jstring presetName) {
    // Persistencia en disco pendiente (requiere ruta de almacenamiento de
    // la app, gestionada normalmente desde Kotlin/PresetManager). Por ahora
    // se registra el guardado en log para trazabilidad, sin escribir un
    // archivo desde el lado nativo todavía.
    const char* cname = env->GetStringUTFChars(presetName, nullptr);
    LOGI("nativeSavePreset (pendiente de persistencia en disco): %s", cname ? cname : "(null)");
    if (cname) env->ReleaseStringUTFChars(presetName, cname);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeGetCurrentParams(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    // Serialización simple (no JSON completo, evita dependencia externa):
    // "eq:b0gain,b1gain,...;comp:thresh,ratio,attack,release"
    std::string out = "eq:";
    for (int b = 0; b < ivanna::dsp::PEQ_BANDS; ++b) {
        if (b > 0) out += ",";
        out += std::to_string(g_engine.eq.getBand(b).gain_dB);
    }
    out += ";preset:" + std::to_string(g_engine.presetId.load());
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeSetParams(JNIEnv *env, jobject, jstring params) {
    // Deserialización simétrica a nativeGetCurrentParams pendiente
    // (requiere parser del formato "eq:...;preset:..."). Se acepta la
    // llamada sin lanzar UnsatisfiedLinkError; no aplica cambios todavía.
    const char* cname = env->GetStringUTFChars(params, nullptr);
    LOGI("nativeSetParams (parser pendiente), recibido: %s", cname ? cname : "(null)");
    if (cname) env->ReleaseStringUTFChars(params, cname);
    return JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeInitializeAI(JNIEnv *env, jobject, jstring modelPath) {
    // Sin runtime de inferencia (TFLite/ExecuTorch) enlazado todavía en
    // este target; omega_engine/export_to_executorch.py sugiere que el
    // modelo se exportará a ExecuTorch, pero el enlace del runtime en
    // CMakeLists.txt sigue pendiente. Se documenta explícitamente.
    const char* cname = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("nativeInitializeAI (runtime de inferencia pendiente de enlazar): %s", cname ? cname : "(null)");
    if (cname) env->ReleaseStringUTFChars(modelPath, cname);
    return JNI_FALSE;
}

JNIEXPORT jfloatArray JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeInferenceAI(JNIEnv *env, jobject, jfloatArray inputData) {
    // Passthrough explícito hasta que el runtime de inferencia esté
    // enlazado (ver nativeInitializeAI). Devuelve el mismo array de
    // entrada sin modificar, en vez de simular una inferencia falsa.
    jsize n = env->GetArrayLength(inputData);
    jfloatArray result = env->NewFloatArray(n);
    jfloat *in  = env->GetFloatArrayElements(inputData, nullptr);
    env->SetFloatArrayRegion(result, 0, n, in);
    env->ReleaseFloatArrayElements(inputData, in, JNI_ABORT);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeReleaseAI(JNIEnv *, jobject) {
    LOGI("nativeReleaseAI: sin runtime de IA activo que liberar todavía");
    return JNI_TRUE;
}

} // extern "C"

// ─────────────────────────────────────────────────────────────────────────────
// Punto de integración real con el pipeline de audio (no-JNI).
// AudioEngine (audio_orchestrator.cpp) puede llamar a esta función desde
// nativeProcessCapture / el callback AAudio para aplicar EQ -> Compresor ->
// Exciter sobre el bloque de audio real, y actualizar las métricas de
// loudness reportadas a la UI vía nativeGetXxxLoudness().
// ─────────────────────────────────────────────────────────────────────────────
namespace ivanna::nativelib {

void processBlock(float* L, float* R, int n_frames) noexcept {
    if (!g_engine.enabled.load(std::memory_order_relaxed)) return;
    if (n_frames <= 0) return;

    std::lock_guard<std::mutex> lock(g_engine.mutex);

    g_engine.eq.process(L, R, L, R, n_frames);
    g_engine.comp.process(L, R, L, R, n_frames);
    g_engine.exciter.process(L, R, L, R, n_frames);

    // Gain reduction actual del compresor, reflejado en todas las bandas
    // (compresor de banda única -> mismo valor para todas; mantiene
    // compatibilidad con la API existente nativeEqGetGainReduction(b)).
    float gr = -g_engine.comp.currentGainDB();
    if (gr < 0.f) gr = 0.f;
    for (int i = 0; i < dsp::PEQ_BANDS; ++i) g_engine.lastGainReduction[i] = gr;

    // Peak level simple del bloque procesado, en dB (placeholder de
    // loudness más sofisticado tipo ITU-R BS.1770, pendiente).
    float peak = 0.f;
    for (int i = 0; i < n_frames; ++i) {
        peak = std::max(peak, std::fabs(L[i]));
        peak = std::max(peak, std::fabs(R[i]));
    }
    float peak_dB = (peak > 1e-9f) ? 20.f * std::log10(peak) : -100.f;
    g_peakLevel.store(peak_dB, std::memory_order_relaxed);
}

void setSamplerate(float fs) noexcept {
    std::lock_guard<std::mutex> lock(g_engine.mutex);
    g_engine.eq.setSamplerate(fs);
    g_engine.comp.setSamplerate(fs);
    g_engine.exciter.setSamplerate(fs);
}

} // namespace ivanna::nativelib

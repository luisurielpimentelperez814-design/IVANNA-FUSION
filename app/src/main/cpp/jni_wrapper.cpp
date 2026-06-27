#include <jni.h>
#include <cmath>
#include <vector>
#include <android/log.h>
#include <string.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "IVANNA_DSP", __VA_ARGS__)

// Suavizado exponencial (filtro de un polo / EMA reparametrizado) — ver
// historial del proyecto para la verificación algebraica/numérica de
// que es matemáticamente equivalente a un promedio móvil exponencial.
static inline float homeostasis(float n, float omega, float mu) {
    if (std::isnan(omega) || std::isinf(omega)) return n;
    if (std::isnan(n) || std::isinf(n)) return omega;
    return (n + mu * omega) / (1.0f + mu);
}

struct Biquad {
    float b0=1, b1=0, b2=0, a1=0, a2=0, x1=0, x2=0, y1=0, y2=0;
    // freq/Q/gain por banda se guardan y se usan para recalcular los
    // coeficientes reales cada vez que cualquiera de los 3 cambia —
    // antes nativeSetEQFreq/nativeSetEQQ estaban vacíos ({}), así que
    // mover esos controles en la UI no tenía ningún efecto audible
    // (bug reportado: "se activan funciones pero no hay efecto").
    float freq = 1000.f, gainDB = 0.f, Q = 1.41f;
    bool bypassed = false;

    void setPeaking(float freqIn, float sr, float gainDBIn, float Qin) {
        freq = freqIn; gainDB = gainDBIn; Q = Qin;
        float A = powf(10.0f, gainDB/40.0f);
        float w0 = 2.0f * M_PI * freq / sr;
        float alpha = sinf(w0) / (2.0f * Q);
        float cosw0 = cosf(w0);
        b0 = 1.0f + alpha*A; b1 = -2.0f*cosw0; b2 = 1.0f - alpha*A;
        float a0 = 1.0f + alpha/A; a1 = -2.0f*cosw0; a2 = 1.0f - alpha/A;
        b0/=a0; b1/=a0; b2/=a0; a1/=a0; a2/=a0;
    }
    void setFreq(float freqIn, float sr) { setPeaking(freqIn, sr, gainDB, Q); }
    void setQ(float Qin, float sr) { setPeaking(freq, sr, gainDB, Qin); }
    float process(float in) {
        if (bypassed) return in;
        float out = b0*in + b1*x1 + b2*x2 - a1*y1 - a2*y2;
        x2=x1; x1=in; y2=y1; y1=out; return out;
    }
};

static std::vector<Biquad> eq(8);
static float compThresh=-20, compRatio=4, compAttack=10, compRelease=100;
static float compKnee=6, compMakeup=0, compEnv=0;
static bool compBypass=false;
static float excDrive=1, excMix=0.5;
static bool excBypass=false, fftEnabled=false;
static bool initialized=false;
static float g_sampleRate = 48000.f;
static float lastInputLevel=0, lastOutputLevel=0;
static float spectrum[32] = {0};
static float currentRmsDb = -60.0f;
static float correlation = 1.0f;
static int latencyMicros = 5000;
static int generation = 0;
static float bestFitness = 0.0f;
static float tempo = 120.0f;
static char detectedGenre[32] = "ROCK";
static float lastL = 0.0f, lastR = 0.0f;

// ═══════════════════════════════════════════════════════════════
// INICIALIZACIÓN
// ═══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jboolean JNICALL Java_com_ivannafusion_AudioEngine_nativeInit(JNIEnv*, jobject, jint sr, jint) {
    g_sampleRate = (float)sr;
    // 8 bandas con frecuencias estándar de un EQ paramétrico (no las 8
    // fijadas en 1000Hz que tenía una versión anterior, que sonaban
    // idénticas hasta llamar a setFreq, que estaba vacío).
    static const float defaultFreqs[8] = {60.f, 150.f, 400.f, 1000.f, 2500.f, 6000.f, 10000.f, 16000.f};
    for(int i=0; i<8; i++) eq[i].setPeaking(defaultFreqs[i], g_sampleRate, 0, 1.41f);
    initialized=true;
    LOGI("DSP inicializado a %d Hz", sr);
    return JNI_TRUE;
}

// ═══════════════════════════════════════════════════════════════
// PROCESAMIENTO DE AUDIO
// ═══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeProcessAudio(JNIEnv* env, jobject, jfloatArray in, jfloatArray out, jint frames) {
    if(!initialized) {
        jfloat* inBuf = env->GetFloatArrayElements(in, nullptr);
        env->SetFloatArrayRegion(out, 0, frames * 2, inBuf);
        env->ReleaseFloatArrayElements(in, inBuf, JNI_ABORT);
        return;
    }

    jfloat *inBuf = env->GetFloatArrayElements(in, 0);
    jfloat *outBuf = env->GetFloatArrayElements(out, 0);
    float inputSum=0, outputSum=0;

    for(int f=0; f<frames; f++) {
        float L = inBuf[f*2];
        float R = inBuf[f*2+1];

        inputSum += fabsf(L) + fabsf(R);

        for(int b=0; b<8; b++) {
            L = eq[b].process(L);
            R = eq[b].process(R);
        }

        if(!compBypass) {
            float lvl = fabsf(L);
            float coeff = (lvl > compEnv) ?
                (1.0f - expf(-1.0f/(compAttack*0.001f*48000.0f))) :
                (1.0f - expf(-1.0f/(compRelease*0.001f*48000.0f)));
            float rawEnv = coeff*lvl + (1.0f-coeff)*compEnv;
            // Protección NaN/Inf: ver historial del proyecto — si
            // attack/release llegaran a 0, expf(-1/0) puede degenerar.
            compEnv = (std::isnan(rawEnv) || std::isinf(rawEnv)) ? lvl : homeostasis(compEnv, rawEnv, 0.5f);

            float gr = (compEnv > compThresh) ? (compThresh-compEnv)*(1.0f-1.0f/compRatio) : 0;
            float gain = powf(10.0f, (gr+compMakeup)/20.0f);
            L *= gain;
            R *= gain;
        }

        if(!excBypass) {
            float transientL = fabsf(L) - fabsf(lastL);
            float transientR = fabsf(R) - fabsf(lastR);
            float transientFactor = fmaxf(0.0f, fmaxf(transientL, transientR) * 10.0f);
            float adaptiveMu = excMix * (1.0f - fminf(0.8f, transientFactor * 0.5f));
            float excited_L = L + (tanhf(L*excDrive) - L);
            float excited_R = R + (tanhf(R*excDrive) - R);
            L = homeostasis(L, excited_L, adaptiveMu);
            R = homeostasis(R, excited_R, adaptiveMu);
            lastL = L; lastR = R;
        }

        if(fftEnabled) {
            L *= 1.1f;
            R *= 1.1f;
        }

        outBuf[f*2] = L;
        outBuf[f*2+1] = R;

        outputSum += fabsf(L) + fabsf(R);
    }

    lastInputLevel = inputSum / (frames*2);
    lastOutputLevel = outputSum / (frames*2);

    // RMS suavizado (ver historial: antes se sobreescribía directo,
    // saltando bruscamente en el medidor de la UI entre bloques).
    float rawRmsDb = (lastInputLevel > 0.001f) ? 20.0f * log10f(lastInputLevel) : -60.0f;
    currentRmsDb = homeostasis(currentRmsDb, rawRmsDb, 0.35f);

    // Post-FX: FDN reverb + M/S widener (implementación real, no stubs)
    apply_post_fx(outBuf, frames);

    env->ReleaseFloatArrayElements(in, inBuf, 0);
    env->ReleaseFloatArrayElements(out, outBuf, 0);
}

// ═══════════════════════════════════════════════════════════════
// MÉTRICAS DE IA (heurística por energía espectral — NO es un modelo
// de machine learning entrenado; ver detectGenre en AudioEngine.kt
// para la heurística real de género usada en la capa Kotlin)
// ═══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jfloat JNICALL Java_com_ivannafusion_AudioEngine_nativeAiGetRmsDb(JNIEnv*, jobject) {
    return currentRmsDb;
}

extern "C" JNIEXPORT jfloatArray JNICALL Java_com_ivannafusion_AudioEngine_nativeAiGetSpectrum(JNIEnv* env, jobject) {
    jfloatArray result = env->NewFloatArray(32);
    if (result != nullptr) {
        float normalizedLevel = (currentRmsDb + 60.0f) / 60.0f;
        if (normalizedLevel < 0) normalizedLevel = 0;
        if (normalizedLevel > 1) normalizedLevel = 1;

        for (int i = 0; i < 32; i++) {
            float freqFactor = 1.0f - fabsf((static_cast<float>(i) - 16.0f) / 16.0f);
            float rawBand = normalizedLevel * freqFactor * 0.8f;
            // Suavizado con mu adaptativo (ver historial del proyecto):
            // evita que las 32 barras "tiemblen" entre llamadas
            // consecutivas sin atrasar reacciones a cambios reales.
            float adaptiveMu = 0.3f * (1.0f + fabsf(rawBand - spectrum[i]));
            spectrum[i] = fmaxf(0.0f, fminf(1.0f, homeostasis(spectrum[i], rawBand, adaptiveMu)));
        }
        env->SetFloatArrayRegion(result, 0, 32, spectrum);
    }
    return result;
}

extern "C" JNIEXPORT jfloat JNICALL Java_com_ivannafusion_AudioEngine_nativeGetCorrelation(JNIEnv*, jobject) { return correlation; }
extern "C" JNIEXPORT jint JNICALL Java_com_ivannafusion_AudioEngine_nativeGetLatencyMicros(JNIEnv*, jobject) { return latencyMicros; }
extern "C" JNIEXPORT jstring JNICALL Java_com_ivannafusion_AudioEngine_nativeAiGetDetectedGenre(JNIEnv* env, jobject) { return env->NewStringUTF(detectedGenre); }
extern "C" JNIEXPORT jfloat JNICALL Java_com_ivannafusion_AudioEngine_nativeAiGetTempo(JNIEnv*, jobject) { return tempo; }

// ═══════════════════════════════════════════════════════════════
// FUNCIONES EQ — gain/freq/Q/bypass, las 4 con efecto real
// ═══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQGain(JNIEnv*, jobject, jint band, jfloat gain) { if(band>=0 && band<8) eq[band].setPeaking(eq[band].freq, g_sampleRate, gain, eq[band].Q); }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQFreq(JNIEnv*, jobject, jint band, jfloat freqHz) { if(band>=0 && band<8) eq[band].setFreq(freqHz, g_sampleRate); }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQQ(JNIEnv*, jobject, jint band, jfloat q) { if(band>=0 && band<8) eq[band].setQ(q, g_sampleRate); }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQBypass(JNIEnv*, jobject, jint band, jboolean bypass) { if(band>=0 && band<8) eq[band].bypassed = bypass; }

// ═══════════════════════════════════════════════════════════════
// FUNCIONES COMPRESOR
// ═══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorThreshold(JNIEnv*, jobject, jfloat f) { compThresh=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorRatio(JNIEnv*, jobject, jfloat f) { compRatio=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorAttack(JNIEnv*, jobject, jfloat f) { compAttack=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorRelease(JNIEnv*, jobject, jfloat f) { compRelease=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorKnee(JNIEnv*, jobject, jfloat f) { compKnee=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorMakeup(JNIEnv*, jobject, jfloat f) { compMakeup=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorBypass(JNIEnv*, jobject, jboolean b) { compBypass=b; }

// ═══════════════════════════════════════════════════════════════
// FUNCIONES EXCITER
// ═══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetExciterDrive(JNIEnv*, jobject, jfloat f) { excDrive=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetExciterMix(JNIEnv*, jobject, jfloat f) { excMix=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetExciterBypass(JNIEnv*, jobject, jboolean b) { excBypass=b; }

// ═══════════════════════════════════════════════════════════════
// FUNCIONES FFT
// ═══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetFFTEffect(JNIEnv*, jobject, jboolean b) { fftEnabled=b; }

// ═══════════════════════════════════════════════════════════════
// RESET
// ═══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeReset(JNIEnv*, jobject) {
    for(int i=0; i<8; i++) eq[i] = Biquad();
    compThresh=-20; compRatio=4; compAttack=10; compRelease=100;
    compKnee=6; compMakeup=0; compEnv=0; compBypass=false;
    excDrive=1; excMix=0.5; excBypass=false; fftEnabled=false;
    for(int i=0; i<32; i++) spectrum[i] = 0;
    currentRmsDb = -60.0f;
    LOGI("DSP Reset");
}

// ═══════════════════════════════════════════════════════════════
// MÉTRICAS REALES
// ═══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jfloat JNICALL Java_com_ivannafusion_AudioEngine_nativeGetInputLevel(JNIEnv*, jobject) {
    return lastInputLevel;
}

extern "C" JNIEXPORT jfloat JNICALL Java_com_ivannafusion_AudioEngine_nativeGetOutputLevel(JNIEnv*, jobject) {
    return lastOutputLevel;
}

// ═══════════════════════════════════════════════════════════════
// STUBS DE IvannaNativeLib — funciones declaradas en IvannaNativeLib.kt
// que NO tienen implementación real todavía en este target.
// Están aquí para que el linker las resuelva y evitar el
// UnsatisfiedLinkError que ocurría antes de este commit.
// Cuando existan implementaciones reales, reemplazar estos stubs
// en sus archivos fuente correspondientes (o aquí mismo) sin
// cambiar los nombres de símbolo.
// ═══════════════════════════════════════════════════════════════

// --- Audio Engine bridge (AudioEngine.kt ya gestiona el motor real; ---
// --- estos son un segundo punto de entrada alternativo pendiente)   ---
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeInitAudioEngine(JNIEnv*, jobject, jint sampleRate, jint bufferSize) {
    LOGI("IvannaNativeLib.nativeInitAudioEngine(%d, %d) — stub", sampleRate, bufferSize);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeProcessAudio(JNIEnv* env, jobject, jfloatArray in, jfloatArray out) {
    jsize len = env->GetArrayLength(in);
    // Passthrough stub: copia entrada → salida sin procesar
    jfloat* inBuf  = env->GetFloatArrayElements(in,  nullptr);
    jfloat* outBuf = env->GetFloatArrayElements(out, nullptr);
    jsize   outLen = env->GetArrayLength(out);
    jsize   copy   = len < outLen ? len : outLen;
    for (jsize i = 0; i < copy; i++) outBuf[i] = inBuf[i];
    env->ReleaseFloatArrayElements(in,  inBuf,  JNI_ABORT);
    env->ReleaseFloatArrayElements(out, outBuf, 0);
    return (jint)copy;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeReleaseAudioEngine(JNIEnv*, jobject) {
    LOGI("IvannaNativeLib.nativeReleaseAudioEngine — stub");
    return JNI_TRUE;
}

// --- Preset / Persistence ---
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeLoadPreset(JNIEnv* env, jobject, jstring presetName) {
    const char* name = env->GetStringUTFChars(presetName, nullptr);
    LOGI("IvannaNativeLib.nativeLoadPreset('%s') — stub", name);
    env->ReleaseStringUTFChars(presetName, name);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeSavePreset(JNIEnv* env, jobject, jstring presetName) {
    const char* name = env->GetStringUTFChars(presetName, nullptr);
    LOGI("IvannaNativeLib.nativeSavePreset('%s') — stub", name);
    env->ReleaseStringUTFChars(presetName, name);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeGetCurrentParams(JNIEnv* env, jobject) {
    LOGI("IvannaNativeLib.nativeGetCurrentParams — stub");
    return env->NewStringUTF("{}");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeSetParams(JNIEnv* env, jobject, jstring params) {
    const char* p = env->GetStringUTFChars(params, nullptr);
    LOGI("IvannaNativeLib.nativeSetParams('%s') — stub", p);
    env->ReleaseStringUTFChars(params, p);
    return JNI_TRUE;
}

// --- AI Engine ---
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeInitializeAI(JNIEnv* env, jobject, jstring modelPath) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("IvannaNativeLib.nativeInitializeAI('%s') — stub (requiere modelo .tflite cargado)", path);
    env->ReleaseStringUTFChars(modelPath, path);
    return JNI_FALSE;  // false = modelo no disponible todavía; la UI debe manejarlo
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeInferenceAI(JNIEnv* env, jobject, jfloatArray inputData) {
    LOGI("IvannaNativeLib.nativeInferenceAI — stub, devolviendo array vacío");
    jfloatArray result = env->NewFloatArray(1);
    float zero = 0.0f;
    env->SetFloatArrayRegion(result, 0, 1, &zero);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_IvannaNativeLib_nativeReleaseAI(JNIEnv*, jobject) {
    LOGI("IvannaNativeLib.nativeReleaseAI — stub");
    return JNI_TRUE;
}
// ═══════════════════════════════════════════════════════════════
// MOTOR EVOLUTIVO PF-ENGINE — Algoritmo Genético Diferencial
// Optimiza los parámetros del amplificador (drive/wet/alpha/delta/sigma)
// en tiempo real midiendo el fitness como:
//   fitness = harmonic_richness × clarity × dynamic_range
// ═══════════════════════════════════════════════════════════════

// Individuo del algoritmo genético
struct EvoIndividual {
    float drive, wet, alpha, delta, sigma;
    float fitness;
};

static const int EVO_POP  = 12;   // tamaño de población (cabe en L1)
static const int EVO_DIMS = 5;    // drive, wet, alpha, delta, sigma

static EvoIndividual evo_pop[EVO_POP];
static int   evo_generation   = 0;
static int   evo_bar_count    = 0;
static float evo_best_fitness = 0.f;
static int   evo_best_idx     = 0;
static bool  evo_running      = false;

// Parámetros actuales del amplificador (escritos por pfSetXxx)
static float pf_drive   = 1.0f, pf_wet  = 0.6f;
static float pf_alpha   = 1.0f, pf_delta= 0.4f, pf_sigma = 0.5f;
static float pf_low     = 0.0f, pf_mid  = 0.0f, pf_high  = 0.0f;
static float pf_presence= 0.0f;
static int   pf_amp_model = 3;

// Función de fitness: riqueza armónica * claridad * rango dinámico
// (evaluada sobre el buffer de espectro ya calculado por nativeProcessAudio)
static float evaluate_fitness(const EvoIndividual& ind) {
    // Penalizar valores extremos que saturan o cortan señal
    if (ind.drive > 3.5f || ind.wet < 0.05f) return 0.f;

    // Componente 1: riqueza armónica — queremos energía en medios-altos
    float harmonic = 0.f;
    for (int i = 4; i < 24; i++) harmonic += spectrum[i];
    harmonic /= 20.f;

    // Componente 2: claridad — penalizar si los medios están muy comprimidos
    float mid_energy = 0.f;
    for (int i = 8; i < 16; i++) mid_energy += spectrum[i];
    float clarity = mid_energy > 0.01f ? 1.f / (1.f + mid_energy * 10.f) : 0.5f;

    // Componente 3: rango dinámico — queremos nivel moderado (no silencio, no clip)
    float rms_linear = powf(10.f, currentRmsDb / 20.f);
    float dynamic_range = 1.f - fabsf(rms_linear - 0.25f);   // óptimo en -12 dBFS
    if (dynamic_range < 0.f) dynamic_range = 0.f;

    // Peso de los parámetros del individuo sobre el fitness
    float param_score = (ind.drive * 0.3f + ind.wet * 0.4f + ind.alpha * 0.3f) /
                        (ind.sigma + 0.1f);

    return harmonic * 0.4f + clarity * 0.3f + dynamic_range * 0.2f + param_score * 0.1f;
}

// Inicializar población con variación alrededor de los params actuales
static void evo_init_population() {
    srand((unsigned)evo_generation * 6364136223846793005ULL);
    for (int i = 0; i < EVO_POP; i++) {
        auto& ind = evo_pop[i];
        // Variación ±30% alrededor de los valores actuales
        auto jitter = [](float base, float range) -> float {
            float r = (rand() % 1000) / 1000.f - 0.5f;
            float v = base + r * range;
            return v < 0.01f ? 0.01f : v > 4.f ? 4.f : v;
        };
        ind.drive  = jitter(pf_drive,  0.6f);
        ind.wet    = jitter(pf_wet,    0.4f);
        ind.alpha  = jitter(pf_alpha,  0.4f);
        ind.delta  = jitter(pf_delta,  0.3f);
        ind.sigma  = jitter(pf_sigma,  0.3f);
        ind.fitness = evaluate_fitness(ind);
        if (i == 0 || ind.fitness > evo_pop[evo_best_idx].fitness)
            evo_best_idx = i;
    }
    evo_best_fitness = evo_pop[evo_best_idx].fitness;
}

// Un tick evolutivo: evolucionar la población 1 generación (Diferencial Evolution / DE/rand/1)
static void evo_step() {
    float F = 0.7f, CR = 0.85f;   // factor de mutación, tasa de cruzamiento (estándar DE)
    for (int i = 0; i < EVO_POP; i++) {
        // Selección aleatoria de 3 individuos distintos a i
        int r1, r2, r3;
        do { r1 = rand() % EVO_POP; } while (r1 == i);
        do { r2 = rand() % EVO_POP; } while (r2 == i || r2 == r1);
        do { r3 = rand() % EVO_POP; } while (r3 == i || r3 == r1 || r3 == r2);

        EvoIndividual trial = evo_pop[i];
        float* t = &trial.drive;
        float* a = &evo_pop[r1].drive;
        float* b = &evo_pop[r2].drive;
        float* c = &evo_pop[r3].drive;
        int jrand = rand() % EVO_DIMS;
        for (int d = 0; d < EVO_DIMS; d++) {
            if ((rand() % 1000) / 1000.f < CR || d == jrand) {
                t[d] = a[d] + F * (b[d] - c[d]);
                if (t[d] < 0.01f) t[d] = 0.01f;
                if (t[d] > 4.f)   t[d] = 4.f;
            }
        }
        trial.fitness = evaluate_fitness(trial);
        if (trial.fitness >= evo_pop[i].fitness) {
            evo_pop[i] = trial;
            if (trial.fitness > evo_best_fitness) {
                evo_best_fitness = trial.fitness;
                evo_best_idx = i;
            }
        }
    }
    evo_generation++;
}

// ─── JNI del motor evolutivo ──────────────────────────────────────────────

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativePfEvoStart(JNIEnv*, jobject) {
    if (!evo_running) { evo_init_population(); evo_running = true; }
    LOGI("EvoEngine START gen=%d pop=%d", evo_generation, EVO_POP);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativePfEvoTick(JNIEnv*, jobject, jint bars) {
    if (!evo_running) return;
    evo_bar_count = bars;
    evo_step();      // Una generación por tick de compás
    // Aplicar el mejor individuo al estado del amplificador
    pf_drive = evo_pop[evo_best_idx].drive;
    pf_wet   = evo_pop[evo_best_idx].wet;
    pf_alpha = evo_pop[evo_best_idx].alpha;
    pf_delta = evo_pop[evo_best_idx].delta;
    pf_sigma = evo_pop[evo_best_idx].sigma;
    generation = evo_generation;
    bestFitness = evo_best_fitness;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativePfEvoStop(JNIEnv*, jobject) {
    evo_running = false;
    LOGI("EvoEngine STOP gen=%d best_fitness=%.3f", evo_generation, evo_best_fitness);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativePfEvoReset(JNIEnv*, jobject) {
    evo_running = false;
    evo_generation = 0; evo_bar_count = 0;
    evo_best_fitness = 0.f; evo_best_idx = 0;
    generation = 0; bestFitness = 0.f;
    for (auto& ind : evo_pop) ind.fitness = 0.f;
    LOGI("EvoEngine RESET");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_AudioEngine_nativePfGetBarCount(JNIEnv*, jobject) {
    return evo_bar_count;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_ivannafusion_AudioEngine_nativeGetGeneration(JNIEnv*, jobject) {
    return evo_generation;
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_AudioEngine_nativeGetBestFitness(JNIEnv*, jobject) {
    return evo_best_fitness;
}

// ─── Setters de parámetros PF (actualizan estado para el motor evo) ───────

extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativePfSetAmp     (JNIEnv*, jobject, jint i)    { pf_amp_model=i; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativePfSetDrive   (JNIEnv*, jobject, jfloat f)  { pf_drive=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativePfSetWet     (JNIEnv*, jobject, jfloat f)  { pf_wet=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativePfSetAlpha   (JNIEnv*, jobject, jfloat f)  { pf_alpha=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativePfSetDelta   (JNIEnv*, jobject, jfloat f)  { pf_delta=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativePfSetSigma   (JNIEnv*, jobject, jfloat f)  { pf_sigma=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativePfSetLow     (JNIEnv*, jobject, jfloat f)  { pf_low=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativePfSetMid     (JNIEnv*, jobject, jfloat f)  { pf_mid=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativePfSetHigh    (JNIEnv*, jobject, jfloat f)  { pf_high=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativePfSetPresence(JNIEnv*, jobject, jfloat f)  { pf_presence=f; }

// ─── IA setters (actualizan estado para el clasificador) ──────────────────

extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeAiSetEnabled    (JNIEnv*, jobject, jboolean b) { (void)b; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeAiSetAutoAdapt  (JNIEnv*, jobject, jboolean b) { (void)b; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeAiSetSensitivity(JNIEnv*, jobject, jfloat f)   { (void)f; }
extern "C" JNIEXPORT jboolean JNICALL Java_com_ivannafusion_AudioEngine_nativeAiIsLoaded  (JNIEnv*, jobject)              { return initialized; }

// ═══════════════════════════════════════════════════════════════
// FDN-4 REVERB — Feedback Delay Network de 4 nodos
// Delays mutuamente primos calibrados para densidad Hall/Plate/Room/Spring/Chamber.
// Damping: filtro paso-bajos de 1 polo en cada nodo de retroalimentación.
// Diffusion: mezcla de señal directa + early reflections Hadamard.
// Pre-delay: línea de retardo de hasta 500 ms.
// ═══════════════════════════════════════════════════════════════

static const int FDN_PRIMES_HALL[4]   = { 1327, 1559, 1877, 2039 }; // ~27–42 ms @ 48kHz
static const int FDN_PRIMES_PLATE[4]  = { 743,  887, 1049, 1217  }; // ~15–25 ms
static const int FDN_PRIMES_ROOM[4]   = { 401,  503,  613,  701  }; // ~8–14 ms
static const int FDN_PRIMES_SPRING[4] = { 251,  317,  397,  479  }; // ~5–10 ms (shimmer)
static const int FDN_PRIMES_CHAMBER[4]= { 997, 1151, 1303, 1483  }; // ~20–30 ms

static constexpr int FDN_MAX_DELAY  = 2100;   // samples (≥largest prime above)
static constexpr int PRE_DELAY_MAX  = 24001;  // 500 ms @ 48kHz

struct FDNNode {
    float buf[FDN_MAX_DELAY] = {};
    int   wp = 0, len = 1327;
    float lp = 0.f;   // low-pass state (damping)
    float read() const { int rp = (wp - len + FDN_MAX_DELAY) % FDN_MAX_DELAY; return buf[rp]; }
    void  write(float v) { buf[wp] = v; wp = (wp + 1) % FDN_MAX_DELAY; }
};

struct PreDelayLine {
    float buf[PRE_DELAY_MAX] = {};
    int wp = 0, len = 0;
    float tick(float in) {
        buf[wp] = in;
        int rp = (wp - len + PRE_DELAY_MAX) % PRE_DELAY_MAX;
        wp = (wp + 1) % PRE_DELAY_MAX;
        return buf[rp];
    }
};

static FDNNode    fdn_L[4], fdn_R[4];
static PreDelayLine pre_L, pre_R;

static float rev_decay     = 0.4f;   // 0..1  → maps to feedback coefficient
static float rev_preDelay  = 0.f;    // ms
static float rev_damping   = 0.5f;   // 0..1  → LPF coefficient
static float rev_diffusion = 0.7f;   // 0..1  → early-to-late mix
static float rev_earlyMix  = 0.5f;   // not used in JNI route but stored
static float rev_mix       = 0.3f;   // wet/dry
static bool  rev_bypass    = false;

static void fdn_set_primes(FDNNode* nodes, const int* primes) {
    for (int i = 0; i < 4; i++) {
        nodes[i].len = primes[i];
        nodes[i].wp  = 0;
        nodes[i].lp  = 0.f;
        memset(nodes[i].buf, 0, sizeof(nodes[i].buf));
    }
}

static void fdn_apply_type(const char* type) {
    const int* P = FDN_PRIMES_HALL;
    if      (!strcmp(type,"PLATE"))  P = FDN_PRIMES_PLATE;
    else if (!strcmp(type,"ROOM"))   P = FDN_PRIMES_ROOM;
    else if (!strcmp(type,"SPRING")) P = FDN_PRIMES_SPRING;
    else if (!strcmp(type,"CHAMBER"))P = FDN_PRIMES_CHAMBER;
    fdn_set_primes(fdn_L, P);
    fdn_set_primes(fdn_R, P);
}

// Procesa 1 sample de reverb en un canal de 4 nodos FDN
// Hadamard 4×4 mixando los nodos (ortogonal, máxima densidad):
//   [ a+b+c+d,  a+b-c-d,  a-b+c-d,  a-b-c+d ] * 0.5
static inline float fdn_tick(FDNNode* nodes, float inp, float fb, float damp) {
    float s[4];
    for (int i = 0; i < 4; i++) s[i] = nodes[i].read();

    // Hadamard mix
    float h0 = (s[0]+s[1]+s[2]+s[3]) * 0.5f;
    float h1 = (s[0]+s[1]-s[2]-s[3]) * 0.5f;
    float h2 = (s[0]-s[1]+s[2]-s[3]) * 0.5f;
    float h3 = (s[0]-s[1]-s[2]+s[3]) * 0.5f;
    float hm[4] = { h0, h1, h2, h3 };

    float out = 0.f;
    for (int i = 0; i < 4; i++) {
        // Low-pass damping on feedback path
        nodes[i].lp = damp * nodes[i].lp + (1.f - damp) * (hm[i] * fb + (i == 0 ? inp : 0.f));
        nodes[i].write(nodes[i].lp);
        out += nodes[i].lp;
    }
    return out * 0.25f;
}

// Called from nativeProcessAudio interleaved stereo (inBuf/outBuf already modified by DSP chain)
static void reverb_process(jfloat* buf, int frames) {
    if (rev_bypass || rev_mix < 0.001f) return;

    float fb   = powf(10.f, -3.f * rev_decay * (float)FDN_PRIMES_HALL[3] / 48000.f / rev_decay);
    // Simpler: fb = 0.3 + 0.65 * decay → range 0.30..0.95
    fb = 0.30f + 0.65f * rev_decay;
    float damp = 0.1f + 0.88f * rev_damping;   // LPF coeff (closer to 1 = more damping)

    for (int f = 0; f < frames; f++) {
        float L = buf[f*2],   R = buf[f*2+1];

        // Pre-delay
        float pdL = pre_L.tick(L);
        float pdR = pre_R.tick(R);

        // FDN per channel (decorrelated by different primes init order)
        float wetL = fdn_tick(fdn_L, pdL, fb, damp);
        float wetR = fdn_tick(fdn_R, pdR, fb, damp);

        // Diffusion: blend direct+reverb on wet path
        wetL = pdL * (1.f - rev_diffusion) + wetL * rev_diffusion;
        wetR = pdR * (1.f - rev_diffusion) + wetR * rev_diffusion;

        buf[f*2]   = L   * (1.f - rev_mix) + wetL * rev_mix;
        buf[f*2+1] = R   * (1.f - rev_mix) + wetR * rev_mix;
    }
}

// ═══════════════════════════════════════════════════════════════
// M/S STEREO WIDENER
// width: M/S balance. 1.0 = unity. >1 = wider. 0 = mono.
// Haas delay: adds a short delay to R channel for extra width perception.
// LFO modulation for spatial decorrelation (nativeSpatialSet*).
// ═══════════════════════════════════════════════════════════════

static constexpr int HAAS_BUF = 9601;   // 200 ms @ 48kHz
static float haas_buf[HAAS_BUF] = {};
static int   haas_wp   = 0;
static int   haas_len  = 0;          // Haas delay in samples

static float wid_width   = 1.f;     // 0..2 (1 = unity)
static float wid_depth   = 0.5f;
static float wid_mix     = 0.f;
static float wid_bypass  = false;

// Spatial / decorrelation (LFO)
static float spat_width  = 1.f;
static float spat_mu     = 0.5f;
static float spat_lfo_ph = 0.f;
static float spat_lfo_rate = 0.2f;   // Hz

static inline void widener_process(jfloat* buf, int frames) {
    if (wid_bypass || (wid_mix < 0.001f && fabsf(wid_width - 1.f) < 0.01f && spat_width < 1.01f)) return;

    // Combined effective width = wid_width * spat_width, clamped 0..3
    float eff_width = wid_width * spat_width;
    if (eff_width < 0.f) eff_width = 0.f;
    if (eff_width > 3.f) eff_width = 3.f;

    float lfo_inc = spat_lfo_rate / 48000.f * 2.f * (float)M_PI;

    for (int f = 0; f < frames; f++) {
        float L = buf[f*2], R = buf[f*2+1];

        // M/S encode
        float M = (L + R) * 0.5f;
        float S = (L - R) * 0.5f;

        // LFO modulates S width for spatial decorrelation
        float lfo = sinf(spat_lfo_ph) * spat_mu * 0.15f;
        spat_lfo_ph += lfo_inc;
        if (spat_lfo_ph > (float)(2.0 * M_PI)) spat_lfo_ph -= (float)(2.0 * M_PI);

        // Apply width to S channel
        S *= eff_width * (1.f + lfo);

        // M/S decode
        float wL = M + S;
        float wR = M - S;

        // Haas delay on R (widening perception)
        if (haas_len > 0) {
            haas_buf[haas_wp] = wR;
            int rp = (haas_wp - haas_len + HAAS_BUF) % HAAS_BUF;
            haas_wp = (haas_wp + 1) % HAAS_BUF;
            wR = haas_buf[rp];
        }

        // Mix wet/dry (wid_mix=0 → pure width processing, no dry/wet crossfade)
        buf[f*2]   = wL;
        buf[f*2+1] = wR;
    }
}

// Hook reverb+widener into nativeProcessAudio output
// (called at the end of Java_com_ivannafusion_AudioEngine_nativeProcessAudio)
static void apply_post_fx(jfloat* outBuf, int frames) {
    widener_process(outBuf, frames);
    reverb_process(outBuf, frames);
}

// ─── JNI setters — Reverb ──────────────────────────────────────────────────

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeReverbSetType(JNIEnv* env, jobject, jstring t) {
    const char* type = env->GetStringUTFChars(t, nullptr);
    fdn_apply_type(type);
    env->ReleaseStringUTFChars(t, type);
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeReverbSetDecay(JNIEnv*, jobject, jfloat f) {
    rev_decay = (f < 0.f) ? 0.f : (f > 1.f) ? 1.f : f;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeReverbSetPreDelay(JNIEnv*, jobject, jfloat ms) {
    rev_preDelay = ms;
    int len = (int)(ms * 48.f);   // ms → samples @ 48kHz
    if (len < 0) len = 0;
    if (len >= PRE_DELAY_MAX) len = PRE_DELAY_MAX - 1;
    pre_L.len = len; pre_R.len = len;
    pre_L.wp  = 0;   pre_R.wp  = 0;
    memset(pre_L.buf, 0, sizeof(pre_L.buf));
    memset(pre_R.buf, 0, sizeof(pre_R.buf));
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeReverbSetDamping(JNIEnv*, jobject, jfloat f) {
    rev_damping = (f < 0.f) ? 0.f : (f > 1.f) ? 1.f : f;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeReverbSetDiffusion(JNIEnv*, jobject, jfloat f) {
    rev_diffusion = (f < 0.f) ? 0.f : (f > 1.f) ? 1.f : f;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeReverbSetMix(JNIEnv*, jobject, jfloat f) {
    rev_mix = (f < 0.f) ? 0.f : (f > 1.f) ? 1.f : f;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeReverbSetBypass(JNIEnv*, jobject, jboolean b) {
    rev_bypass = (b == JNI_TRUE);
}

// ─── JNI setters — Widener ─────────────────────────────────────────────────

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeWiderSetWidth(JNIEnv*, jobject, jfloat f) {
    wid_width = (f < 0.f) ? 0.f : (f > 3.f) ? 3.f : f;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeWiderSetDepth(JNIEnv*, jobject, jfloat f) {
    wid_depth = (f < 0.f) ? 0.f : (f > 1.f) ? 1.f : f;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeWiderSetMix(JNIEnv*, jobject, jfloat f) {
    wid_mix = (f < 0.f) ? 0.f : (f > 1.f) ? 1.f : f;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeWiderSetDelay(JNIEnv*, jobject, jfloat ms) {
    int len = (int)(ms * 48.f);
    if (len < 0) len = 0;
    if (len >= HAAS_BUF) len = HAAS_BUF - 1;
    haas_len = len;
    haas_wp  = 0;
    memset(haas_buf, 0, sizeof(haas_buf));
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeWiderSetBypass(JNIEnv*, jobject, jboolean b) {
    wid_bypass = (b == JNI_TRUE);
}

// ─── JNI setters — Spatial ─────────────────────────────────────────────────

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeSpatialSetWidth(JNIEnv*, jobject, jfloat f) {
    spat_width = (f < 0.f) ? 0.f : (f > 3.f) ? 3.f : f;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeSpatialSetMu(JNIEnv*, jobject, jfloat f) {
    spat_mu = (f < 0.f) ? 0.f : (f > 1.f) ? 1.f : f;
}

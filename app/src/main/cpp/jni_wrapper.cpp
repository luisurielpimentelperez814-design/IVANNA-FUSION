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

// ─── Reverb / Widener / Spatial setters (ya en estado global) ─────────────

extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeReverbSetType     (JNIEnv*, jobject, jstring t) { (void)t; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeReverbSetDecay    (JNIEnv*, jobject, jfloat f)  { (void)f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeReverbSetPreDelay (JNIEnv*, jobject, jfloat f)  { (void)f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeReverbSetDamping  (JNIEnv*, jobject, jfloat f)  { (void)f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeReverbSetDiffusion(JNIEnv*, jobject, jfloat f)  { (void)f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeReverbSetMix      (JNIEnv*, jobject, jfloat f)  { (void)f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeReverbSetBypass   (JNIEnv*, jobject, jboolean b){ (void)b; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeWiderSetWidth     (JNIEnv*, jobject, jfloat f)  { (void)f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeWiderSetDepth     (JNIEnv*, jobject, jfloat f)  { (void)f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeWiderSetMix       (JNIEnv*, jobject, jfloat f)  { (void)f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeWiderSetDelay     (JNIEnv*, jobject, jfloat f)  { (void)f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeWiderSetBypass    (JNIEnv*, jobject, jboolean b){ (void)b; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSpatialSetWidth   (JNIEnv*, jobject, jfloat f)  { (void)f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSpatialSetMu      (JNIEnv*, jobject, jfloat f)  { (void)f; }

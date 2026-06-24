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
extern "C" JNIEXPORT jint JNICALL Java_com_ivannafusion_AudioEngine_nativeGetGeneration(JNIEnv*, jobject) { return generation; }
extern "C" JNIEXPORT jfloat JNICALL Java_com_ivannafusion_AudioEngine_nativeGetBestFitness(JNIEnv*, jobject) { return bestFitness; }
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
// OMEGA DAEMON - Funciones JNI para controlar el daemon root
// ═══════════════════════════════════════════════════════════════

// Declaraciones forward de funciones del daemon
extern bool omega_daemon_start();
extern void omega_daemon_stop();
extern bool omega_daemon_is_running();

extern "C" JNIEXPORT jboolean JNICALL Java_com_ivannafusion_OmegaDaemon_nativeStart(JNIEnv* env, jobject thiz) {
    LOGI("OmegaDaemon::nativeStart() llamado desde Kotlin");
    bool result = omega_daemon_start();
    if (result) {
        LOGI("✅ OmegaDaemon iniciado correctamente");
    } else {
        LOGE("❌ OmegaDaemon no pudo iniciarse (puede requerir root)");
    }
    return static_cast<jboolean>(result);
}

extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_OmegaDaemon_nativeStop(JNIEnv* env, jobject thiz) {
    LOGI("OmegaDaemon::nativeStop() llamado desde Kotlin");
    omega_daemon_stop();
    LOGI("OmegaDaemon detenido");
}

extern "C" JNIEXPORT jboolean JNICALL Java_com_ivannafusion_OmegaDaemon_nativeIsRunning(JNIEnv* env, jobject thiz) {
    bool running = omega_daemon_is_running();
    return static_cast<jboolean>(running);
}

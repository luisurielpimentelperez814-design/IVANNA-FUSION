#include <jni.h>
#include <cmath>
#include <vector>
#include <android/log.h>
#include <string.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "IVANNA_DSP", __VA_ARGS__)

// ═══════════════════════════════════════════════════════════════
// Suavizado exponencial (filtro de un polo / promedio móvil
// exponencial) — técnica estándar de DSP, NO un descubrimiento
// nuevo. Se usa para que los medidores de la UI (RMS, espectro) no
// salten abruptamente frame a frame, sin alterar el audio real que
// pasa por el motor (eq/comp/exciter siguen procesando muestra a
// muestra sin este suavizado).
//
// smooth(prev, raw, mu) = (prev + mu*raw) / (1 + mu)
// Es algebraicamente equivalente a un EMA con alpha = mu/(1+mu):
// smooth = prev*(1-alpha) + raw*alpha. mu más alto = más reactivo
// (sigue rápido los cambios), mu más bajo = más suave (más estable
// visualmente, más lento a reaccionar).
// ═══════════════════════════════════════════════════════════════
static inline float smoothMetric(float prev, float raw, float mu) {
    if (std::isnan(raw) || std::isinf(raw)) return prev;
    if (std::isnan(prev) || std::isinf(prev)) return raw;
    return (prev + mu * raw) / (1.0f + mu);
}

// Biquad Filter para EQ
struct Biquad {
    float b0=1, b1=0, b2=0, a1=0, a2=0, x1=0, x2=0, y1=0, y2=0;
    
    void setPeaking(float freq, float sr, float gainDB, float Q) {
        float A = powf(10.0f, gainDB/40.0f);
        float w0 = 2.0f * M_PI * freq / sr;
        float alpha = sinf(w0) / (2.0f * Q);
        float cosw0 = cosf(w0);
        
        b0 = 1.0f + alpha*A;
        b1 = -2.0f*cosw0;
        b2 = 1.0f - alpha*A;
        float a0 = 1.0f + alpha/A;
        a1 = -2.0f*cosw0;
        a2 = 1.0f - alpha/A;
        
        b0/=a0; b1/=a0; b2/=a0; a1/=a0; a2/=a0;
    }
    
    float process(float in) {
        float out = b0*in + b1*x1 + b2*x2 - a1*y1 - a2*y2;
        x2=x1; x1=in; y2=y1; y1=out;
        return out;
    }
};

// Estado global
static std::vector<Biquad> eq(8);
static float compThresh=-20, compRatio=4, compAttack=10, compRelease=100;
static float compKnee=6, compMakeup=0, compEnv=0;
static bool compBypass=false;
static float excDrive=1, excMix=0.5;
static bool excBypass=false, fftEnabled=false;
static bool initialized=false;
static float lastInputLevel=0, lastOutputLevel=0;

// Variables para IA y métricas (stubs)
static float currentRmsDb = -60.0f;
static float spectrum[32] = {0};
static float correlation = 1.0f;
static int latencyMicros = 5000;static int generation = 0;
static float bestFitness = 0.0f;
static float tempo = 0.0f;
static char detectedGenre[32] = "Unknown";

// INICIALIZACIÓN
extern "C" JNIEXPORT jboolean JNICALL Java_com_ivannafusion_AudioEngine_nativeInit(JNIEnv*, jobject, jint sr, jint) {
    for(int i=0; i<8; i++) eq[i].setPeaking(1000, 48000, 0, 1.41);
    initialized=true;
    LOGI("✅ DSP initialized at %d Hz", sr);
    return JNI_TRUE;
}

// PROCESAMIENTO DE AUDIO REAL
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
            float newEnv = coeff*lvl + (1.0f-coeff)*compEnv;
            // Protección: si compEnv alguna vez se vuelve NaN/Inf (por
            // ejemplo si attack/release llegaran a 0 desde la UI,
            // dividiendo por cero en expf(-1.0f/0)), sin este chequeo
            // el compresor quedaría mudo permanentemente hasta
            // nativeReset(), porque NaN se propaga en toda operación
            // aritmética siguiente.
            compEnv = (std::isnan(newEnv) || std::isinf(newEnv)) ? lvl : newEnv;
            
            float gr = (compEnv > compThresh) ? (compThresh-compEnv)*(1.0f-1.0f/compRatio) : 0;
            float gain = powf(10.0f, (gr+compMakeup)/20.0f);
            L *= gain;
            R *= gain;
        }
        
        if(!excBypass) {
            L = L + (tanhf(L*excDrive) - L) * excMix;
            R = R + (tanhf(R*excDrive) - R) * excMix;
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
    
    // Suavizado exponencial del RMS reportado a la UI (ver smoothMetric
    // arriba). El audio en sí ya se procesó sample-a-sample sin este
    // suavizado; esto solo estabiliza el NÚMERO que se muestra en el
    // medidor, evitando que salte bruscamente entre bloques de 2048
    // muestras (~43ms a 48kHz) procesados consecutivamente.
    if (lastInputLevel > 0.001f) {
        float rawRmsDb = 20.0f * log10f(lastInputLevel);
        currentRmsDb = smoothMetric(currentRmsDb, rawRmsDb, 0.35f);
    } else {
        currentRmsDb = smoothMetric(currentRmsDb, -60.0f, 0.35f);
    }
    
    env->ReleaseFloatArrayElements(in, inBuf, 0);
    env->ReleaseFloatArrayElements(out, outBuf, 0);
}

// ═══════════════════════════════════════════════════════════════
// FUNCIONES DE IA Y MÉTRICAS (STUBS - Implementaciones básicas)
// ═══════════════════════════════════════════════════════════════

extern "C" JNIEXPORT jfloat JNICALL Java_com_ivannafusion_AudioEngine_nativeAiGetRmsDb(JNIEnv*, jobject) {
    return currentRmsDb;
}

extern "C" JNIEXPORT jfloatArray JNICALL Java_com_ivannafusion_AudioEngine_nativeAiGetSpectrum(JNIEnv* env, jobject) {
    jfloatArray result = env->NewFloatArray(32);
    if (result != nullptr) {
        // Generar espectro simulado basado en el nivel actual
        float normalizedLevel = (currentRmsDb + 60.0f) / 60.0f;
        if (normalizedLevel < 0) normalizedLevel = 0;
        if (normalizedLevel > 1) normalizedLevel = 1;
        
        for (int i = 0; i < 32; i++) {
            float freqFactor = 1.0f - fabsf((static_cast<float>(i) - 16.0f) / 16.0f);
            float rawBand = normalizedLevel * freqFactor * 0.8f;
            // Suavizado con mu adaptativo: si el valor crudo cambió
            // mucho respecto al anterior, mu sube (sigue el cambio más
            // rápido); si cambió poco, mu se mantiene bajo (más estable
            // visualmente). Evita que las barras "tiemblen" entre
            // llamadas consecutivas sin atrasar reacciones a cambios
            // reales de volumen.
            float adaptiveMu = 0.3f * (1.0f + fabsf(rawBand - spectrum[i]));
            spectrum[i] = fmaxf(0.0f, fminf(1.0f, smoothMetric(spectrum[i], rawBand, adaptiveMu)));
        }
        env->SetFloatArrayRegion(result, 0, 32, spectrum);
    }
    return result;
}

extern "C" JNIEXPORT jfloat JNICALL Java_com_ivannafusion_AudioEngine_nativeGetCorrelation(JNIEnv*, jobject) {
    return correlation;
}

extern "C" JNIEXPORT jint JNICALL Java_com_ivannafusion_AudioEngine_nativeGetLatencyMicros(JNIEnv*, jobject) {
    return latencyMicros;
}

extern "C" JNIEXPORT jint JNICALL Java_com_ivannafusion_AudioEngine_nativeGetGeneration(JNIEnv*, jobject) {
    return generation;
}

extern "C" JNIEXPORT jfloat JNICALL Java_com_ivannafusion_AudioEngine_nativeGetBestFitness(JNIEnv*, jobject) {
    return bestFitness;
}

extern "C" JNIEXPORT jstring JNICALL Java_com_ivannafusion_AudioEngine_nativeAiGetDetectedGenre(JNIEnv* env, jobject) {
    return env->NewStringUTF(detectedGenre);
}

extern "C" JNIEXPORT jfloat JNICALL Java_com_ivannafusion_AudioEngine_nativeAiGetTempo(JNIEnv*, jobject) {
    return tempo;
}

// ═══════════════════════════════════════════════════════════════
// FUNCIONES EQ
// ═══════════════════════════════════════════════════════════════

extern "C" JNIEXPORT void JNICALL
Java_com_ivannafusion_AudioEngine_nativeSetEQGain(JNIEnv*, jobject, jint band, jfloat gain) {
    if(band>=0 && band<8) {
        eq[band].setPeaking(1000, 48000, gain, 1.41);
        LOGI("EQ Band %d: %.1f dB", band, gain);
    }
}

extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQFreq(JNIEnv*, jobject, jint, jfloat) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQQ(JNIEnv*, jobject, jint, jfloat) {}
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetEQBypass(JNIEnv*, jobject, jint, jboolean) {}

// ═══════════════════════════════════════════════════════════════
// FUNCIONES COMPRESOR
// ═══════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorThreshold(JNIEnv*, jobject, jfloat f) { compThresh=f; LOGI("Comp Threshold: %.1f dB", f); }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorRatio(JNIEnv*, jobject, jfloat f) { compRatio=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorAttack(JNIEnv*, jobject, jfloat f) { compAttack=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorRelease(JNIEnv*, jobject, jfloat f) { compRelease=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorKnee(JNIEnv*, jobject, jfloat f) { compKnee=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorMakeup(JNIEnv*, jobject, jfloat f) { compMakeup=f; }
extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetCompressorBypass(JNIEnv*, jobject, jboolean b) { compBypass=b; }

// ═══════════════════════════════════════════════════════════════
// FUNCIONES EXCITER
// ═══════════════════════════════════════════════════════════════

extern "C" JNIEXPORT void JNICALL Java_com_ivannafusion_AudioEngine_nativeSetExciterDrive(JNIEnv*, jobject, jfloat f) { excDrive=f; LOGI("Exciter Drive: %.2f", f); }
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

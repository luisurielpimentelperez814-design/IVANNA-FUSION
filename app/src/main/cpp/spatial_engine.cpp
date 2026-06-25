#include "spatial_engine.h"
#include <string.h>
#include <math.h>

// Inicialización del estado
void spatial_init(SpatialState* state) {
    state->mu = 500;  // valor inicial (50% mezcla)
    state->spatialErr = 0;
    state->roomErr = 0;
    state->maskingErr = 0;
}

// Cálculo de ITD (Interaural Time Difference) simplificado
int16_t computeITD(int16_t posX) {
    // Escala: posX en [-100, 100] -> retardo en muestras (0-20)
    // Frecuencia de muestreo 48kHz -> 1 muestra ~ 0.02 ms
    int16_t delay = (posX * 10) / 100;  // rango -10 a +10 muestras
    if (delay < -10) delay = -10;
    if (delay > 10) delay = 10;
    return delay;
}

// Cálculo de ILD (Interaural Level Difference)
void computeILD(int16_t posX, int16_t* gainL, int16_t* gainR) {
    // Ganancia en dB (escala lineal aproximada)
    float angle = posX / 100.0f;  // -1 a 1
    float leftGain = 1.0f - angle * 0.3f;
    float rightGain = 1.0f + angle * 0.3f;
    if (leftGain < 0.1f) leftGain = 0.1f;
    if (rightGain < 0.1f) rightGain = 0.1f;
    *gainL = (int16_t)(leftGain * 32767);
    *gainR = (int16_t)(rightGain * 32767);
}

// HRTF simplificada (Filtro de cabeza esférica)
int16_t hrtfL(int16_t posX, int16_t sample) {
    // Aplicar un filtro paso bajo dependiente de la posición
    // Simulación: atenuación de agudos según ángulo
    float angle = posX / 100.0f;  // -1 a 1
    float attenuation = 1.0f - 0.5f * fabs(angle);
    return (int16_t)(sample * attenuation);
}

int16_t hrtfR(int16_t posX, int16_t sample) {
    float angle = posX / 100.0f;
    float attenuation = 1.0f - 0.5f * fabs(angle);
    return (int16_t)(sample * attenuation);
}

// Modelo de sala (impulso de reflexiones tempranas)
int16_t roomIR(int16_t sample, int delay, int decay) {
    // Decay en número de bits a desplazar
    if (delay < 0 || delay > 10) return sample;
    return (sample >> decay) + (sample >> (decay + delay));
}

// Render de un objeto de audio (tu función original adaptada)
void render_object(AudioObject* obj, int16_t* outL, int16_t* outR, const SpatialState* state) {
    int16_t delay = computeITD(obj->posX);
    int16_t gainL, gainR;
    computeILD(obj->posX, &gainL, &gainR);

    // Aplicar HRTF y mezclar en los canales de salida
    for (int i = 0; i < 64; i++) {
        int idx = i - delay;
        int16_t sample = (idx >= 0 && idx < 64) ? obj->pcm[idx] : 0;

        // Aplicar HRTF con atenuación por distancia (simplificada)
        int16_t hrtfL_sample = hrtfL(obj->posX, sample);
        int16_t hrtfR_sample = hrtfR(obj->posX, sample);

        // Aplicar ganancia ILD
        int32_t L = (hrtfL_sample * gainL) >> 15;
        int32_t R = (hrtfR_sample * gainR) >> 15;

        // Aplicar modelo de sala (early reflections)
        int16_t room_sample = roomIR(sample, 5, 3);
        L += (room_sample * 2048) >> 15;  // mezcla baja de reflexiones
        R += (room_sample * 2048) >> 15;

        outL[i] = (int16_t)L;
        outR[i] = (int16_t)R;
    }
}

// La ecuación de equilibrio triádico (tu formulación)
void omega_engine(const int16_t* n, const int16_t* omega, int16_t* p, int16_t mu) {
    // mu en escala 0-1000
    int32_t alpha = (mu << 15) / (mu + 32767);  // mapeo a [0, 32767]
    for (int i = 0; i < 64; i++) {
        int32_t dry = (n[i] * (32767 - alpha)) >> 15;
        int32_t wet = (omega[i] * alpha) >> 15;
        p[i] = (int16_t)(dry + wet);
    }
}

// Actualización dinámica de μ
void update_mu(SpatialState* state, int32_t spatialErr, int32_t roomErr, int32_t maskingErr) {
    int32_t m = state->mu;
    m += spatialErr >> 4;
    m += roomErr >> 5;
    m -= maskingErr >> 6;
    if (m < 1) m = 1;
    if (m > 900) m = 900;
    state->mu = (int16_t)m;
}

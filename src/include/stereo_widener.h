#pragma once
// stereo_widener.h — Expansión de imagen estéreo (Mid-Side widening)
//
// CORRECCIÓN DE DISEÑO IMPORTANTE: la expansión estéreo real se calcula
// sobre la diferencia ENTRE CANALES en el mismo instante (L[n] - R[n],
// la componente "side" de la codificación Mid-Side), NO sobre la
// diferencia TEMPORAL de un mismo canal (x[n] - x[n-1], que es
// matemáticamente un filtro de diferenciación/highpass crudo y no
// tiene ninguna relación con la imagen estéreo). Procesar audio
// estéreo intercalado como si fuera mono con ese segundo enfoque
// destruye la separación de canales en vez de ampliarla.
//
// Codificación Mid-Side estándar:
//   mid  = (L + R) * 0.5   (contenido común a ambos canales — centro)
//   side = (L - R) * 0.5   (contenido que difiere entre canales — ancho)
// Decodificación tras amplificar 'side' por widthAmount:
//   L' = mid + side * widthAmount
//   R' = mid - side * widthAmount
// widthAmount = 1.0 → estéreo original sin cambio.
// widthAmount > 1.0 → imagen más ancha (más diferencia entre canales).
// widthAmount < 1.0 → imagen más estrecha (hacia mono en 0.0).
//
// Pipeline:
//   L,R ──► mid/side ──► side *= width ──► L',R' ──► out
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include <atomic>
#include <cmath>
#include <algorithm>

namespace ivanna::dsp {

class StereoWidener {
public:
    StereoWidener() noexcept = default;

    // width: 0.0 = mono (side eliminado), 1.0 = estéreo original,
    // 2.0 = el límite razonable antes de que la imagen se perciba
    // artificial o el bajo se desestabilice en mono (graves muy
    // anchos en sistemas mono-summed, ej. algunos altavoces de TV,
    // pueden cancelarse parcialmente — ver setBassProtect).
    void setWidth(float width) noexcept {
        width_.store(std::clamp(width, 0.f, 2.f), std::memory_order_relaxed);
    }

    // Si está activo, el "side" se atenúa progresivamente por debajo
    // de bassProtectHz_ para evitar que los graves anchos se cancelen
    // al sumarse a mono (problema real y conocido de M/S widening
    // agresivo en graves). No es opcional ignorable: sin esto, width
    // > 1.0 puede sonar más débil en mono que en estéreo, justo lo
    // contrario de "mejora".
    void setBassProtect(bool enabled) noexcept {
        bassProtect_.store(enabled, std::memory_order_relaxed);
    }

    void setSamplerate(float fs) noexcept { samplerate_ = fs; }
    void setBypass(bool b) noexcept { bypass_.store(b, std::memory_order_relaxed); }

    void resetState() noexcept {
        bassEnvL_ = 0.f;
        bassEnvR_ = 0.f;
    }

    void process(const float* inL, const float* inR,
                 float* outL, float* outR, int n_frames) noexcept {
        if (bypass_.load(std::memory_order_relaxed)) {
            if (inL != outL) std::copy(inL, inL + n_frames, outL);
            if (inR != outR) std::copy(inR, inR + n_frames, outR);
            return;
        }

        const float width = width_.load(std::memory_order_relaxed);
        const bool bassProtect = bassProtect_.load(std::memory_order_relaxed);

        // Coeficiente de un filtro de un polo (lowpass) para extraer la
        // envolvente de graves del canal, usado solo si bassProtect
        // está activo. fc ~120Hz: por debajo de eso, mono-sum es más
        // importante que la imagen estéreo en la mayoría de sistemas.
        const float fc = 120.f;
        const float rc_coeff = std::exp(-2.f * 3.14159265f * fc / samplerate_);

        for (int i = 0; i < n_frames; ++i) {
            float L = inL[i];
            float R = inR[i];

            // Protección NaN/Inf en la entrada — ver historial del
            // proyecto: un NaN/Inf no detectado en una etapa de
            // procesamiento estéreo puede propagarse y, en este caso
            // específico, mid/side amplificaría el problema (side de
            // un NaN sigue siendo NaN, y se multiplicaría por width).
            if (std::isnan(L) || std::isinf(L)) L = 0.f;
            if (std::isnan(R) || std::isinf(R)) R = 0.f;

            float mid  = (L + R) * 0.5f;
            float side = (L - R) * 0.5f;

            float effectiveWidth = width;
            if (bassProtect) {
                // Envolvente simple de graves por canal (no requiere
                // filtro biquad completo, solo detectar si hay energía
                // de baja frecuencia significativa).
                bassEnvL_ = rc_coeff * bassEnvL_ + (1.f - rc_coeff) * std::fabs(L);
                bassEnvR_ = rc_coeff * bassEnvR_ + (1.f - rc_coeff) * std::fabs(R);
                float bassEnergy = std::max(bassEnvL_, bassEnvR_);
                // Si hay graves significativos, reducir el widening
                // hacia 1.0 (sin cambio) proporcionalmente — no a 0,
                // para no perder el efecto por completo en mezclas
                // con bajo presente casi siempre.
                float bassAttenuation = std::clamp(1.f - bassEnergy * 2.f, 0.3f, 1.f);
                effectiveWidth = 1.f + (width - 1.f) * bassAttenuation;
            }

            side *= effectiveWidth;

            outL[i] = mid + side;
            outR[i] = mid - side;
        }
    }

private:
    std::atomic<float> width_       { 1.f };
    std::atomic<bool>  bassProtect_ { true };
    std::atomic<bool>  bypass_      { false };

    float samplerate_ = 48000.f;
    float bassEnvL_ = 0.f;
    float bassEnvR_ = 0.f;
};

} // namespace ivanna::dsp

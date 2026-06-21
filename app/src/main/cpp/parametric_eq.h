#ifndef PARAMETRIC_EQ_H
#define PARAMETRIC_EQ_H

#include <cmath>
#include <array>
#include <complex>

namespace dsp {

constexpr int PEQ_BANDS = 8;  // Número de bandas del ecualizador

// Estructura para un filtro biquad (base del EQ paramétrico)
struct BiquadFilter {
    float b0 = 1.0f, b1 = 0.0f, b2 = 0.0f;
    float a0 = 1.0f, a1 = 0.0f, a2 = 0.0f;
    
    // Estado del filtro (muestras anteriores)
    float x1 = 0.0f, x2 = 0.0f;
    float y1 = 0.0f, y2 = 0.0f;
    
    void reset() {
        x1 = x2 = y1 = y2 = 0.0f;
    }
    
    float process(float input) {
        float output = (b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2) / a0;
        
        x2 = x1;
        x1 = input;
        y2 = y1;
        y1 = output;
        
        return output;
    }
};

// Tipos de filtros para el EQ
enum class FilterType {
    LOWPASS,
    HIGHPASS,
    BANDPASS,
    NOTCH,
    PEAK,
    LOWSHELF,
    HIGHSHELF
};

// Banda del ecualizador paramétrico
struct EQBand {
    FilterType type = FilterType::PEAK;
    float frequency = 1000.0f;  // Hz
    float gain_db = 0.0f;       // dB
    float q = 1.0f;             // Factor Q (ancho de banda)
    bool enabled = true;
    
    BiquadFilter filter;
    
    void update_coefficients(float sample_rate) {
        if (!enabled) {
            // Filtro transparente (bypass)
            filter.b0 = 1.0f;
            filter.b1 = 0.0f;
            filter.b2 = 0.0f;
            filter.a0 = 1.0f;
            filter.a1 = 0.0f;
            filter.a2 = 0.0f;
            return;
        }
        
        float w0 = 2.0f * M_PI * frequency / sample_rate;
        float cos_w0 = std::cos(w0);
        float sin_w0 = std::sin(w0);
        float alpha = sin_w0 / (2.0f * q);
        float A = std::pow(10.0f, gain_db / 40.0f); // sqrt de la ganancia lineal
        
        switch (type) {
            case FilterType::LOWPASS:
                filter.b0 = (1.0f - cos_w0) / 2.0f;
                filter.b1 = 1.0f - cos_w0;
                filter.b2 = (1.0f - cos_w0) / 2.0f;
                filter.a0 = 1.0f + alpha;
                filter.a1 = -2.0f * cos_w0;
                filter.a2 = 1.0f - alpha;
                break;
                
            case FilterType::HIGHPASS:
                filter.b0 = (1.0f + cos_w0) / 2.0f;
                filter.b1 = -(1.0f + cos_w0);
                filter.b2 = (1.0f + cos_w0) / 2.0f;
                filter.a0 = 1.0f + alpha;
                filter.a1 = -2.0f * cos_w0;
                filter.a2 = 1.0f - alpha;
                break;
                
            case FilterType::BANDPASS:
                filter.b0 = alpha;
                filter.b1 = 0.0f;
                filter.b2 = -alpha;
                filter.a0 = 1.0f + alpha;
                filter.a1 = -2.0f * cos_w0;
                filter.a2 = 1.0f - alpha;
                break;
                
            case FilterType::NOTCH:
                filter.b0 = 1.0f;
                filter.b1 = -2.0f * cos_w0;
                filter.b2 = 1.0f;
                filter.a0 = 1.0f + alpha;
                filter.a1 = -2.0f * cos_w0;
                filter.a2 = 1.0f - alpha;
                break;
                
            case FilterType::PEAK:
                filter.b0 = 1.0f + alpha * A;
                filter.b1 = -2.0f * cos_w0;
                filter.b2 = 1.0f - alpha * A;
                filter.a0 = 1.0f + alpha / A;
                filter.a1 = -2.0f * cos_w0;
                filter.a2 = 1.0f - alpha / A;
                break;
                
            case FilterType::LOWSHELF: {
                float sqrt_A = std::sqrt(A);
                filter.b0 = A * ((A + 1) - (A - 1) * cos_w0 + 2 * sqrt_A * alpha);
                filter.b1 = 2 * A * ((A - 1) - (A + 1) * cos_w0);
                filter.b2 = A * ((A + 1) - (A - 1) * cos_w0 - 2 * sqrt_A * alpha);
                filter.a0 = (A + 1) + (A - 1) * cos_w0 + 2 * sqrt_A * alpha;
                filter.a1 = -2 * ((A - 1) + (A + 1) * cos_w0);
                filter.a2 = (A + 1) + (A - 1) * cos_w0 - 2 * sqrt_A * alpha;
                break;
            }
                
            case FilterType::HIGHSHELF: {
                float sqrt_A = std::sqrt(A);
                filter.b0 = A * ((A + 1) + (A - 1) * cos_w0 + 2 * sqrt_A * alpha);
                filter.b1 = -2 * A * ((A - 1) + (A + 1) * cos_w0);
                filter.b2 = A * ((A + 1) + (A - 1) * cos_w0 - 2 * sqrt_A * alpha);
                filter.a0 = (A + 1) - (A - 1) * cos_w0 + 2 * sqrt_A * alpha;
                filter.a1 = 2 * ((A - 1) - (A + 1) * cos_w0);
                filter.a2 = (A + 1) - (A - 1) * cos_w0 - 2 * sqrt_A * alpha;
                break;
            }
        }
    }
};

// Ecualizador paramétrico completo
class ParametricEQ {
public:
    static constexpr int MAX_BANDS = PEQ_BANDS;
    
    ParametricEQ() {
        // Configurar bandas por defecto (EQ de 5 bandas)
        bands[0] = {FilterType::HIGHPASS, 80.0f, 0.0f, 0.707f, true, {}};
        bands[1] = {FilterType::PEAK, 250.0f, 0.0f, 1.0f, true, {}};
        bands[2] = {FilterType::PEAK, 1000.0f, 0.0f, 1.0f, true, {}};
        bands[3] = {FilterType::PEAK, 4000.0f, 0.0f, 1.0f, true, {}};
        bands[4] = {FilterType::LOWSHELF, 8000.0f, 0.0f, 0.707f, true, {}};
        
        // Deshabilitar bandas no usadas
        for (int i = 5; i < MAX_BANDS; i++) {
            bands[i].enabled = false;
        }
    }
    
    void set_sample_rate(float sample_rate) {
        this->sample_rate = sample_rate;
        update_all_bands();
    }
    
    void set_band(int index, FilterType type, float freq, float gain_db, float q, bool enabled) {
        if (index >= 0 && index < MAX_BANDS) {
            bands[index].type = type;
            bands[index].frequency = freq;
            bands[index].gain_db = gain_db;
            bands[index].q = q;
            bands[index].enabled = enabled;
            bands[index].update_coefficients(sample_rate);
        }
    }
    
    void set_band_gain(int index, float gain_db) {
        if (index >= 0 && index < MAX_BANDS) {
            bands[index].gain_db = gain_db;
            bands[index].update_coefficients(sample_rate);
        }
    }
    
    void set_band_frequency(int index, float freq) {
        if (index >= 0 && index < MAX_BANDS) {
            bands[index].frequency = freq;
            bands[index].update_coefficients(sample_rate);
        }
    }
    
    void set_band_q(int index, float q) {
        if (index >= 0 && index < MAX_BANDS) {
            bands[index].q = q;
            bands[index].update_coefficients(sample_rate);
        }
    }
    
    void enable_band(int index, bool enabled) {
        if (index >= 0 && index < MAX_BANDS) {
            bands[index].enabled = enabled;
            bands[index].update_coefficients(sample_rate);
        }
    }
    
    void reset() {
        for (int i = 0; i < MAX_BANDS; i++) {
            bands[i].filter.reset();
        }
    }
    
    // Procesar una muestra (mono)
    float process(float input) {
        float output = input;
        
        // Aplicar cada banda en cascada
        for (int i = 0; i < MAX_BANDS; i++) {
            if (bands[i].enabled) {
                output = bands[i].filter.process(output);
            }
        }
        
        return output;
    }
    
    // Procesar buffer estéreo (intercalado LRLRLR)
    void process_stereo(float* buffer, int num_samples) {
        for (int i = 0; i < num_samples; i += 2) {
            buffer[i] = process(buffer[i]);         // Canal izquierdo
            buffer[i + 1] = process(buffer[i + 1]); // Canal derecho
        }
    }
    
    // Procesar buffer mono
    void process_mono(float* buffer, int num_samples) {
        for (int i = 0; i < num_samples; i++) {
            buffer[i] = process(buffer[i]);
        }
    }
    
    const EQBand& get_band(int index) const {
        return bands[index];
    }

private:
    std::array<EQBand, MAX_BANDS> bands;
    float sample_rate = 48000.0f;
    
    void update_all_bands() {
        for (int i = 0; i < MAX_BANDS; i++) {
            bands[i].update_coefficients(sample_rate);
        }
    }
};

} // namespace dsp

#endif // PARAMETRIC_EQ_H

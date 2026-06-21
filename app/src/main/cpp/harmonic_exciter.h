#ifndef HARMONIC_EXCITER_H
#define HARMONIC_EXCITER_H

#include <cmath>
#include <algorithm>
#include <array>

namespace dsp {

// Excitador armónico (genera armónicos para enriquecer el contenido)
class HarmonicExciter {
public:
    HarmonicExciter() {
        reset();
    }
    
    void set_drive(float db) {
        drive_db = db;
        drive_linear = db_to_linear(db);
    }
    
    void set_mix(float mix) {
        this->mix = std::clamp(mix, 0.0f, 1.0f);
    }
    
    void set_harmonic_amount(float amount) {
        harmonic_amount = std::clamp(amount, 0.0f, 2.0f);
    }
    
    void set_harmonic_freq(float freq) {
        harmonic_freq = std::clamp(freq, 20.0f, 20000.0f);
        update_filters();
    }
    
    void set_sample_rate(float sr) {
        sample_rate = sr;
        update_filters();
    }
    
    void reset() {
        for (auto& f : hp_filters) f.reset();
        for (auto& f : lp_filters) f.reset();
        phase = 0.0f;
    }
    
    // Procesar una muestra (mono)
    float process(float input) {
        // Separar frecuencias altas y bajas
        float high_freq = hp_filters[0].process(input);
        float low_freq = input - high_freq;
        
        // Aplicar drive a las frecuencias altas
        float driven = high_freq * drive_linear;
        
        // Generar armónicos usando waveshaping suave
        float harmonics = 0.0f;
        
        // Segundo armónico (octava arriba)
        float h2 = waveshape(driven, 1.0f);
        harmonics += h2 * 0.5f;
        
        // Tercer armónico (quinta arriba de la octava)
        float h3 = waveshape(driven * 1.5f, 0.8f);
        harmonics += h3 * 0.3f;
        
        // Cuarto armónico (dos octavas arriba)
        float h4 = waveshape(driven * 2.0f, 0.6f);
        harmonics += h4 * 0.2f;
        
        // Mezclar armónicos generados
        float excited = high_freq + harmonics * harmonic_amount;
        
        // Filtrar para remover frecuencias no deseadas
        excited = lp_filters[0].process(excited);
        
        // Mezclar señal original con excitada
        float output = low_freq + excited * mix + high_freq * (1.0f - mix);
        
        return output;
    }
    
    // Procesar buffer estéreo (intercalado LRLRLR)
    void process_stereo(float* buffer, int num_samples) {
        for (int i = 0; i < num_samples; i += 2) {
            buffer[i] = process(buffer[i]);
            buffer[i + 1] = process(buffer[i + 1]);
        }
    }
    
    // Procesar buffer mono
    void process_mono(float* buffer, int num_samples) {
        for (int i = 0; i < num_samples; i++) {
            buffer[i] = process(buffer[i]);
        }
    }

private:
    float drive_db = 0.0f;
    float drive_linear = 1.0f;
    float mix = 0.5f;
    float harmonic_amount = 1.0f;
    float harmonic_freq = 2000.0f;
    float sample_rate = 48000.0f;
    float phase = 0.0f;
    
    // Filtros simples de un polo
    struct OnePoleFilter {
        float coeff = 0.0f;
        float state = 0.0f;
        
        void set_cutoff(float freq, float sr, bool highpass = false) {
            float x = 2.0f * M_PI * freq / sr;
            coeff = highpass ? (1.0f - x) : x;
            coeff = std::clamp(coeff, 0.001f, 0.999f);
        }
        
        void reset() {
            state = 0.0f;
        }
        
        float process(float input) {
            state = state + coeff * (input - state);
            return state;
        }
        
        float process_highpass(float input) {
            float lp = process(input);
            return input - lp;
        }
    };
    
    std::array<OnePoleFilter, 2> hp_filters;
    std::array<OnePoleFilter, 2> lp_filters;
    
    void update_filters() {
        // Highpass para separar frecuencias altas
        hp_filters[0].set_cutoff(harmonic_freq, sample_rate, true);
        
        // Lowpass para suavizar armónicos generados
        lp_filters[0].set_cutoff(15000.0f, sample_rate);
    }
    
    // Waveshaping suave (tanh)
    static float waveshape(float input, float gain) {
        return std::tanh(input * gain);
    }
    
    static float db_to_linear(float db) {
        return std::pow(10.0f, db / 20.0f);
    }
};

} // namespace dsp

#endif // HARMONIC_EXCITER_H

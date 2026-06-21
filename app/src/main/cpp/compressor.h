#ifndef COMPRESSOR_H
#define COMPRESSOR_H

#include <cmath>
#include <algorithm>

// Compresor de dinámica de audio
class Compressor {
public:
    Compressor() = default;
    
    void set_threshold(float db) {
        threshold_db = db;
        threshold_linear = db_to_linear(db);
    }
    
    void set_ratio(float ratio) {
        this->ratio = std::max(1.0f, ratio);
    }
    
    void set_attack(float ms) {
        attack_ms = std::max(0.1f, ms);
        update_coefficients();
    }
    
    void set_release(float ms) {
        release_ms = std::max(1.0f, ms);
        update_coefficients();
    }
    
    void set_knee(float db) {
        knee_db = std::max(0.0f, db);
    }
    
    void set_makeup_gain(float db) {
        makeup_gain_db = db;
        makeup_gain_linear = db_to_linear(db);
    }
    
    void set_sample_rate(float sr) {
        sample_rate = sr;
        update_coefficients();
    }
    
    void reset() {
        envelope_db = -96.0f;
    }
    
    // Procesar una muestra (mono)
    float process(float input) {
        // Convertir a dB
        float input_db = linear_to_db(std::abs(input) + 1e-10f);
        
        // Calcular ganancia de compresión
        float gain_reduction_db = 0.0f;
        
        if (input_db > threshold_db + knee_db / 2.0f) {
            // Compresión completa
            gain_reduction_db = (threshold_db - input_db) * (1.0f - 1.0f / ratio);
        } else if (input_db > threshold_db - knee_db / 2.0f && knee_db > 0.0f) {
            // Zona de knee (transición suave)
            float x = input_db - threshold_db + knee_db / 2.0f;
            gain_reduction_db = (1.0f - 1.0f / ratio) * -x * x / (2.0f * knee_db);
        }
        
        // Aplicar suavizado de envelope
        float target_envelope_db = gain_reduction_db;
        
        if (target_envelope_db < envelope_db) {
            // Ataque
            envelope_db = attack_coeff * envelope_db + (1.0f - attack_coeff) * target_envelope_db;
        } else {
            // Release
            envelope_db = release_coeff * envelope_db + (1.0f - release_coeff) * target_envelope_db;
        }
        
        // Aplicar ganancia de compresión + makeup
        float total_gain_db = envelope_db + makeup_gain_db;
        float total_gain_linear = db_to_linear(total_gain_db);
        
        return input * total_gain_linear;
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
    
    // Obtener nivel de reducción de ganancia actual (en dB)
    float get_gain_reduction_db() const {
        return envelope_db;
    }

private:
    float threshold_db = -20.0f;
    float threshold_linear = 0.1f;
    float ratio = 4.0f;
    float attack_ms = 10.0f;
    float release_ms = 100.0f;
    float knee_db = 6.0f;
    float makeup_gain_db = 0.0f;
    float makeup_gain_linear = 1.0f;
    float sample_rate = 48000.0f;
    
    float attack_coeff = 0.0f;
    float release_coeff = 0.0f;
    float envelope_db = -96.0f;
    
    void update_coefficients() {
        // Coeficientes de suavizado exponencial
        attack_coeff = std::exp(-1.0f / (attack_ms * 0.001f * sample_rate));
        release_coeff = std::exp(-1.0f / (release_ms * 0.001f * sample_rate));
    }
    
    static float db_to_linear(float db) {
        return std::pow(10.0f, db / 20.0f);
    }
    
    static float linear_to_db(float linear) {
        return 20.0f * std::log10(linear);
    }
};

#endif // COMPRESSOR_H

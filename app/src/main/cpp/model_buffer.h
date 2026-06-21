#pragma once
// model_buffer.h — Buffers pre-asignados para entrada/salida de inferencia
// de AIInference, sin malloc/free en el bucle de audio.
//
// ExecuTorch's Module::forward() acepta executorch::aten::Tensor (vía
// make_tensor_ptr), que internamente referencia memoria que el llamador
// posee. ModelInputBuffer/ModelOutputBuffer son esa memoria: se reservan
// una sola vez al iniciar el daemon (ver omega_daemon.cpp) y se
// reutilizan en cada bloque, en línea con la regla de oro del proyecto
// de "sin malloc/free en el bucle de audio ni en el bucle de inferencia".
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include <array>
#include <cstddef>
#include <cstdint>

namespace ivanna::dsp {

// Tamaño máximo de bloque de audio que se envía a inferencia por llamada.
// El modelo de clasificación de contenido (música/voz/silencio) no
// necesita el bloque completo de audio de baja latencia (512 muestras a
// 48kHz = ~10.6ms); para detección de tipo de contenido es más eficiente
// acumular una ventana más larga (p.ej. 1024 muestras ~21ms) antes de
// invocar forward(), reduciendo la frecuencia de inferencias sin afectar
// la latencia del audio en sí (el modelo corre en un hilo separado del
// productor, ver omega_daemon.cpp).
inline constexpr int kModelMaxInputSamples  = 1024;
inline constexpr int kModelMaxOutputValues  = 16;  // p.ej. logits de clase + embeddings cortos

// Buffer de entrada pre-asignado: acumula muestras de audio mono
// (promedio L/R, o solo L si el modelo se entrenó así) hasta tener
// suficientes para una inferencia, sin reservar memoria dinámica.
class ModelInputBuffer {
public:
    ModelInputBuffer() noexcept { clear(); }

    void clear() noexcept {
        write_pos_ = 0;
        data_.fill(0.0f);
    }

    // Agrega muestras al buffer de acumulación. Devuelve true si el
    // buffer alcanzó kModelMaxInputSamples y está listo para inferencia
    // (el llamador debe leer data()/size() y luego clear()).
    bool append(const float* samples, int n) noexcept {
        int remaining = kModelMaxInputSamples - write_pos_;
        int to_copy = (n < remaining) ? n : remaining;
        for (int i = 0; i < to_copy; ++i) {
            data_[write_pos_++] = samples[i];
        }
        return write_pos_ >= kModelMaxInputSamples;
    }

    const float* data() const noexcept { return data_.data(); }
    int size() const noexcept { return write_pos_; }
    bool isFull() const noexcept { return write_pos_ >= kModelMaxInputSamples; }

private:
    std::array<float, kModelMaxInputSamples> data_{};
    int write_pos_ = 0;
};

// Buffer de salida pre-asignado: recibe el resultado copiado desde
// InferenceResult::output (que a su vez ya copió desde la memoria
// interna de ExecuTorch, ver ai_inference.cpp). No referencia memoria
// de ExecuTorch directamente para evitar problemas de tiempo de vida si
// el llamador retiene este buffer más allá de la siguiente llamada a
// forward().
class ModelOutputBuffer {
public:
    ModelOutputBuffer() noexcept { clear(); }

    void clear() noexcept {
        count_ = 0;
        data_.fill(0.0f);
    }

    // Copia hasta kModelMaxOutputValues valores desde 'src'. Si
    // n > kModelMaxOutputValues, los valores sobrantes se descartan
    // (no se trunca silenciosamente sin que el llamador pueda saberlo:
    // ver wasTruncated()).
    void assign(const float* src, int n) noexcept {
        count_ = (n < kModelMaxOutputValues) ? n : kModelMaxOutputValues;
        truncated_ = (n > kModelMaxOutputValues);
        for (int i = 0; i < count_; ++i) data_[i] = src[i];
    }

    const float* data() const noexcept { return data_.data(); }
    int size() const noexcept { return count_; }
    bool wasTruncated() const noexcept { return truncated_; }

    // Índice de la clase con mayor valor (uso típico: logits de
    // clasificación música/voz/silencio -> argmax).
    int argmax() const noexcept {
        if (count_ == 0) return -1;
        int best = 0;
        for (int i = 1; i < count_; ++i) {
            if (data_[i] > data_[best]) best = i;
        }
        return best;
    }

private:
    std::array<float, kModelMaxOutputValues> data_{};
    int  count_ = 0;
    bool truncated_ = false;
};

} // namespace ivanna::dsp

#pragma once
// ai_inference.h — Wrapper sobre la API de alto nivel de ExecuTorch
// (executorch::extension::Module) para inferencia local de audio.
//
// API real verificada contra la documentación oficial de ExecuTorch
// (docs.pytorch.org/executorch, "Using ExecuTorch with C++" /
// "Module Extension Tutorial"):
//   #include <executorch/extension/module/module.h>
//   #include <executorch/extension/tensor/tensor.h>
//   executorch::extension::Module module("model.pte");
//   auto tensor = executorch::extension::make_tensor_ptr({shape...}, data);
//   auto outputs = module.forward(tensor);
//
// Se usa la API de alto nivel (Module/forward) en vez de la de bajo nivel
// (Program/Method/MemoryManager/HierarchicalAllocator) porque esta última
// requiere conocer de antemano el grafo exacto de memoria planificada del
// modelo (tamaños de buffers intermedios por backend) — datos que solo
// existen una vez que hay un .pte real exportado y perfilado. Module ya
// gestiona esa planificación de memoria internamente.
//
// Estado real al escribir este header: NO existe ningún archivo .pte en
// el repositorio (se verificó con `find . -iname "*.pte"` antes de
// escribir este código). AIInference::load() funciona correctamente con
// cualquier .pte válido en cuanto exista uno (ver
// omega_engine/export_to_executorch.py para el pipeline de exportación);
// hasta entonces, isLoaded() devuelve false y processBlock() hace
// passthrough explícito — comportamiento idéntico al que ya documentaba
// ivanna_native_lib_v2.cpp antes de esta integración.
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include <cstdint>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

// Forward declaration para no obligar a todo el árbol de includes del
// proyecto a depender de los headers de ExecuTorch cuando solo necesitan
// el tipo AIInference (p.ej. ivanna_native_lib_v2.cpp solo necesita
// load()/isLoaded()/processBlock(), no los tipos internos de Module).
namespace executorch::extension { class Module; }

namespace ivanna::dsp {

// Resultado de carga del modelo. No es solo bool: distinguir POR QUÉ
// falló (archivo ausente vs. .pte corrupto/incompatible vs. error del
// runtime) importa para diagnosticar en campo sin acceso a logcat.
enum class ModelLoadStatus {
    kOk,
    kFileNotFound,
    kInvalidProgram,      // el .pte existe pero Module no pudo parsearlo
    kMethodLoadFailed,     // el programa cargó pero el método "forward" falló al preparar
    kNotLoaded             // estado inicial, antes de llamar a load()
};

// Resultado de una inferencia.
struct InferenceResult {
    bool        ok = false;
    // Vector de salida ya copiado a memoria propia del llamador (no
    // apunta a memoria interna de ExecuTorch, que puede invalidarse en
    // la siguiente llamada a forward()).
    std::vector<float> output;
    float       inference_time_ms = 0.0f;
};

// Envuelve un modelo ExecuTorch cargado para clasificación ligera de
// contenido de audio (música/voz/video) sobre bloques cortos de audio
// mono. Pensada para llamarse desde un hilo de trabajo dedicado
// (omega_daemon.cpp), NUNCA desde el callback de AudioFlinger/AAudio —
// forward() puede tardar varios milisegundos incluso en un modelo de
// <5MB cuantizado, y eso es inaceptable en un hilo de tiempo real
// estricto.
class AIInference {
public:
    AIInference() noexcept;
    ~AIInference();

    // Sin copia (Module no es copiable, y duplicar un modelo cargado en
    // memoria no tiene sentido para este caso de uso).
    AIInference(const AIInference&) = delete;
    AIInference& operator=(const AIInference&) = delete;

    // Carga un modelo .pte desde disco. Thread-safe respecto a
    // isLoaded()/runInference() (protegido con mutex interno), pero NO
    // pensada para llamarse desde el hot path — hace I/O de archivo y
    // parseo del programa, ambos potencialmente lentos.
    ModelLoadStatus load(const std::string& pte_path) noexcept;

    // Libera el modelo cargado (si alguno). Seguro llamar aunque no haya
    // ninguno cargado.
    void unload() noexcept;

    bool isLoaded() const noexcept;

    // Última ruta cargada exitosamente (vacío si nunca se cargó nada).
    const std::string& modelPath() const noexcept { return loaded_path_; }

    // Ejecuta forward() sobre un bloque de audio mono de 'n_samples'
    // muestras float32 en [-1, 1]. Devuelve el vector de salida del
    // modelo (p.ej. logits de 3 clases: música/voz/silencio) ya copiado
    // a memoria propia.
    //
    // Si !isLoaded(), devuelve InferenceResult{ok=false} de inmediato
    // sin tocar 'input' — el llamador debe interpretar ok=false como
    // "sin clasificación disponible todavía", no como error fatal.
    InferenceResult runInference(const float* input, int n_samples) noexcept;

    // Última latencia de inferencia medida (0 si nunca se ejecutó).
    float lastInferenceTimeMs() const noexcept { return last_inference_ms_; }

private:
    std::unique_ptr<executorch::extension::Module> module_;
    mutable std::mutex mutex_;
    std::string loaded_path_;
    float last_inference_ms_ = 0.0f;
};

} // namespace ivanna::dsp

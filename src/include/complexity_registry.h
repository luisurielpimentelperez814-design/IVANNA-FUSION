#pragma once
// complexity_registry.h — Control de presupuesto de CPU/memoria por módulo
// (portado desde core/complexity_registry.h, "Industrial Platform v2.0",
// que vivía desconectado del build real de Gradle — ver README.md sección
// "BASE ACTIVA DEL PROYECTO"). Idea de diseño rescatada porque es
// independiente del problema de los headers de DSP de ese directorio
// (biquad sin SIMD real, pipeline con resampler placeholder).
//
// USO PREVISTO: registrar módulos UNA VEZ al inicio de la app/daemon, y
// llamar a validate_runtime() ocasionalmente para diagnóstico/logging —
// NUNCA en el hot path de audio. register_module() usa std::vector::
// push_back y validate_runtime() hace strcmp en un bucle lineal; ambas
// operaciones son inaceptables dentro del callback de audio en tiempo
// real (violarían la regla de "sin malloc en el bucle de audio" que
// ya sigue el resto del proyecto en src/cpp/effect_library.cpp).
//
// © 2026 Luis Uriel Pimentel Pérez — GORE TNS

#include <vector>
#include <cstring>
#include <cstdint>

namespace ivanna::control {

enum class ComplexityLevel : uint8_t {
    TRIVIAL = 1,   // <0.1ms, <1KB
    SIMPLE = 2,    // <0.5ms, <4KB
    MODERATE = 3,  // <2ms, <16KB
    COMPLEX = 4,   // <5ms, <64KB
    CRITICAL = 5   // >5ms, requiere aprobación explícita
};

struct ModuleBudget {
    const char* name;
    ComplexityLevel level;
    float cpu_budget_ms;
    size_t memory_budget;
    uint32_t max_dependencies;
    bool requires_approval;
};

class ComplexityRegistry {
public:
    static ComplexityRegistry& instance() {
        static ComplexityRegistry r;
        return r;
    }

    bool register_module(const ModuleBudget& budget) noexcept {
        if (budget.requires_approval && !approval_granted_) {
            return false;
        }
        modules_.push_back(budget);
        return true;
    }

    // Diagnóstico fuera del hot path: ¿el módulo 'name' se mantuvo
    // dentro de su presupuesto declarado (con 10% de margen)?
    bool validate_runtime(const char* name, float actual_cpu_ms,
                           size_t actual_mem) const noexcept {
        for (const auto& m : modules_) {
            if (strcmp(m.name, name) == 0) {
                if (actual_cpu_ms > m.cpu_budget_ms * 1.1f) return false;
                if (actual_mem > m.memory_budget * 1.1f) return false;
                return true;
            }
        }
        return false;
    }

    size_t module_count() const noexcept { return modules_.size(); }

private:
    std::vector<ModuleBudget> modules_;
    bool approval_granted_ = false;
};

#define IVANNA_REGISTER_MODULE(name, level, cpu_ms, mem_bytes) \
    static bool name##_registered = [] { \
        return ivanna::control::ComplexityRegistry::instance().register_module({ \
            #name, ivanna::control::ComplexityLevel::level, \
            cpu_ms, mem_bytes, 3, level >= 5 \
        }); \
    }()

} // namespace ivanna::control

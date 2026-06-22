#pragma once
// IVANNA Complexity Control - Runtime budget enforcement
// Complexity Level: 1/5 | Memory: <4KB

#include <vector>
#include <cstring>
#include <cstdint>

namespace ivanna::control {

enum class ComplexityLevel : uint8_t {
    TRIVIAL = 1,   // <0.1ms, <1KB
    SIMPLE = 2,    // <0.5ms, <4KB
    MODERATE = 3,  // <2ms, <16KB
    COMPLEX = 4,   // <5ms, <64KB
    CRITICAL = 5   // >5ms, requires approval
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

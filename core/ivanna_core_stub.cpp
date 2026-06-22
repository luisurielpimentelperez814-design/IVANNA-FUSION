// IVANNA-FUSION — stub de compilación para ivanna_core STATIC
// Los headers del core son header-only; este archivo provee la unidad
// de traducción mínima para que CMake no aborte con "no sources".
// biquad_neon.h es el primero — ya tiene guards NEON/scalar.
#include "complexity_registry.h"
#include "dsp/biquad_neon.h"
#include "ai/ai_controller.h"
#include "consistency/device_fingerprint.h"
#include "spatial/hrtf_engine.h"

extern "C" const char* ivanna_core_version() { return "2.0.0"; }

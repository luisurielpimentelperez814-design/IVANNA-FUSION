cmake_minimum_required(VERSION 3.22.1)

project("ivanna_fusion")

# Configuración de optimización para ARM64
set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -funroll-loops -march=armv8.2-a+fp16+dotprod -fPIC")

# Motor DSP real, compartido con el efecto de audioserver (src/cpp, src/include)
set(IVANNA_DSP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../src")

# ── Librería principal de Ivanna (carga IvannaNativeLib.kt) ────────────────
# Reúne en UNA sola librería los 17 símbolos Java_com_ivannafusion_IvannaNativeLib_*
# que IvannaNativeLib.kt requiere, repartidos entre estos archivos:
#   - evolutionary_kernel.cpp : nativeInitializeEvolution/GetBestFitness/
#                               GetGeneration/EvolveStep (+ bindings AudioEngine ya existentes)
#   - phase_oracle.cpp        : nativePredictSamples/GetPhaseState/SetPhaseParameters
#                               (+ binding AudioEngine ya existente)
#   - audio_orchestrator.cpp  : nativeInitAudioEngine/ProcessAudio/ReleaseAudioEngine
#                               (+ bindings AudioEngine completos, AAudio real)
#   - ivanna_native_lib_v2.cpp: EQ/Compresor/Exciter reales (ParametricEQ,
#                               Compressor, HarmonicExciter) + presets + AI passthrough
# NOTA: ivanna_native_lib.cpp (primera versión, sin DSP real) se conserva en
# el repo sin compilar — ver comentario al final de este archivo.
add_library(ivanna_native SHARED
    audio_orchestrator.cpp
    phase_oracle.cpp
    evolutionary_kernel.cpp
    ivanna_native_lib_v2.cpp
    "${IVANNA_DSP_DIR}/cpp/biquad_neon.cpp"
    "${IVANNA_DSP_DIR}/cpp/compressor.cpp"
    "${IVANNA_DSP_DIR}/cpp/harmonic_exciter.cpp"
    "${IVANNA_DSP_DIR}/cpp/parametric_eq.cpp"
)

target_include_directories(ivanna_native PRIVATE
    "${IVANNA_DSP_DIR}/include"
)

target_link_libraries(ivanna_native
    android
    log
    atomic
    aaudio
)

# Librería del motor Omega (JNI)
add_library(omega_engine_native SHARED
    audio_pipeline_android.cpp
)

target_link_libraries(omega_engine_native
    android
    log
    atomic
)

# Librería del efecto Omega (JNI)
add_library(omega_effect SHARED
    omega_effect.cpp
)

target_link_libraries(omega_effect
    android
    log
    atomic
)

# Librería del daemon Omega (JNI)
add_library(omega_daemon SHARED
    omega_daemon.cpp
)

target_link_libraries(omega_daemon
    android
    log
    atomic
)

# ── Motor PF Engine ─────────────────────────────────────────────────────────
# shm_hyperplane.cpp no define ningún símbolo IvannaNativeLib (solo
# Java_com_ivannafusion_ShmManager_nativeMlock), así que se mantiene como
# librería separada sin riesgo de colisión con ivanna_native.
# ivanna_native_lib_v2.cpp se retiró de aquí (ahora compila únicamente
# dentro de ivanna_native, arriba) para evitar que IvannaNativeLib.kt
# (que solo carga "ivanna_native" y "pf_engine") cargue los mismos 41+
# símbolos Java_com_ivannafusion_IvannaNativeLib_* desde DOS .so distintos.
add_library(pf_engine SHARED
    shm_hyperplane.cpp
)

target_link_libraries(pf_engine
    android
    log
    atomic
)

# ─────────────────────────────────────────────────────────────────────────────
# Nota histórica: ivanna_native_lib.cpp (primera versión de los bindings de
# IvannaNativeLib, sin motor DSP real detrás — solo std::atomic) se conserva
# en el repositorio como referencia, pero deliberadamente NO se agrega a
# ningún add_library() de este archivo. La versión activa y compilada es
# ivanna_native_lib_v2.cpp (dentro de ivanna_native, arriba), que respalda
# los mismos símbolos JNI con ParametricEQ/Compressor/HarmonicExciter reales.
# ─────────────────────────────────────────────────────────────────────────────

# IVANNA-FUSION TRASCENDENTAL
# © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.

cmake_minimum_required(VERSION 3.22.1)
project("ivanna_trascendental")

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -funroll-loops -march=armv8.2-a+fp16+dotprod -fPIC -Wall -Wextra")

# Motor DSP real, compartido con el efecto de audioserver (src/cpp, src/include)
set(IVANNA_DSP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../src")

set(IVANNA_SOURCES
    audio_orchestrator.cpp
    phase_oracle.cpp
    evolutionary_kernel.cpp
    shm_hyperplane.cpp
    ivanna_native_lib_v2.cpp
    "${IVANNA_DSP_DIR}/cpp/biquad_neon.cpp"
    "${IVANNA_DSP_DIR}/cpp/compressor.cpp"
    "${IVANNA_DSP_DIR}/cpp/harmonic_exciter.cpp"
    "${IVANNA_DSP_DIR}/cpp/parametric_eq.cpp"
)
# Nota: ivanna_native_lib.cpp (versión sin DSP real, solo placeholders en
# memoria) se conserva en el repo sin compilar, como referencia histórica
# y por si se necesita revertir. La versión activa de build es
# ivanna_native_lib_v2.cpp, que expone los mismos 41 símbolos JNI pero
# respaldados por el motor DSP real (ParametricEQ, Compressor,
# HarmonicExciter) compartido con el efecto de audioserver.

add_library(ivanna_trascendental SHARED ${IVANNA_SOURCES})

target_include_directories(ivanna_trascendental PRIVATE
    "${IVANNA_DSP_DIR}/include"
)

find_library(log-lib log)
find_library(android-lib android)
find_library(aaudio-lib aaudio)

target_link_libraries(ivanna_trascendental
    ${log-lib}
    ${android-lib}
    ${aaudio-lib}
)

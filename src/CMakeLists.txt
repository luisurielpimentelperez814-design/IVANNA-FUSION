cmake_minimum_required(VERSION 3.22)
project(ivanna_fusion VERSION 2.0 LANGUAGES C CXX)

# ─── Estándar C++17 ──────────────────────────────────────────────────────────
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ─── Flags de compilación ─────────────────────────────────────────────────────
set(CMAKE_CXX_FLAGS_RELEASE
    "-O3 -ffast-math -march=armv8-a+simd -ftree-vectorize \
     -fno-exceptions -fno-rtti \
     -ffunction-sections -fdata-sections \
     -Wno-unused-result")

# ─── Fuentes IVANNA FUSION (core DSP) ────────────────────────────────────────
set(IVANNA_SOURCES
    cpp/biquad_neon.cpp
    cpp/parametric_eq.cpp
    cpp/compressor.cpp
    cpp/harmonic_exciter.cpp
    cpp/ivanna_fusion.cpp
    cpp/effect_library.cpp
)

# ─── Fuentes PF-ENGINE (integradas en la librería principal) ─────────────────
set(PF_SOURCES
    ../pf_engine/core/pf_engine.cpp
    ../pf_engine/core/pf_evolution.cpp
    ../pf_engine/dsp/pf_dsp.cpp
    ../pf_engine/amps/amp_models.cpp
    ../pf_engine/learning/pf_fft.cpp
    ../pf_engine/learning/pf_learning.cpp
)

# ─── Fuentes FFT Effect (capa espectral) ──────────────────────────────────────
set(FFT_SOURCES
    fft/ivanna_fft_effect.c
)

# ─── Biblioteca compartida ────────────────────────────────────────────────────
add_library(ivanna_fusion SHARED
    ${IVANNA_SOURCES}
    ${PF_SOURCES}
)

target_include_directories(ivanna_fusion PRIVATE
    include
    fft
    # NDK audio headers (disponibles en android.toolchain.cmake)
    ../pf_engine/core
    ../pf_engine/learning
    ${CMAKE_SYSROOT}/usr/include
)

# ─── Definiciones de compilación ─────────────────────────────────────────────
target_compile_definitions(ivanna_fusion PRIVATE
    ANDROID
    __ANDROID__
    IVANNA_FUSION_V2
)

# ─── Bibliotecas del sistema Android ─────────────────────────────────────────
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(ivanna_fusion PRIVATE
    ${log-lib}
    ${android-lib}
)

# ─── Linker flags ────────────────────────────────────────────────────────────
set_target_properties(ivanna_fusion PROPERTIES
    LINK_FLAGS "-static-libstdc++ -Wl,--gc-sections -Wl,--strip-all -Wl,-soname,libivanna_fusion.so"
)
set(ANDROID_STL "c++_static")

# ─── Visibilidad ──────────────────────────────────────────────────────────────
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden")
target_compile_definitions(ivanna_fusion PRIVATE -DHAVE_VISIBILITY=1)

message(STATUS "IVANNA FUSION DSP v2.0 — ABI: ${ANDROID_ABI}")
message(STATUS "  Core: 8-band PEQ + Soft-Knee Compressor + Harmonic Exciter")
message(STATUS "  PF-ENGINE: Amp Models (Marshall/Fender/Vox/70sRock) + Evolution + FFT Learning")
message(STATUS "  FFT Effect: Spectral bass/treble enhancement")
message(STATUS "  NEON SIMD: arm64-v8a")

# ─── IVANNA FFT EFFECT ────────────────────────────────────────────────────────
# Compilar como efecto AudioFlinger independiente (libivanna_fft_effect.so)
add_library(ivanna_fft_effect SHARED
    fft/ivanna_fft_effect.c
)

target_include_directories(ivanna_fft_effect PRIVATE
    fft
    include
    ${CMAKE_SYSROOT}/usr/include
)

target_compile_definitions(ivanna_fft_effect PRIVATE
    ANDROID
    __ANDROID__
)

target_link_libraries(ivanna_fft_effect PRIVATE
    ${log-lib}
    ${android-lib}
)

set_target_properties(ivanna_fft_effect PROPERTIES
    LINK_FLAGS "-Wl,--gc-sections -Wl,--strip-all -Wl,-soname,libivanna_fft_effect.so"
)

message(STATUS "IVANNA FFT Effect — ABI: ${ANDROID_ABI}")

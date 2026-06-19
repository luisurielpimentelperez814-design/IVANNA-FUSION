cmake_minimum_required(VERSION 3.22)
project(ivanna_fusion VERSION 1.0 LANGUAGES CXX C)

# ─── Estándar C++17 ──────────────────────────────────────────────────────────
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ─── Flags de compilación ─────────────────────────────────────────────────────
# -O3 + -ffast-math: optimizaciones agresivas (seguro para DSP donde NaN/Inf no importan)
# -march=armv8-a+simd: habilita instrucciones NEON en ARM64
# -ftree-vectorize: auto-vectorización del compilador
# -fno-exceptions / -fno-rtti: reduce tamaño del .so
set(CMAKE_CXX_FLAGS_RELEASE
    "-O3 -ffast-math -march=armv8-a+simd -ftree-vectorize \
     -fno-exceptions -fno-rtti \
     -ffunction-sections -fdata-sections \
     -Wno-unused-result")

# ─── Fuentes ──────────────────────────────────────────────────────────────────
set(IVANNA_SOURCES
    cpp/biquad_neon.cpp
    cpp/parametric_eq.cpp
    cpp/compressor.cpp
    cpp/harmonic_exciter.cpp
    cpp/ivanna_fusion.cpp
    cpp/effect_library.cpp
)

# ─── Biblioteca compartida ────────────────────────────────────────────────────
add_library(ivanna_fusion SHARED ${IVANNA_SOURCES})

target_include_directories(ivanna_fusion PRIVATE
    include
    # NDK audio headers (disponibles en android.toolchain.cmake)
    ${CMAKE_SYSROOT}/usr/include
)

# ─── Definiciones de compilación ─────────────────────────────────────────────
target_compile_definitions(ivanna_fusion PRIVATE
    ANDROID
    __ANDROID__
)

# ─── Bibliotecas del sistema Android ─────────────────────────────────────────
find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(ivanna_fusion PRIVATE
    ${log-lib}
    ${android-lib}
)

# ─── Linker flags: eliminar símbolos no usados, minimizar tamaño ──────────────
set_target_properties(ivanna_fusion PROPERTIES
    LINK_FLAGS "-static-libstdc++ -Wl,--gc-sections -Wl,--strip-all -Wl,-soname,libivanna_fusion.so"
)
set(ANDROID_STL "c++_static")

# ─── Visibilidad: solo AUDIO_EFFECT_LIBRARY_INFO_SYM debe ser visible ─────────
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden")
target_compile_definitions(ivanna_fusion PRIVATE
    -DHAVE_VISIBILITY=1
)

message(STATUS "IVANNA FUSION DSP — build para ABI: ${ANDROID_ABI}")
message(STATUS "  Samplerate target: 48000 Hz stereo")
message(STATUS "  NEON SIMD: habilitado para arm64-v8a")

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

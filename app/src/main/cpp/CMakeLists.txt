cmake_minimum_required(VERSION 3.18.1)
project("ivanna_jni")

# ─────────────────────────────────────────────────────────────────────────────
# Target ivanna_jni (JNI wrapper)
# ─────────────────────────────────────────────────────────────────────────────
add_library(ivanna_jni SHARED
    jni_wrapper.cpp
    evolutionary_kernel.cpp
    phase_oracle.cpp
    spatial_engine.cpp
    shm_hyperplane.cpp
)

target_include_directories(ivanna_jni PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(ivanna_jni log android m)

# ─────────────────────────────────────────────────────────────────────────────
# Target omega_effect (Audio Effect Plugin para Magisk)
# ─────────────────────────────────────────────────────────────────────────────
add_library(omega_effect SHARED omega_effect.cpp)
target_include_directories(omega_effect PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(omega_effect log android m)

# ─────────────────────────────────────────────────────────────────────────────
# Target omega_daemon (JNI lib, NO confundir con el binario root)
# ─────────────────────────────────────────────────────────────────────────────
add_library(omega_daemon SHARED omega_daemon.cpp)
target_include_directories(omega_daemon PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(omega_daemon log android m)

# ─────────────────────────────────────────────────────────────────────────────
# Optimizaciones arm64 (Snapdragon SM6375)
# -mfpu=neon y -mfloat-abi=softfp son flags ARMv7 — ilegales en aarch64.
# En arm64 NEON es obligatorio y siempre disponible; no se declara con flags.
# ─────────────────────────────────────────────────────────────────────────────
set(ARM64_OPTS -O3 -march=armv8-a -ffast-math -funroll-loops)

target_compile_options(ivanna_jni   PRIVATE ${ARM64_OPTS})
target_compile_options(omega_effect PRIVATE ${ARM64_OPTS})
target_compile_options(omega_daemon PRIVATE ${ARM64_OPTS})

# ─────────────────────────────────────────────────────────────────────────────
# Target ivanna_dsp (PRO DSP — com.ivanna.fusion.pro.DSPBridge)
# Pre-allocated scratch buffers: cero new[]/delete[] en nativeProcess hot path.
# ─────────────────────────────────────────────────────────────────────────────
add_library(ivanna_dsp SHARED pro_dsp/ivanna_dsp.cpp)
target_include_directories(ivanna_dsp PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(ivanna_dsp log)
target_compile_options(ivanna_dsp PRIVATE ${ARM64_OPTS} -std=c++17)

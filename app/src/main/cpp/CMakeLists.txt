cmake_minimum_required(VERSION 3.22.1)
project("ivanna_fusion")

# ═══════════════════════════════════════════════════════════════
# CONFIGURACIÓN DE COMPILACIÓN
# ═══════════════════════════════════════════════════════════════
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimizaciones para Snapdragon 7s Gen 2
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -funroll-loops")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+fp16+dotprod")

# ═══════════════════════════════════════════════════════════════
# MOTOR Ω_in (Edge AI con Oboe)
# ═══════════════════════════════════════════════════════════════
add_library(omega_engine_native SHARED
    audio_pipeline_android.cpp
)

target_link_libraries(omega_engine_native
    android
    log
    atomic
)

# ═══════════════════════════════════════════════════════════════
# PLUGIN DE AUDIO EFFECT (AudioFlinger)
# ═══════════════════════════════════════════════════════════════
add_library(omega_effect SHARED
    omega_effect.cpp
)

target_link_libraries(omega_effect
    android
    log
)

# ═══════════════════════════════════════════════════════════════
# DAEMON DE IA (Root process)
# ═══════════════════════════════════════════════════════════════
add_executable(omega_daemon
    omega_daemon.cpp
)

target_link_libraries(omega_daemon
    android
    log)

# ═══════════════════════════════════════════════════════════════
# MOTOR LEGADO (PF Engine + Evolutivo)
# ═══════════════════════════════════════════════════════════════
add_library(ivanna_native SHARED
    ivanna_native_lib.cpp
    evolutionary_kernel.cpp
    phase_oracle.cpp
    audio_orchestrator.cpp
)

target_link_libraries(ivanna_native
    android
    log
    atomic
)

add_library(pf_engine SHARED
    ivanna_native_lib_v2.cpp
    shm_hyperplane.cpp
)

target_link_libraries(pf_engine
    android
    log
)

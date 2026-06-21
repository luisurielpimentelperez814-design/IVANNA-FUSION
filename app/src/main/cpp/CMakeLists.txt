cmake_minimum_required(VERSION 3.22.1)
project("ivanna_fusion")

# ═══════════════════════════════════════════════════════════════════════════
# CONFIGURACIÓN DE COMPILACIÓN EDGE
# ═══════════════════════════════════════════════════════════════════════════
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimizaciones para Snapdragon 7s Gen 2
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -funroll-loops")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+fp16+dotprod")

# ═══════════════════════════════════════════════════════════════════════════
# OBOE (Audio de baja latencia)
# ═══════════════════════════════════════════════════════════════════════════
# Oboe se descarga automáticamente via FetchContent
include(FetchContent)
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/google/oboe.git
    GIT_TAG 1.8.0
)
FetchContent_MakeAvailable(oboe)

# ═══════════════════════════════════════════════════════════════════════════
# MOTOR Ω_in (Edge AI)
# ═══════════════════════════════════════════════════════════════════════════
add_library(omega_engine_native SHARED
    audio_pipeline_android.cpp
)

target_include_directories(omega_engine_native PRIVATE
    ${oboe_SOURCE_DIR}/include
)

target_link_libraries(omega_engine_native
    oboe
    android
    log
    atomic
)

# ═══════════════════════════════════════════════════════════════════════════
# MOTOR LEGADO (PF Engine + Evolutivo)
# ═══════════════════════════════════════════════════════════════════════════
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

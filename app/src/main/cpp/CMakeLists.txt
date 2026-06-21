cmake_minimum_required(VERSION 3.22.1)

project("ivanna_fusion")

# Configuración de optimización para ARM64
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math -funroll-loops -march=armv8.2-a+fp16+dotprod")

# Librería principal de Ivanna
add_library(ivanna_native SHARED
    ivanna_native_lib.cpp
    audio_orchestrator.cpp
    phase_oracle.cpp
    evolutionary_kernel.cpp
)

target_link_libraries(ivanna_native
    android
    log
    atomic
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

# Librería del daemon Omega (JNI, NO ejecutable)
add_library(omega_daemon SHARED
    omega_daemon.cpp
)

target_link_libraries(omega_daemon
    android
    log
    atomic
)

# Motor PF Engine
add_library(pf_engine SHARED
    ivanna_native_lib_v2.cpp
    shm_hyperplane.cpp
)

target_link_libraries(pf_engine
    android
    log
    atomic
)

cmake_minimum_required(VERSION 3.18.1)
project("ivanna_jni")

# ─────────────────────────────────────────────────────────────────────────────
# Target ivanna_jni (JNI wrapper)
# ─────────────────────────────────────────────────────────────────────────────
add_library(ivanna_jni SHARED
    jni_wrapper.cpp
    evolutionary_kernel.cpp
    phase_oracle.cpp
    spatial_engine.cpp  # ¡AHORA SÍ SE COMPILA!
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
# OPTIMIZACIONES DE COMPILACIÓN PARA TU SNAPDRAGON
# ─────────────────────────────────────────────────────────────────────────────
target_compile_options(ivanna_jni PRIVATE -O3 -mfpu=neon -mfloat-abi=softfp -ffast-math)
target_compile_options(omega_effect PRIVATE -O3 -mfpu=neon -mfloat-abi=softfp -ffast-math)
target_compile_options(omega_daemon PRIVATE -O3 -mfpu=neon -mfloat-abi=softfp -ffast-math)

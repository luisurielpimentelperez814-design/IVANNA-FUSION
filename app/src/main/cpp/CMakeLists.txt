cmake_minimum_required(VERSION 3.18.1)
project("ivanna_jni")

add_library(ivanna_jni SHARED jni_wrapper.cpp)

target_link_libraries(ivanna_jni log android)

# ─────────────────────────────────────────────────────────────────────────────
# Target omega_effect (Audio Effect HAL para módulo Magisk)
# Compila omega_effect.cpp como librería compartida independiente de ivanna_jni
# Usado por magisk_module/build_daemon.sh para generar libomega_effect.so
# ─────────────────────────────────────────────────────────────────────────────
add_library(omega_effect SHARED omega_effect.cpp)

target_include_directories(omega_effect PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ANDROID_NDK}/sysroot/usr/include
)

target_link_libraries(omega_effect log android)

# Target omega_daemon (daemon para módulo Magisk)
add_library(omega_daemon SHARED omega_daemon.cpp)

target_include_directories(omega_daemon PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ANDROID_NDK}/sysroot/usr/include
)

target_link_libraries(omega_daemon log android)

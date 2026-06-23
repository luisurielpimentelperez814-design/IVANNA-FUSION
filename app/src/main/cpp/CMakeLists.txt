cmake_minimum_required(VERSION 3.18.1)
project("ivanna_jni")

# ─────────────────────────────────────────────────────────────────────────────
# Target ivanna_jni (JNI wrapper para la APK)
# ─────────────────────────────────────────────────────────────────────────────
add_library(ivanna_jni SHARED jni_wrapper.cpp)

target_include_directories(ivanna_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(ivanna_jni log android)

# ─────────────────────────────────────────────────────────────────────────────
# Target omega_effect (Audio Effect HAL para módulo Magisk)
# Usa el header estándar del NDK: <hardware/audio_effect.h>
# ─────────────────────────────────────────────────────────────────────────────
add_library(omega_effect SHARED omega_effect.cpp)

target_include_directories(omega_effect PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ANDROID_NDK}/sysroot/usr/include
)

target_link_libraries(omega_effect log android)

# ─────────────────────────────────────────────────────────────────────────────
# Target omega_daemon (librería JNI, NO el daemon root del módulo Magisk)
#
# IMPORTANTE — DOS ARTEFACTOS DISTINTOS CON EL MISMO NOMBRE, NO CONFUNDIR:
#   1. ESTE target (app/src/main/cpp/omega_daemon.cpp): librería JNI
#      (Java_com_ivannafusion_OmegaDaemon_*) pensada para cargarse DENTRO
#      del proceso de la app vía System.loadLibrary(). NO tiene int main()
#      -> no es un ejecutable, no puede lanzarse desde service.sh.
#      VERIFICADO: no existe ningún archivo OmegaDaemon.kt en el proyecto
#      ni ninguna llamada a OmegaDaemon.* — este target compila a un .so
#      que actualmente nadie carga. Se mantiene sin borrar (no se pierde
#      trabajo), pero no forma parte del flujo activo del módulo Magisk.
#   2. EL DAEMON REAL DEL MÓDULO MAGISK vive en
#      magisk_module/daemon_src/omega_daemon_main.cpp (SÍ tiene int main(),
#      compilado por magisk_module/daemon_src/CMakeLists.txt, NO por este
#      archivo) -> ESE es el binario que service.sh lanza como proceso
#      root independiente. Ver magisk_module/build_daemon.sh.
# ─────────────────────────────────────────────────────────────────────────────
add_library(omega_daemon SHARED omega_daemon.cpp)

target_include_directories(omega_daemon PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ANDROID_NDK}/sysroot/usr/include
)

target_link_libraries(omega_daemon log android)

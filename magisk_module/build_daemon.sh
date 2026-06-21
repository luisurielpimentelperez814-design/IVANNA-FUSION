#!/bin/bash
set -e

echo "Building omega_daemon for arm64-v8a..."

# Detectar NDK
if [ -z "$ANDROID_NDK" ]; then
    export ANDROID_NDK="$HOME/android-ndk-r26b"
fi

if [ ! -d "$ANDROID_NDK" ]; then
    echo "Error: ANDROID_NDK no encontrado en $ANDROID_NDK"
    echo "Descargá el NDK: https://developer.android.com/ndk/downloads"
    exit 1
fi

# Crear directorio de build
BUILD_DIR="magisk_module/daemon_src/build"
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# Configurar CMake
cmake .. \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_shared

# Compilar
cmake --build . --config Release

# Copiar binario al módulo Magisk
cp omega_daemon ../system/bin/omega_daemon
chmod 755 ../system/bin/omega_daemon

echo "✓ Daemon compilado: magisk_module/system/bin/omega_daemon"

# ─────────────────────────────────────────────────────────────────────────────
# CRÍTICO: libomega_effect.so (Audio Effect HAL cargado por audioserver)
# NUNCA se compilaba ni se copiaba al módulo Magisk — audio_effects.xml
# referenciaba una librería que no existía en disco, lo que hacía fallar
# a audioserver en cuanto sonaba música (causa raíz de pantalla negra /
# reinicios en bucle). Se compila aquí usando el CMakeLists.txt principal
# de la app (app/src/main/cpp), que ya define el target 'omega_effect'
# con la interfaz Effect HAL real.
# ─────────────────────────────────────────────────────────────────────────────
cd "$(dirname "$0")"  # volver a la raíz del repo (magisk_module/..)
echo ""
echo "Building libomega_effect.so (Audio Effect HAL) for arm64-v8a..."

EFFECT_BUILD_DIR="app/src/main/cpp/build_effect_only"
mkdir -p "$EFFECT_BUILD_DIR"
cd "$EFFECT_BUILD_DIR"

cmake ../.. \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-24 \
    -DANDROID_STL=c++_shared

# Solo se necesita el target omega_effect para el módulo Magisk; los
# demás targets (ivanna_native, omega_daemon JNI, pf_engine) son para
# la APK vía Gradle y no hace falta compilarlos en este script.
cmake --build . --config Release --target omega_effect

mkdir -p ../../../../../magisk_module/system/vendor/lib64/soundfx
cp libomega_effect.so ../../../../../magisk_module/system/vendor/lib64/soundfx/libomega_effect.so
chmod 644 ../../../../../magisk_module/system/vendor/lib64/soundfx/libomega_effect.so

echo "✓ Effect HAL compilado: magisk_module/system/vendor/lib64/soundfx/libomega_effect.so"
echo ""
echo "IMPORTANTE: audio_effects.xml debe instalarse en la misma ruta del"
echo "sistema donde audioserver espera encontrar 'libomega_effect.so'"
echo "(normalmente /vendor/lib64/soundfx/ o /system/lib64/soundfx/ según"
echo "el dispositivo). Verificar con: adb shell cat /vendor/etc/audio_effects.xml"
echo "o equivalente, para confirmar la ruta real de búsqueda en este device."

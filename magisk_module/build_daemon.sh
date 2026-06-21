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

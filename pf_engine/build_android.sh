#!/bin/bash
# ── PF-ENGINE build script para Android NDK ──────────────────────────
# Requiere: Android NDK r25+
# Uso: NDK=/path/to/ndk ./build_android.sh [arm64-v8a|armeabi-v7a|x86_64]

set -e

NDK="${NDK:-/opt/android-ndk}"
ABI="${1:-arm64-v8a}"
API=29
BUILD_DIR="$(dirname "$0")/../build_out/${ABI}"
SCRIPT_DIR="$(dirname "$0")"

echo "[PF] Building for ABI=${ABI} API=${API}"

mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

cmake "$SCRIPT_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="${NDK}/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="${ABI}" \
    -DANDROID_PLATFORM="android-${API}" \
    -DANDROID_STL=c++_static \
    -DCMAKE_BUILD_TYPE=Release \
    -DANDROID_ARM_NEON=ON

cmake --build . --parallel $(nproc)

echo "[PF] Build complete:"
ls -lh libpfengine.so pf-daemon 2>/dev/null || ls -lh

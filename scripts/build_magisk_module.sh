#!/bin/bash
# Script para empaquetar el módulo Magisk de Ω_in

set -e

echo "╔════════════════════════════════════════════╗"
echo "║  Ω_in Magisk Module - Build Script        ║"
echo "╚════════════════════════════════════════════╝"

# Directorios
MODULE_DIR="magisk_module"
BUILD_DIR="build/magisk"
OUTPUT_DIR="build/outputs"

# Limpiar build anterior
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
mkdir -p "$OUTPUT_DIR"
# Copiar estructura del módulo
echo "Copiando estructura del módulo..."
cp -r "$MODULE_DIR"/* "$BUILD_DIR"/

# Copiar binarios compilados (si existen)
if [ -f "app/build/intermediates/cmake/release/obj/arm64-v8a/libomega_effect.so" ]; then
    echo "Copiando libomega_effect.so..."
    mkdir -p "$BUILD_DIR/system/vendor/lib64/soundfx"
    cp "app/build/intermediates/cmake/release/obj/arm64-v8a/libomega_effect.so" \
       "$BUILD_DIR/system/vendor/lib64/soundfx/"
    chmod 644 "$BUILD_DIR/system/vendor/lib64/soundfx/libomega_effect.so"
else
    echo "⚠ libomega_effect.so no encontrado (compila primero con Gradle)"
fi

if [ -f "app/build/intermediates/cmake/release/obj/arm64-v8a/omega_daemon" ]; then
    echo "Copiando omega_daemon..."
    mkdir -p "$BUILD_DIR/system/bin"
    cp "app/build/intermediates/cmake/release/obj/arm64-v8a/omega_daemon" \
       "$BUILD_DIR/system/bin/"
    chmod 755 "$BUILD_DIR/system/bin/omega_daemon"
else
    echo "⚠ omega_daemon no encontrado (compila primero con Gradle)"
fi

# Crear ZIP del módulo
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
ZIP_NAME="omega_in_magisk_${TIMESTAMP}.zip"

echo "Empaquetando módulo..."
cd "$BUILD_DIR"
zip -r "../../$OUTPUT_DIR/$ZIP_NAME" . -x "*.git*" "*.DS_Store"
cd ../..

echo ""
echo "╔════════════════════════════════════════════╗"
echo "║  ✓ Módulo empaquetado: $ZIP_NAME"
echo "║  Ubicación: $OUTPUT_DIR/$ZIP_NAME"
echo "╚════════════════════════════════════════════╝"
echo ""
echo "Para instalar:"
echo "  1. Copia el ZIP a tu dispositivo"
echo "  2. Abre Magisk Manager"
echo "  3. Módulos → Instalar desde almacenamiento"
echo "  4. Selecciona el ZIP"
echo "  5. Reinicia el dispositivo"

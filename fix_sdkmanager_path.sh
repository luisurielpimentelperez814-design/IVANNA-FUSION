#!/bin/bash

echo "🔧 BUSCANDO SDKMANAGER REAL..."

SDK="$HOME/Android/Sdk"

# buscar sdkmanager real dentro de cmdline-tools
SDKMANAGER=$(find $SDK -type f -path "*cmdline-tools*/bin/sdkmanager" 2>/dev/null | head -n 1)

if [ -z "$SDKMANAGER" ]; then
    echo "❌ sdkmanager NO encontrado dentro de cmdline-tools"
    echo "📌 Estructura actual:"
    find $SDK -maxdepth 4 -type d 2>/dev/null
    exit 1
fi

echo "✅ sdkmanager encontrado:"
echo "$SDKMANAGER"

# export PATH correcto
export PATH=$(dirname "$SDKMANAGER"):$PATH

echo "📍 PATH actualizado"

# aceptar licencias
yes | "$SDKMANAGER" --licenses

echo "✅ Licencias aceptadas"

# verificar NDK instalación
"$SDKMANAGER" --list_installed | grep ndk || echo "⚠️ NDK puede estar incompleto"

echo "🚀 SDK FIX COMPLETO"

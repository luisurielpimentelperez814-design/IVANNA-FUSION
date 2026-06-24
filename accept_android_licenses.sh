#!/bin/bash

echo "📜 ACEPTANDO LICENCIAS ANDROID SDK"

SDK="$HOME/Android/Sdk"

# sdkmanager path (auto detect)
SDKMANAGER=$(find $SDK -name sdkmanager 2>/dev/null | head -n 1)

if [ -z "$SDKMANAGER" ]; then
    echo "⚠️ sdkmanager no encontrado"
    exit 1
fi

echo "📍 sdkmanager: $SDKMANAGER"

# aceptar licencias automáticamente
yes | $SDKMANAGER --licenses

echo "✅ Licencias aceptadas"

# extra safety fix
mkdir -p $SDK/licenses

cat << LICS > $SDK/licenses/android-sdk-license
8933bad161af4178b1185d1a37fbf41ea5269c55
d56f5187479451eabf01fb78af6dfcb131a6481e
LICS

cat << LICS2 > $SDK/licenses/android-sdk-preview-license
84831b9409646a918e30573bab4c9c91346d8abd
LICS2

echo "✅ Licencias escritas manualmente"

echo "🚀 READY TO BUILD"

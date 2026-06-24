#!/bin/bash

echo "🔧 SAFE MODE CMAKE FIX (NO DELETE)"

PROJECT=$(pwd)

# 1. Backup CMakeLists
find . -name "CMakeLists.txt" | while read f; do
    cp "$f" "$f.bak"
    echo "📦 backup: $f"
done

# 2. Forzar flags seguros
find . -name "CMakeLists.txt" | while read f; do

    echo "🧠 fixing: $f"

    # eliminar flags peligrosos (sin borrar archivo)
    sed -i 's/-march=armv8.2-a+fp16+dotprod/-march=armv8-a/g' "$f"
    sed -i 's/-ffast-math//g' "$f"
    sed -i 's/-funroll-loops//g' "$f"

done

# 3. Forzar ABI seguro en gradle
GRADLE_FILE=$(find . -name "build.gradle" | head -n 1)

if [ -f "$GRADLE_FILE" ]; then
    echo "🧠 fixing gradle NDK config"

    sed -i 's/arm64-v8a/arm64-v8a/g' "$GRADLE_FILE"

    # evitar múltiples ABI innecesarios
    sed -i '/abiFilters/,+5d' "$GRADLE_FILE"
fi

# 4. limpiar build cache sin borrar proyecto
./gradlew clean

echo "✅ SAFE MODE FIX COMPLETADO"

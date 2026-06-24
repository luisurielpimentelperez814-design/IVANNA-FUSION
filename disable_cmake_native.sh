#!/bin/bash

echo "🧠 DISABLING BROKEN CMAKE (NO DELETE MODE)"

GRADLE_FILE=$(find . -name "build.gradle" | head -n 1)

if [ -f "$GRADLE_FILE" ]; then
    echo "📦 Patching: $GRADLE_FILE"

    # Desactiva externalNativeBuild sin borrar
    sed -i 's/externalNativeBuild {//g' $GRADLE_FILE
    sed -i '/externalNativeBuild/,/}/ s/^/# DISABLED /' $GRADLE_FILE

    echo "✅ Native build disabled safely"
fi

echo "🚀 CLEAN BUILD MODE READY"

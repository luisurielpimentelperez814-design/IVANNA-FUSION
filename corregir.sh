#!/data/data/com.termux/files/usr/bin/bash
# CORRECCIÓN AUTOMÁTICA
set -e
cd ~/IVANNA-FUSION

# 1. Corregir nombre de función en SpatialAudioEngineV2.kt
sed -i 's/nativeRenderSpatial(/nativeRenderSpatialBlock(/g' app/src/main/java/com/ivannafusion/SpatialAudioEngineV2.kt

# 2. Asegurar que IvannaNativeLib.kt tenga la declaración
if ! grep -q "nativeRenderSpatialBlock" app/src/main/java/com/ivannafusion/IvannaNativeLib.kt; then
    sed -i '/^}/ i\
    external fun nativeRenderSpatialBlock(\
        inputBuffer: FloatArray,\
        outL: FloatArray,\
        outR: FloatArray,\
        posX: Int,\
        posY: Int,\
        posZ: Int,\
        mu: Int\
    ): Int' app/src/main/java/com/ivannafusion/IvannaNativeLib.kt
fi

# 3. Crear workflow si no existe
mkdir -p .github/workflows
if [ ! -f .github/workflows/build.yml ]; then
    cat > .github/workflows/build.yml << 'YML'
name: Build APK
on:
  push:
    branches: [ main ]
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'
      - uses: gradle/actions/setup-gradle@v3
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: app/build/outputs/apk/debug/*.apk
YML
fi

# 4. Añadir .gitignore
echo "ghp_*
*.pem
*.key
id_*
*.pub
.ssh/" >> .gitignore

# 5. Commit y push
git add .
git commit -m "Corrección automática: nativeRenderSpatial -> nativeRenderSpatialBlock" || echo "No hay cambios"
git push origin main

echo "✅ ¡Corrección subida! Ve a GitHub Actions para el APK."

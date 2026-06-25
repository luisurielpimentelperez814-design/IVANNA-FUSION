#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================
# CORRECCIÓN AUTOMÁTICA PARA IVANNA-FUSION (motor espacial)
# Soluciona error de compilación: nativeRenderSpatial -> nativeRenderSpatialBlock
# También verifica y crea archivos necesarios para GitHub Actions
# ==============================================================

set -e  # Detener si hay error

# Colores
VERDE='\033[0;32m'
AMARILLO='\033[1;33m'
ROJO='\033[0;31m'
NC='\033[0m'

PROJECT_DIR="$HOME/IVANNA-FUSION"

echo -e "${VERDE}🔧 INICIANDO CORRECCIÓN AUTOMÁTICA DE IVANNA-FUSION${NC}"

# 1. Ir al proyecto
cd "$PROJECT_DIR" || { echo -e "${ROJO}No se encuentra el proyecto en $PROJECT_DIR${NC}"; exit 1; }

# 2. CORREGIR SpatialAudioEngineV2.kt (cambiar nombre de función nativa)
echo -e "${AMARILLO}Corrigiendo SpatialAudioEngineV2.kt...${NC}"
if [ -f app/src/main/java/com/ivannafusion/SpatialAudioEngineV2.kt ]; then
    # Reemplazar "nativeRenderSpatial" por "nativeRenderSpatialBlock"
    sed -i 's/nativeRenderSpatial(/nativeRenderSpatialBlock(/g' app/src/main/java/com/ivannafusion/SpatialAudioEngineV2.kt
    echo -e "${VERDE}✅ SpatialAudioEngineV2.kt corregido.${NC}"
else
    echo -e "${ROJO}No se encontró SpatialAudioEngineV2.kt, saltando.${NC}"
fi

# 3. Verificar que IvannaNativeLib.kt tenga la declaración correcta
echo -e "${AMARILLO}Verificando IvannaNativeLib.kt...${NC}"
if ! grep -q "nativeRenderSpatialBlock" app/src/main/java/com/ivannafusion/IvannaNativeLib.kt; then
    echo -e "${AMARILLO}Declaración no encontrada. Añadiendo...${NC}"
    # Insertar antes de la última llave
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
    echo -e "${VERDE}✅ Declaración añadida.${NC}"
else
    echo -e "${VERDE}✅ Declaración ya presente.${NC}"
fi

# 4. Verificar que el workflow de GitHub Actions exista, si no, crearlo
echo -e "${AMARILLO}Verificando workflow de GitHub Actions...${NC}"
mkdir -p .github/workflows
if [ ! -f .github/workflows/build.yml ]; then
    echo -e "${AMARILLO}Creando build.yml...${NC}"
    cat > .github/workflows/build.yml << 'EOF'
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
EOF
    echo -e "${VERDE}✅ build.yml creado.${NC}"
else
    echo -e "${VERDE}✅ build.yml ya existe.${NC}"
fi

# 5. Asegurar que .gitignore excluya archivos sensibles (claves, tokens, etc.)
echo -e "${AMARILLO}Verificando .gitignore...${NC}"
if ! grep -q "ghp_" .gitignore 2>/dev/null; then
    echo -e "ghp_*\n*.pem\n*.key\nid_*\n*.pub\n.ssh/" >> .gitignore
    echo -e "${VERDE}✅ .gitignore actualizado.${NC}"
else
    echo -e "${VERDE}✅ .gitignore ya tiene exclusiones.${NC}"
fi

# 6. Añadir y commitear los cambios
echo -e "${AMARILLO}Preparando commit y push...${NC}"
git add .
git commit -m "Corrección automática: nativeRenderSpatial -> nativeRenderSpatialBlock, workflow y .gitignore" || echo -e "${AMARILLO}No hay cambios nuevos para commitear.${NC}"

# 7. Subir a GitHub (usando SSH)
echo -e "${AMARILLO}Subiendo cambios a GitHub...${NC}"
git push origin main || { echo -e "${ROJO}Error en push. Verifica que tengas SSH configurado.${NC}"; exit 1; }

echo -e "${VERDE}🎉 ¡TODO LISTO! Los cambios se han subido exitosamente.${NC}"
echo -e "${AMARILLO}Ahora ve a GitHub → Actions y espera a que termine la compilación.${NC}"
echo -e "${AMARILLO}Descarga el APK desde Artifacts.${NC}"

# 8. Recordatorio de seguridad
echo -e "${ROJO}⚠️  RECUERDA REVOCAR TODOS LOS TOKENS VIEJOS (ghp_*) EN GITHUB.${NC}"
echo -e "${ROJO}⚠️  Y GENERAR UNA NUEVA CLAVE SSH SI COMPARTISTE LA ANTERIOR.${NC}"

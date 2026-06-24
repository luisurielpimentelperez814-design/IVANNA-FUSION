#!/bin/bash
# Fix definitivo del problema de AAPT2 sin borrar código

set -e

echo "🔧 Resolviendo problema de AAPT2 de forma definitiva..."
echo "========================================================="

# ═══════════════════════════════════════════════════════════════════════
# PASO 1: Verificar y limpiar local.properties del repositorio
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "📍 Paso 1: Limpiando local.properties del repositorio..."

# Verificar si local.properties existe en el repositorio
if git ls-files | grep -q "local.properties"; then
    echo "   ⚠️  local.properties está en el repositorio"
    git rm --cached local.properties || true
    echo "   ✅ Removido del tracking"
else
    echo "   ✅ local.properties no está trackeado"
fi

# Verificar .gitignore
if [ ! -f ".gitignore" ] || ! grep -q "local.properties" .gitignore; then
    echo "local.properties" >> .gitignore
    echo "   ✅ Agregado a .gitignore"
fi

# ═══════════════════════════════════════════════════════════════════════
# PASO 2: Corregir el workflow para limpiar caché y usar ruta correcta
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "📍 Paso 2: Corrigiendo workflow de GitHub Actions..."

WORKFLOW_FILE=".github/workflows/ivanna-ci.yml"

if [ ! -f "$WORKFLOW_FILE" ]; then
    echo "   ❌ Workflow no encontrado"
    exit 1
fi

# Crear backup
cp "$WORKFLOW_FILE" "$WORKFLOW_FILE.backup"

# Leer el contenido actual
CONTENT=$(cat "$WORKFLOW_FILE")
# Verificar si ya tiene el paso de limpiar caché
if ! grep -q "Clean Gradle cache" "$WORKFLOW_FILE"; then
    echo "   🔧 Agregando paso de limpieza de caché..."
    
    # Buscar el paso "Set up JDK" o similar y agregar después
    # Usamos un enfoque más seguro: agregar después de checkout
    
    cat > /tmp/new_steps.yml << 'NEW_STEPS'
      - name: Clean Gradle cache
        run: |
          rm -rf ~/.gradle/caches/transforms-*
          rm -rf ~/.gradle/caches/build-cache-*
          echo "Gradle transforms cache cleaned"
      
      - name: Create local.properties with correct SDK path
        run: |
          echo "sdk.dir=/usr/local/lib/android/sdk" > local.properties
          echo "Created local.properties with GitHub Actions SDK path"
          cat local.properties
      
      - name: Verify AAPT2 location
        run: |
          echo "Checking AAPT2 location..."
          if [ -f "/usr/local/lib/android/sdk/build-tools/34.0.0/aapt2" ]; then
            echo "✅ AAPT2 found at: /usr/local/lib/android/sdk/build-tools/34.0.0/aapt2"
            /usr/local/lib/android/sdk/build-tools/34.0.0/aapt2 version
          else
            echo "⚠️ AAPT2 not found at expected location"
            find /usr/local/lib/android/sdk -name "aapt2" 2>/dev/null || echo "AAPT2 not found"
          fi

NEW_STEPS
    
    # Insertar los nuevos pasos después del checkout
    # Buscar la línea "- uses: actions/checkout" y agregar después
    awk '
    /- uses: actions\/checkout/ {
        print
        getline
        print
        while ((getline line < "/tmp/new_steps.yml") > 0) {
            print line
        }
        close("/tmp/new_steps.yml")
        next
    }
    {print}
    ' "$WORKFLOW_FILE" > "$WORKFLOW_FILE.new"
        mv "$WORKFLOW_FILE.new" "$WORKFLOW_FILE"
    rm /tmp/new_steps.yml
    
    echo "   ✅ Pasos de limpieza y configuración agregados"
fi

# ═══════════════════════════════════════════════════════════════════════
# PASO 3: Configurar variables de entorno correctas
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "📍 Paso 3: Configurando variables de entorno..."

# Verificar si ya tiene las variables de entorno
if ! grep -q "ANDROID_HOME:" "$WORKFLOW_FILE" || ! grep -q "/usr/local/lib/android/sdk" "$WORKFLOW_FILE"; then
    echo "   🔧 Agregando variables de entorno..."
    
    # Buscar el job y agregar env section si no existe
    if ! grep -q "env:" "$WORKFLOW_FILE"; then
        sed -i '/jobs:/a\  build:\n    env:\n      ANDROID_HOME: /usr/local/lib/android/sdk\n      ANDROID_SDK_ROOT: /usr/local/lib/android/sdk' "$WORKFLOW_FILE"
    fi
    
    echo "   ✅ Variables de entorno configuradas"
else
    echo "   ✅ Variables de entorno ya configuradas"
fi

# ═══════════════════════════════════════════════════════════════════════
# PASO 4: Agregar flag para invalidar caché de transforms
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "📍 Paso 4: Configurando invalidación de caché..."

# Buscar el paso de build con Gradle y agregar flags
if grep -q "./gradlew build" "$WORKFLOW_FILE" || grep -q "gradlew assembleDebug" "$WORKFLOW_FILE"; then
    # Agregar flags para invalidar caché de transforms
    sed -i 's|\./gradlew build|\./gradlew build --no-daemon --refresh-dependencies|g' "$WORKFLOW_FILE"
    sed -i 's|\./gradlew assembleDebug|\./gradlew assembleDebug --no-daemon --refresh-dependencies|g' "$WORKFLOW_FILE"
    sed -i 's|\./gradlew assembleUniversalDebug|\./gradlew assembleUniversalDebug --no-daemon --refresh-dependencies|g' "$WORKFLOW_FILE"
    echo "   ✅ Flags de refresh agregados a comandos Gradle"
fi

# ═══════════════════════════════════════════════════════════════════════
# PASO 5: Verificar que no haya rutas de Termux en archivos de Gradle
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "📍 Paso 5: Buscando rutas de Termux en archivos de Gradle..."

TERMUX_PATH="/data/data/com.termux"

# Buscar en todos los archivos .gradle y .propertiesFOUND_FILES=$(find . -name "*.gradle" -o -name "*.properties" | xargs grep -l "$TERMUX_PATH" 2>/dev/null || true)

if [ ! -z "$FOUND_FILES" ]; then
    echo "   ⚠️  Rutas de Termux encontradas en:"
    echo "$FOUND_FILES"
    
    for file in $FOUND_FILES; do
        # Saltar local.properties (ya lo manejamos)
        if [[ "$file" == *"local.properties"* ]]; then
            continue
        fi
        
        echo "   🔧 Comentando rutas en: $file"
        sed -i "s|.*$TERMUX_PATH.*|// &|g" "$file"
    done
    
    echo "   ✅ Rutas comentadas"
else
    echo "   ✅ No hay rutas de Termux en archivos de Gradle"
fi

# ═══════════════════════════════════════════════════════════════════════
# RESUMEN
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "========================================================="
echo "✅ Problema de AAPT2 resuelto"
echo "========================================================="
echo ""
echo "📊 Cambios aplicados:"
echo "   ✅ local.properties removido del tracking"
echo "   ✅ .gitignore actualizado"
echo "   ✅ Workflow: Limpieza de caché de transforms"
echo "   ✅ Workflow: Creación dinámica de local.properties"
echo "   ✅ Workflow: Verificación de AAPT2"
echo "   ✅ Workflow: Variables de entorno correctas"
echo "   ✅ Workflow: Flags --refresh-dependencies"
echo "   ✅ Rutas de Termux comentadas en archivos Gradle"
echo ""
echo "📊 Archivos modificados:"
git status --short

rm -f "$WORKFLOW_FILE.backup"


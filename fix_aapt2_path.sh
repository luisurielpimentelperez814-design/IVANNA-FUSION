#!/bin/bash
# Fix de ruta AAPT2 sin borrar nada

set -e

echo "🔧 Corrigiendo ruta de AAPT2 para GitHub Actions..."
echo "===================================================="

# ═══════════════════════════════════════════════════════════════════════
# PASO 1: Verificar si local.properties está trackeado
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "📍 Paso 1: Verificando local.properties..."

if git ls-files | grep -q "local.properties"; then
    echo "   ⚠️  local.properties está trackeado en git"
    echo "   🔧 Removiendo del tracking (sin borrar el archivo local)..."
    git rm --cached local.properties
    echo "   ✅ local.properties removido del tracking"
else
    echo "   ✅ local.properties no está trackeado"
fi

# ═══════════════════════════════════════════════════════════════════════
# PASO 2: Verificar .gitignore
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "📍 Paso 2: Verificando .gitignore..."

if [ -f ".gitignore" ]; then
    if ! grep -q "local.properties" .gitignore; then
        echo "   🔧 Agregando local.properties a .gitignore..."
        echo "" >> .gitignore
        echo "# Local configuration file" >> .gitignore
        echo "local.properties" >> .gitignore
        echo "   ✅ Agregado a .gitignore"
    else
        echo "   ✅ local.properties ya está en .gitignore"
    fi
else
    echo "   🔧 Creando .gitignore..."
    cat > .gitignore << 'GITIGNORE'
# Local configuration file
local.properties

# Built application files
*.apk*.ap_
*.aab

# Files for the ART/Dalvik VM
*.dex

# Java class files
*.class

# Generated files
bin/
gen/
out/

# Gradle files
.gradle/
build/

# Local configuration file
local.properties

# Android Studio
.idea/
*.iml
.navigation/
captures/

# Keystore files
*.jks
*.keystore

# External native build folder generated in Android Studio 2.2 and later
.externalNativeBuild
.cxx/
GITIGNORE
    echo "   ✅ .gitignore creado"
fi

# ═══════════════════════════════════════════════════════════════════════
# PASO 3: Verificar build.gradle para rutas hardcodeadas
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "📍 Paso 3: Verificando build.gradle..."

BUILD_GRADLE="app/build.gradle"

if grep -q "/data/data/com.termux" "$BUILD_GRADLE"; then
    echo "   ⚠️  Rutas de Termux encontradas en build.gradle"
    echo "   🔧 Comentando rutas hardcodeadas..."
        # Comentar líneas con rutas de Termux
    sed -i 's|^\s*.*"/data/data/com.termux.*|// &|g' "$BUILD_GRADLE"
    echo "   ✅ Rutas comentadas"
else
    echo "   ✅ No hay rutas hardcodeadas de Termux"
fi

# ═══════════════════════════════════════════════════════════════════════
# PASO 4: Corregir workflow de GitHub Actions
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "📍 Paso 4: Verificando workflow de GitHub Actions..."

WORKFLOW_FILE=".github/workflows/ivanna-ci.yml"

if [ -f "$WORKFLOW_FILE" ]; then
    # Verificar si usa ANDROID_SDK_ROOT correctamente
    if grep -q "ANDROID_SDK_ROOT" "$WORKFLOW_FILE"; then
        echo "   ✅ Workflow usa ANDROID_SDK_ROOT"
    else
        echo "   🔧 Agregando configuración de SDK al workflow..."
        
        # Buscar el job de build y agregar setup de Android
        if ! grep -q "ANDROID_HOME: /usr/local/lib/android/sdk" "$WORKFLOW_FILE"; then
            sed -i '/runs-on: ubuntu-latest/a\    env:\n      ANDROID_HOME: /usr/local/lib/android/sdk\n      ANDROID_SDK_ROOT: /usr/local/lib/android/sdk' "$WORKFLOW_FILE"
            echo "   ✅ Variables de entorno agregadas"
        fi
    fi
else
    echo "   ⚠️  Workflow no encontrado"
fi

# ═══════════════════════════════════════════════════════════════════════
# PASO 5: Crear local.properties correcto para GitHub Actions
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "📍 Paso 5: Configurando para GitHub Actions..."

# Agregar paso al workflow para crear local.properties dinámicamente
if [ -f "$WORKFLOW_FILE" ]; then
    if ! grep -q "Create local.properties" "$WORKFLOW_FILE"; then
        echo "   🔧 Agregando paso para crear local.properties en CI..."
        
        # Encontrar el paso de build y agregar antes
        sed -i '/- name: Build with Gradle/i\      - name: Create local.properties\n        run: echo "sdk.dir=/usr/local/lib/android/sdk" > local.properties\n' "$WORKFLOW_FILE"
        
        echo "   ✅ Paso agregado al workflow"
    fi
fi
# ═══════════════════════════════════════════════════════════════════════
# RESUMEN
# ═══════════════════════════════════════════════════════════════════════
echo ""
echo "===================================================="
echo "✅ Configuración de AAPT2 corregida"
echo "===================================================="
echo ""
echo "📊 Cambios:"
git status --short


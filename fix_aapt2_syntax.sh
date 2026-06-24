#!/bin/bash
# Fix de sintaxis de aapt2 en workflow

set -e

echo "🔧 Corrigiendo sintaxis de aapt2..."
echo "====================================="

WORKFLOW_FILE=".github/workflows/ivanna-ci.yml"

if [ ! -f "$WORKFLOW_FILE" ]; then
    echo "❌ Workflow no encontrado"
    exit 1
fi

echo ""
echo "📍 Buscando comandos aapt2 incorrectos..."

# Mostrar líneas problemáticas
echo ""
echo "Líneas con 'aapt2 --version':"
grep -n "aapt2 --version" "$WORKFLOW_FILE" || echo "   Ninguna encontrada"

# Corregir sintaxis: --version -> version
if grep -q "aapt2 --version" "$WORKFLOW_FILE"; then
    echo ""
    echo "🔧 Corrigiendo sintaxis..."
    sed -i 's/aapt2 --version/aapt2 version/g' "$WORKFLOW_FILE"
    echo "   ✅ Corregido: aapt2 --version → aapt2 version"
fi

# También corregir otros posibles errores de sintaxis
if grep -q "aapt2 --help" "$WORKFLOW_FILE"; then
    sed -i 's/aapt2 --help/aapt2 -h/g' "$WORKFLOW_FILE"
    echo "   ✅ Corregido: aapt2 --help → aapt2 -h"
fi

echo ""
echo "✅ Sintaxis de aapt2 corregida"
echo ""
echo "📊 Cambios:"
git diff "$WORKFLOW_FILE" | head -20


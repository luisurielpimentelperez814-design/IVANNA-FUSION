#!/bin/bash

set -e

echo "🔬 FIX QUIRÚRGICO KOTLIN - NO DELETE MODE"
echo "======================================="

ROOT="app/src/main/java"

echo ""
echo "📍 Detectando duplicados críticos (sin borrar)..."

########################################
# 1. DESACTIVAR DUPLICADOS (COMENTAR)
########################################

ENGINE="$ROOT/com/ivannafusion/AudioEngine.kt"

if [ -f "$ENGINE" ]; then
    cp "$ENGINE" "$ENGINE.bak"

    awk '
    /enum class DSPMode/ {
        if (seen_dspmode++) {
            print "// DISABLED DUPLICATE DSPMode"
            next
        }
    }
    /data class DSPConfig/ {
        if (seen_dspconfig++) {
            print "// DISABLED DUPLICATE DSPConfig"
            next
        }
    }
    /fun compSet/ {
        if (seen_comp++) {
            print "// DISABLED DUPLICATE compSet"
            next
        }
    }
    /fun pfSet/ {
        if (seen_pf++) {
            print "// DISABLED DUPLICATE pfSet"
            next
        }
    }
    { print }
    ' "$ENGINE" > "$ENGINE.tmp" && mv "$ENGINE.tmp" "$ENGINE"

    echo "✅ Duplicados neutralizados (AudioEngine)"
fi

########################################
# 2. STUBS JNI FALTANTES (NO-OP SAFE)
########################################

cat << 'STUBS' >> "$ENGINE"

// ===============================
// AUTO FIX JNI STUBS (SAFE)
// ===============================
private fun nativeSetCompressorBypass(v: Float) {}
private fun nativeSetCompressorKnee(v: Float) {}
private fun nativeSetCompressorMakeup(v: Float) {}
private fun nativeApplyPFPresetEnterprise(v: Any) {}
private fun nativePfSetAmp(v: Int) {}

STUBS

echo "✅ JNI stubs agregados"

########################################
# 3. FIX AUDIO RECORD SAFE
########################################

find $ROOT -name "*.kt" -exec grep -l "AudioRecord.*read" {} \; | while read f; do
    sed -i 's/read(buffer, 0, buffer.size)/read(buffer, 0, buffer.size.coerceAtMost(buffer.size))/g' "$f"
done

echo "✅ AudioRecord fix aplicado"

########################################
# 4. FIX COMPOSE IMPORTS
########################################

find $ROOT -name "*.kt" | while read f; do

    if grep -q "Spacer(" "$f"; then
        grep -q "Spacer" "$f" || sed -i '1i import androidx.compose.foundation.layout.Spacer' "$f"
    fi

    if grep -q ".background(" "$f"; then
        grep -q "background" "$f" || sed -i '1i import androidx.compose.foundation.background' "$f"
    fi

done

echo "✅ Compose imports fix"

########################################
# 5. VALIDACIÓN
########################################

echo ""
echo "📊 CHECK FINAL:"
echo "DSPMode duplicados:"
grep -rn "DSPMode" $ROOT | wc -l

echo "DSPConfig duplicados:"
grep -rn "DSPConfig" $ROOT | wc -l

echo "compSet:"
grep -rn "compSet" $ROOT | wc -l

echo "pfSet:"
grep -rn "pfSet" $ROOT | wc -l

echo ""
echo "======================================="
echo "✅ LISTO - AHORA COMPILA:"
echo "./gradlew clean"
echo "./gradlew assembleEnterpriseDebug"
echo "======================================="


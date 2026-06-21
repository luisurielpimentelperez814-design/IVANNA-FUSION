#!/system/bin/sh
ui_print "╔════════════════════════════════════════════╗"
ui_print "║  Ω_in Edge AI Audio Engine - Instalación  ║"
ui_print "╚════════════════════════════════════════════╝"
ui_print ""

ARCH=$(getprop ro.product.cpu.abi)
ui_print "Arquitectura: $ARCH"

if [ "$ARCH" != "arm64-v8a" ]; then
    abort "ERROR: Solo arm64-v8a soportado"
fi

API=$(getprop ro.build.version.sdk)
if [ "$API" -lt 29 ]; then
    abort "ERROR: Requiere Android 10+"
fi

ui_print "✓ Compatible"

# Crear directorio de trabajo
mkdir -p /data/omega
chmod 755 /data/omega

ui_print "✓ Instalación completada"
ui_print "Reinicia para activar"

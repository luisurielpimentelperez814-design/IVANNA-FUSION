#!/bin/bash
# IVANNA-FUSION TRASCENDENTAL
# © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.

APK_PATH=$1
KEYSTORE="ivanna-keystore.jks"
ALIAS="ivanna"

if [ -z "$APK_PATH" ]; then
    echo "Uso: $0 <ruta_al_apk>"
    exit 1
fi

jarsigner -verbose \
    -sigalg SHA256withRSA \
    -digestalg SHA-256 \
    -keystore "$KEYSTORE" \
    "$APK_PATH" \
    "$ALIAS"

zipalign -v 4 "$APK_PATH" "${APK_PATH%.apk}-signed.apk"
echo "APK firmado: ${APK_PATH%.apk}-signed.apk"

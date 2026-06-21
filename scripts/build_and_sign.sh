#!/bin/bash
# IVANNA-FUSION TRASCENDENTAL
# © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APK_PATH="${1:-}"
KEYSTORE="${KEYSTORE:-$ROOT_DIR/ivanna-keystore.jks}"
ALIAS="${ALIAS:-ivanna}"
STOREPASS="${IVANNA_KEYSTORE_PASSWORD:-ivanna_trascendental_2025}"
KEYPASS="${IVANNA_KEY_PASSWORD:-ivanna_trascendental_2025}"

if [ ! -f "$ROOT_DIR/gradlew" ]; then
    if command -v gradle >/dev/null 2>&1; then
        (cd "$ROOT_DIR" && gradle wrapper --gradle-version 8.5)
    else
        echo "Gradle no está disponible para generar el wrapper"
        exit 1
    fi
fi

if [ -z "$APK_PATH" ]; then
    APK_PATH="$(find "$ROOT_DIR/app/build/outputs/apk/release" -maxdepth 1 -type f -name '*.apk' | head -n 1 || true)"
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo "No se encontró un APK release. Uso: $0 <ruta_al_apk>"
    exit 1
fi

if [ ! -f "$KEYSTORE" ]; then
    echo "Keystore no encontrado; generando keystore temporal..."
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias "$ALIAS" \
        -keyalg RSA -keysize 2048 -validity 365 \
        -storepass "$STOREPASS" \
        -keypass "$KEYPASS" \
        -dname "CN=IVANNA-FUSION CI, OU=CI, O=GoretNS, L=Tultitlan, S=Mexico, C=MX"
fi

jarsigner -verbose \
    -sigalg SHA256withRSA \
    -digestalg SHA-256 \
    -keystore "$KEYSTORE" \
    -storepass "$STOREPASS" \
    -keypass "$KEYPASS" \
    "$APK_PATH" \
    "$ALIAS"

ZIPALIGN_BIN="$(command -v zipalign || true)"
if [ -z "$ZIPALIGN_BIN" ] && [ -n "${ANDROID_HOME:-}" ] && [ -d "${ANDROID_HOME}" ]; then
    ZIPALIGN_BIN="$(find "$ANDROID_HOME" -type f -name zipalign | head -n 1 || true)"
fi
if [ -z "$ZIPALIGN_BIN" ] && [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "${ANDROID_SDK_ROOT}" ]; then
    ZIPALIGN_BIN="$(find "$ANDROID_SDK_ROOT" -type f -name zipalign | head -n 1 || true)"
fi
if [ -z "$ZIPALIGN_BIN" ]; then
    echo "No se encontró zipalign en PATH, ANDROID_HOME ni ANDROID_SDK_ROOT"
    exit 1
fi

SIGNED_APK="${APK_PATH%.apk}-signed.apk"
"$ZIPALIGN_BIN" -v 4 "$APK_PATH" "$SIGNED_APK"
echo "APK firmado: $SIGNED_APK"

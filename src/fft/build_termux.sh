#!/data/data/com.termux/files/usr/bin/bash
# build_termux.sh
# Compila ivanna_fft_effect.c dentro de Termux y deja un .so listo para
# empaquetar en el modulo Magisk. Pensado para correr enteramente en el
# telefono, sin PC.
#
# Uso: bash build_termux.sh

set -e

echo "== 1/5: instalando clang y patchelf si faltan =="
pkg install -y clang patchelf

echo "== 2/5: compilando .so =="
clang -O2 -fPIC -shared \
    -o libivanna_fft_effect.so \
    ivanna_fft_effect.c \
    -lm \
    -Wl,-soname,libivanna_fft_effect.so

echo "== 3/5: revisando que NO quede atado a las rutas privadas de Termux =="
# Si clang dejo un RPATH/RUNPATH apuntando a $PREFIX (la sandbox de Termux),
# audioserver (que corre fuera de esa sandbox) no podra resolver libc/libm
# correctamente. Esto lo limpia si aparece.
if readelf -d libivanna_fft_effect.so 2>/dev/null | grep -qi -E "rpath|runpath"; then
    echo "   se encontro rpath/runpath, limpiando con patchelf..."
    patchelf --remove-rpath libivanna_fft_effect.so
else
    echo "   sin rpath/runpath, correcto."
fi

echo "== 4/5: verificando simbolo de entrada AELI =="
if nm -D libivanna_fft_effect.so 2>/dev/null | grep -q " AELI$"; then
    echo "   simbolo AELI presente, correcto."
else
    echo "   AVISO: no se encontro el simbolo AELI exportado. Revisa que"
    echo "   no se haya compilado con -fvisibility=hidden por defecto."
fi

echo "== 5/5: dependencias de la libreria =="
readelf -d libivanna_fft_effect.so | grep NEEDED

echo ""
echo "Listo: libivanna_fft_effect.so generado en $(pwd)"
echo "Siguiente paso: bash package_module.sh"

#!/data/data/com.termux/files/usr/bin/bash
# package_termux.sh
# Arma el zip flasheable final, metiendo el .so recien compilado en la
# estructura del modulo. Correr DESPUES de build_termux.sh, desde la
# carpeta que contenga: build_termux.sh, libivanna_fft_effect.so (ya
# compilado), y la carpeta module/ con el resto del modulo.

set -e

if [ ! -f "libivanna_fft_effect.so" ]; then
    echo "ERROR: no encuentro libivanna_fft_effect.so en esta carpeta."
    echo "Corre primero: bash build_termux.sh"
    exit 1
fi

pkg install -y zip

rm -f ivanna_fft_effect_module.zip
rm -f module/system/lib64/soundfx/libivanna_fft_effect.so.PLACEHOLDER
cp libivanna_fft_effect.so module/system/lib64/soundfx/libivanna_fft_effect.so

cd module
zip -r -X ../ivanna_fft_effect_module.zip META-INF module.prop customize.sh uninstall.sh system -x '.*'
cd ..

echo ""
echo "Listo: ivanna_fft_effect_module.zip"
echo "Siguiente paso: abre la app Magisk -> Modulos -> Instalar desde"
echo "almacenamiento -> selecciona este zip -> reinicia."

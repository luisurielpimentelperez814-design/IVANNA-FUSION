#!/system/bin/sh
set_perm $MODPATH/system/bin/omega_daemon 0 0 0755

# libomega_effect.so faltaba por completo en este script (y en el
# módulo instalado) — audio_effects.xml referenciaba una librería que
# nunca se copiaba ni se le fijaban permisos, causando que audioserver
# fallara al intentar cargarla. set_perm con 0644 (lectura para todos,
# escritura solo root) es el permiso estándar para librerías cargadas
# por audioserver (mismo patrón que otras .so en soundfx/).
if [ -f "$MODPATH/system/vendor/lib64/soundfx/libomega_effect.so" ]; then
    set_perm $MODPATH/system/vendor/lib64/soundfx/libomega_effect.so 0 0 0644
fi
if [ -f "$MODPATH/system/etc/audio_effects.xml" ]; then
    set_perm $MODPATH/system/etc/audio_effects.xml 0 0 0644
fi

#!/system/bin/sh
# IVANNA-FUSION / Ω_in — service.sh
#
# CORRECCIÓN: la versión anterior lanzaba omega_daemon una sola vez sin
# ningún control de fallos. Si el daemon crasheaba (por ejemplo al no
# poder bind() un socket que quedó "fantasma" de una ejecución previa
# tras un reinicio), no había reintento ni límite -> en combinación con
# el bug de libomega_effect.so faltante (que crasheaba audioserver), el
# dispositivo podía entrar en un ciclo de reinicios. Esta versión:
#   1. Espera a que el sistema esté completamente arrancado (sleep 10,
#      conservado).
#   2. Lanza el daemon con un máximo de reintentos (5) y backoff
#      creciente, en vez de un bucle infinito de respawn inmediato.
#   3. Loguea a un archivo en /data/local/tmp para diagnóstico, ya que
#      un daemon en boot no tiene logcat visible fácilmente.

sleep 10

LOG_FILE="/data/local/tmp/omega_daemon_service.log"
MAX_RETRIES=5
RETRY=0

echo "[$(date)] service.sh iniciado" >> "$LOG_FILE"

while [ $RETRY -lt $MAX_RETRIES ]; do
    echo "[$(date)] Intento $((RETRY+1))/$MAX_RETRIES de iniciar omega_daemon" >> "$LOG_FILE"
    /system/bin/omega_daemon >> "$LOG_FILE" 2>&1 &
    DAEMON_PID=$!

    # Esperar un poco y verificar que el proceso sigue vivo (detecta
    # crashes inmediatos, p.ej. fallo de bind() en el socket).
    sleep 3
    if kill -0 "$DAEMON_PID" 2>/dev/null; then
        echo "[$(date)] omega_daemon corriendo (PID $DAEMON_PID)" >> "$LOG_FILE"
        break
    fi

    RETRY=$((RETRY+1))
    echo "[$(date)] omega_daemon terminó inmediatamente, reintentando en $((RETRY*2))s" >> "$LOG_FILE"
    sleep $((RETRY*2))
done

if [ $RETRY -ge $MAX_RETRIES ]; then
    echo "[$(date)] omega_daemon falló $MAX_RETRIES veces seguidas, no se reintenta más. Ver $LOG_FILE" >> "$LOG_FILE"
fi

#!/system/bin/sh
MODDIR=${0%/*}
LOGFILE=/data/omega/daemon.log

sleep 10

echo "[$(date)] Iniciando Ω_in daemon..." > $LOGFILE

if [ ! -f /system/bin/omega_daemon ]; then
    echo "[$(date)] ERROR: Daemon no encontrado" >> $LOGFILE
    exit 1
fi

mkdir -p /data/omega
chmod 755 /data/omega

nohup taskset -c 6,7 /system/bin/omega_daemon > $LOGFILE 2>&1 &
DAEMON_PID=$!

echo "[$(date)] Daemon PID: $DAEMON_PID" >> $LOGFILE
sleep 2

if kill -0 $DAEMON_PID 2>/dev/null; then
    echo "[$(date)] ✓ Daemon corriendo" >> $LOGFILE
else
    echo "[$(date)] ERROR: Daemon falló" >> $LOGFILE
fi

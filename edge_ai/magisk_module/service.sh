#!/system/bin/sh
MODDIR=${0%/*}
OMEGA_DIR=/data/adb/omega

mkdir -p $OMEGA_DIR
chmod 0777 $OMEGA_DIR

sleep 5

nohup $MODDIR/omega_daemon > $OMEGA_DIR/daemon.log 2>&1 &
echo $! > $OMEGA_DIR/daemon.pid

chmod 0666 $OMEGA_DIR/shm_*.dat 2>/dev/null
chmod 0666 $OMEGA_DIR/ctrl.sock 2>/dev/null

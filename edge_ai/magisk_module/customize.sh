#!/sbin/sh
SKIPUNZIP=0

ARCH=$(getprop ro.product.cpu.abi)
ui_print "- Architecture: $ARCH"

if [ "$ARCH" != "arm64-v8a" ]; then
    abort "! Only arm64-v8a supported"
fi

SOC=$(cat /sys/devices/soc0/machine 2>/dev/null || echo "unknown")
ui_print "- SoC: $SOC"

mkdir -p /data/adb/omega
chmod 0777 /data/adb/omega

ui_print "- Module installed"
ui_print "- Reboot to activate"

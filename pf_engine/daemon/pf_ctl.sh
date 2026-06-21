#!/system/bin/sh
# pf_ctl.sh — CLI de control para pf-daemon
# Uso: pf_ctl.sh <comando>
# Ejemplos:
#   pf_ctl.sh "drive=2.5;wet=0.8;amp=0"
#   pf_ctl.sh status
#   pf_ctl.sh load:70s_rock
#   pf_ctl.sh save:my_preset

SOCK=/data/pf/pf.sock
CMD="$1"

if [ -z "$CMD" ]; then
    echo "uso: pf_ctl.sh <comando>"
    echo "comandos: status | load:<name> | save:<name> | amp:<0-4> | bar"
    echo "          <param>=<val>;... (alpha,beta,gamma,delta,sigma,drive,wet,low,mid,high,presence,sag)"
    exit 1
fi

echo -n "${CMD}" | nc -U "$SOCK"

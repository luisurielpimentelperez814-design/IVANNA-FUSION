# PF-ENGINE PRO MAX NEXT LEVEL v3.0.0

Motor de audio DSP en tiempo real para Android con root/Magisk.

## Arquitectura

```
PF-ENGINE-PRO-MAX-NEXT/
├── core/
│   ├── pf_engine.h          ← API pública + tipos (PFParams, PFBusID, PFAmpModel)
│   ├── pf_engine.cpp        ← Motor central: init, process, command parser, presets I/O
│   └── pf_evolution.cpp     ← Time Evolution Engine (BAR 0/16/32/48)
├── dsp/
│   └── pf_dsp.cpp           ← Biquad EQ, waveshaping, NEON SIMD, cadena de efectos
├── amps/
│   └── amp_models.cpp       ← Marshall / Fender / Vox / 70s Rock — parámetros por bus
├── learning/
│   ├── pf_fft.h / .cpp      ← FFT recursiva (Cooley-Tukey)
│   ├── pf_learning.h        ← API del motor de aprendizaje
│   └── pf_learning.cpp      ← Feature extraction → param mapping → optimizador
├── daemon/
│   ├── pf_daemon.cpp        ← Daemon UNIX socket /data/pf/pf.sock
│   └── pf_ctl.sh            ← CLI de control
├── magisk/
│   ├── module.prop
│   ├── customize.sh
│   ├── service.sh           ← Autostart del daemon en boot
│   └── META-INF/...
├── config/
│   ├── audio_effects.conf   ← Overlay para audio_effects del sistema
│   └── pf_defaults.conf     ← Configuración runtime
├── presets/
│   ├── 70s_rock.json/.pfp
│   ├── psychedelic.json/.pfp
│   ├── clean_studio.json/.pfp
│   ├── marshall_crunch.json/.pfp
│   └── vox_sparkle.json/.pfp
└── build/
    ├── CMakeLists.txt
    ├── build_android.sh     ← Build con Android NDK
    └── gen_presets.py       ← JSON → .pfp binario
```

## Build

```bash
NDK=/opt/android-ndk ./build/build_android.sh arm64-v8a
```

## Control en tiempo real

```bash
# Parámetros directos
pf_ctl "drive=2.5;wet=0.8;amp=0"

# Cargar preset
pf_ctl load:70s_rock

# Estado actual
pf_ctl status

# Cambiar amp model (0=Marshall 1=Fender 2=Vox 3=Rock70s)
pf_ctl amp:0
```

## Protocolo socket

```
alpha=1.2;beta=0.5;delta=0.6;drive=2.0;wet=0.7
load:<name>
save:<name>
amp:<0-4>
status
quit
```

## Amp Models

| ID | Modelo | Drive curve | EQ |
|----|--------|-------------|-----|
| 0 | Marshall | tanh + cubic asimétrico | High boost, mid scoop |
| 1 | Fender | arctan suave | Low warm, hi presence |
| 2 | Vox | tanh + harmónicos | Mid sparkle 1.2kHz |
| 3 | Rock70s | cubic→tanh cascade | Full stack |

## Time Evolution

| BAR | Estado |
|-----|--------|
| 0 | Baseline limpio |
| 16 | Build energético (+drive +beta) |
| 32 | Peak saturación (+delta +presence) |
| 48 | Decay controlado (wet estabilizado) |

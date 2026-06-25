# IVANNA-FUSION DSP v2.0

> © 2025-2026 Luis Uriel Pimentel Pérez — GORE TNS. Todos los derechos reservados.

Motor de audio DSP profesional para Android, distribuido como **Módulo Magisk** + **app de control** (Kotlin + Jetpack Compose). Optimizado para Snapdragon 4 Gen 2 / Android 14-15 con aceleración ARM NEON SIMD.

---

## ✨ Características

### DSP Core — ARM NEON SIMD
| Módulo | Descripción |
|---|---|
| **EQ Paramétrico 8 bandas** | Biquad IIR en cascada, Direct Form II Transposed, `float32x2_t` NEON |
| **Compresor Soft-Knee** | RMS linked-stereo, attack/release configurable, makeup gain, NaN-safe |
| **Excitador Armónico** | Waveshaping `tanh` + HPF pre/post, mezcla wet/dry adaptativa |
| **FFT Spectral Effect** | Realce de graves/agudos por bloques de 256 samples, zero malloc en hot path |

### PF-ENGINE-PRO-MAX-NEXT v3.0
- **Amp Modeling**: Marshall Crunch · Fender Clean · Vox Sparkle · 70s Rock Full Stack · Bypass
- **Parámetros espectrales**: α (tilt) · β (harmonic density) · γ (transient) · δ (distortion) · σ (spatial width)
- **Evolution Curve**: Build → Peak → Decay sincronizada automáticamente
- **FFT Learning**: análisis espectral de referencia para auto-parametrización

### App de Control
| Pantalla | Función |
|---|---|
| Dashboard | Métricas en tiempo real: RMS, espectro 32 bandas, correlación, BPM, género |
| Efectos | EQ 8 bandas · Compresor · Excitador · FFT |
| PF-Engine | Selector de amp model + sliders espectrales α β γ δ σ + Evolution Curve |
| Presets | 6 presets con parámetros visualizados |
| IA | Clasificador espectral, captura de audio interno (Spotify/YouTube) |
| Ajustes | Auditoría de parámetros del sistema y estado del hardware |

---

## 🏗️ Arquitectura

```
IVANNA-FUSION/
├── app/                          # Android app (Kotlin + Jetpack Compose)
│   └── src/main/
│       ├── cpp/                  # JNI: ivanna_jni (DSP), omega_effect (HAL), omega_daemon
│       └── java/com/ivannafusion/
│           ├── AudioEngine.kt    # Motor de audio + métricas homeostáticas
│           ├── DSPState.kt       # Estado global persistido (DataStore)
│           ├── IvannaNativeLib.kt# Bindings JNI evolutivos y phase oracle
│           ├── OmegaDaemon.kt    # Interfaz con el daemon root vía UDS
│           ├── PresetManager.kt  # Gestión de presets
│           └── ui/               # Screens + Components + Theme
│
├── src/                          # DSP core — libivanna_fusion.so
│   ├── cpp/                      # biquad_neon, PEQ, compressor, exciter, effect_library
│   ├── include/                  # Headers DSP
│   └── fft/                      # FFT spectral effect
│
├── pf_engine/                    # PF-ENGINE-PRO-MAX-NEXT v3.0
│   ├── core/                     # pf_engine, pf_evolution
│   ├── dsp/                      # amp models, biquad NEON
│   ├── amps/                     # Marshall / Fender / Vox / 70sRock
│   └── learning/                 # FFT learning + auto-parametrización
│
├── magisk_module/                # Módulo Magisk
│   ├── daemon_src/               # omega_daemon_main.cpp (daemon root)
│   └── system/                   # SELinux + install scripts
│
├── system/
│   ├── lib64/soundfx/            # libivanna_fft_effect.so
│   └── vendor/lib64/soundfx/     # libivanna_fusion.so (arm64)
│
├── presets/                      # Presets JSON + binarios .pfp
├── config/                       # audio_effects.conf / audio_effects.xml
├── sepolicy/                     # SELinux policy rules
├── scripts/                      # build_and_sign.sh, verify_latency.py
└── .github/workflows/            # CI: build-apk.yml, ivanna-ci.yml
```

---

## 🛠️ Compilación

### Requisitos
- Android NDK r25c+
- CMake 3.22+
- Android Studio Hedgehog+ (AGP 8.x)
- Dispositivo con Magisk v24+ (para el módulo)

### APK (vía Gradle)

```bash
git clone https://github.com/luisurielpimentelperez814-design/IVANNA-FUSION.git
cd IVANNA-FUSION
./gradlew assembleUniversalRelease     # 4 ABIs — distribución general
./gradlew assembleEnterpriseRelease    # arm64-v8a solo — Magisk/root
```

### Módulo Magisk (libivanna_fusion.so + zip)

```bash
cd src
cmake -DANDROID_ABI=arm64-v8a \
      -DANDROID_NDK=$NDK_HOME \
      -DCMAKE_TOOLCHAIN_FILE=$NDK_HOME/build/cmake/android.toolchain.cmake \
      -DANDROID_PLATFORM=android-26 \
      -DCMAKE_BUILD_TYPE=Release \
      -B build_arm64
cmake --build build_arm64 --target ivanna_fusion

cp build_arm64/libivanna_fusion.so system/vendor/lib64/soundfx/

zip -r IVANNA-FUSION-v2.0.zip \
    META-INF system presets module.prop service.sh customize.sh sepolicy
```

### Daemon root (arm64)

```bash
cd magisk_module
bash build_daemon.sh
```

### CI / GitHub Actions

| Workflow | Trigger | Artefacto |
|---|---|---|
| `build-apk.yml` | push a `main` | APK universal (4 ABIs) |
| `ivanna-ci.yml` | push a `main` | APK enterprise arm64 + módulo Magisk .zip |

Para firmar con keystore propio:
1. `base64 -w0 tu-keystore.jks` → secreto `IVANNA_KEYSTORE_B64`
2. Contraseñas: secretos `IVANNA_KEYSTORE_PASSWORD` y `IVANNA_KEY_PASSWORD`

---

## 🎛️ Presets incluidos

| Preset | Amp Model | Drive | Descripción |
|---|---|---|---|
| Clean Studio | Fender | 0.8 | Grabación vocal/guitarra cristalina |
| Marshall Crunch | Marshall | 3.2 | Stack clásico, crunch brutal |
| Vox Sparkle | Vox | 1.8 | AC30, medios brillantes y chispeantes |
| 70s Rock | Rock70s | 2.8 | Grand Funk / Rush, cuerpo y ataque |
| Psychedelic | Rock70s | 2.2 | Floyd / Hendrix, armónicos amplios |
| Flat | Bypass | 1.0 | Señal pura sin coloración |

---

## 📦 Instalación del módulo Magisk

1. Descargar `IVANNA-FUSION-v2.0.zip` desde Releases o compilar localmente
2. En Magisk Manager → Módulos → Instalar desde almacenamiento
3. Seleccionar el `.zip` y reiniciar
4. Instalar la APK de control
5. *(Opcional)* Copiar `yamnet.tflite` + `yamnet_class_map.csv` a `app/src/main/assets/` para clasificación de audio con YAMNet (ver `app/src/main/assets/README_MODEL.txt`)

---

## 📋 Notas de versión

### v2.0 (2026-06)
- PF-ENGINE-PRO-MAX-NEXT v3.0 integrado: amp modeling completo, parámetros espectrales α β γ δ σ
- Evolution Curve automática (Build→Peak→Decay)
- FFT spectral effect integrado en el hot path (zero malloc)
- App de control: 6 pantallas con persistencia real vía DataStore
- Módulo Magisk: interfaz `audio_effect_library_t` real, sin crash de audioserver
- Captura de audio interno del sistema (MediaProjection / PlaybackCaptureService)
- Clasificador de género espectral + detección de BPM en tiempo real
- CI: dos workflows (APK universal + APK enterprise con módulo Magisk)

---

## ⚖️ Licencia

© 2025-2026 Luis Uriel Pimentel Pérez — GORE TNS. Todos los derechos reservados.  
Uso, reproducción o distribución sin autorización expresa del autor está prohibida.

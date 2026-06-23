# IVANNA-FUSION TRASCENDENTAL v2.0

> © 2025-2026 Luis Uriel Pimentel Pérez — GORE TNS. Todos los derechos reservados.

## ⚠️ BASE ACTIVA DEL PROYECTO (LEER PRIMERO)

Este repositorio acumuló, a lo largo de varias sesiones de trabajo en
paralelo, **5 implementaciones distintas del mismo motor de audio**:
`app/src/main/cpp/` + `src/cpp`/`src/include`, `edge_ai/`, `pf_engine/`,
`omega_engine/`, y `core/`+`include/ivanna/` ("Industrial Platform v2.0").

**La ÚNICA base que Gradle realmente compila hacia el APK, y la única
verificada end-to-end (incluye YAMNet para clasificación de audio real),
es:**

```
app/src/main/cpp/          ← código fuente que Gradle compila (CMakeLists.txt referenciado en app/build.gradle)
src/cpp/ + src/include/    ← motor DSP real (EQ paramétrico, compresor, exciter), incluido por el CMakeLists de arriba
magisk_module/             ← módulo Magisk con la interfaz Audio Effect HAL real (audio_effect_library_t)
```

**Por qué se descartaron las otras 4 (sin borrarlas — siguen en el repo
como referencia):**
- `edge_ai/`, `pf_engine/`, `omega_engine/` (Python): no están
  referenciadas desde ningún `CMakeLists.txt` que Gradle invoque — son
  código huérfano respecto al build real, sin importar su contenido.
- `core/` + `include/ivanna/` ("Industrial Platform v2.0"): mismo
  problema de desconexión del build, y además su biquad NEON no logra
  paralelismo real (la recursión IIR se calcula escalar, solo se
  empaqueta en vectores al final — sin ganancia de SIMD), y su
  `AudioPipeline` central tiene el resampler marcado explícitamente
  como `// Stage 1: ... (resampling placeholder)` en el código fuente.

Si vas a seguir desarrollando este proyecto en una nueva sesión:
**edita dentro de `app/src/main/cpp/`, `src/cpp/`, `src/include/`, y
`magisk_module/`**. Cualquier cambio a los otros directorios no tendrá
efecto en el APK ni en el módulo instalado hasta que se conecte
explícitamente al `CMakeLists.txt` real.

**Rescatado de `core/` hacia la base activa:** `complexity_registry.h`
(control de presupuesto de CPU/memoria por módulo) se portó a
`src/include/complexity_registry.h` — es la única pieza de
"Industrial Platform v2.0" sin el problema de SIMD cosmético o
placeholders, y es independiente del resto de ese directorio.

---

## 🔊 El mejor procesador de audio DSP del mundo

Motor de audio DSP real para Android, diseñado para Snapdragon 4 Gen 2 / Android 14-15.  
Implementado como **Magisk Module** (AudioFlinger effect) + **app de control** (Kotlin + Jetpack Compose).

---

## ✅ Arquitectura v2.0

### DSP Core (ARM NEON SIMD)
- **8-band Parametric EQ** — biquad IIR cascadeados, Direct Form II Transposed con NEON float32x2_t
- **Soft-Knee Compressor** — RMS linked-stereo, attack/release configurable, makeup gain
- **Harmonic Exciter** — waveshaping tanh + HPF pre/post, mezcla wet/dry

### PF-ENGINE-PRO-MAX-NEXT v3.0.0
- **Amp Modeling**: Marshall Crunch · Fender Clean · Vox Sparkle · 70s Rock Full Stack · Bypass
- **Spectral Parameters**: α (tilt) · β (harmonic density) · γ (transient) · δ (distortion) · σ (spatial width)
- **Evolution Curve**: curva automática Build→Peak→Decay sincronizada por compases
- **FFT Learning**: análisis espectral de referencia para auto-parametrización

### FFT Spectral Effect
- Realce de graves/agudos por bloques de 256 samples vía FFT radix-2 real
- Zero malloc en el hot path — completamente real-time safe

### App de Control
| Pantalla | Función |
|---|---|
| Intro | Splash animado con logo pulsante |
| Simbiosis | Control principal: fusion level, visualizador de fase, Kalman tracker |
| Monitor | Latencia en tiempo real, SHM, stats de audio |
| Presets | 6 presets PF-ENGINE con parámetros visualizados |
| PF-Engine | Amp model selector + sliders espectrales + Evolution Curve |
| IA | Motor evolutivo, planificador térmico, Oráculo de Fase |
| Ajustes | Auditoría de parámetros del sistema |

---

## 📦 Estructura del repositorio

```
IVANNA-FUSION/
├── src/                          # DSP core (libivanna_fusion.so)
│   ├── cpp/                      # biquad, PEQ, compressor, exciter, effect_library
│   ├── include/                  # headers DSP
│   ├── fft/                      # FFT spectral effect (ivanna_fft_effect.c)
│   └── CMakeLists.txt            # Build: ivanna_fusion + pf_engine + fft
│
├── pf_engine/                    # PF-ENGINE-PRO-MAX-NEXT v3.0.0
│   ├── core/                     # pf_engine.h/cpp, pf_evolution.h/cpp
│   ├── dsp/                      # pf_dsp.cpp: amp models, biquad, NEON frame
│   ├── amps/                     # amp_models.cpp: Marshall/Fender/Vox/Rock70s
│   ├── learning/                 # pf_fft.h/cpp, pf_learning.h/cpp
│   ├── daemon/                   # pf_daemon.cpp, pf_ctl.sh
│   ├── config/                   # pf_defaults.conf, audio_effects.conf
│   └── CMakeLists.txt
│
├── presets/                      # Presets JSON + binarios .pfp
│   ├── clean_studio.json
│   ├── marshall_crunch.json
│   ├── vox_sparkle.json
│   ├── 70s_rock.json
│   └── psychedelic.json
│
├── app/                          # Android app (Kotlin / Jetpack Compose)
│   └── src/main/java/com/ivannafusion/
│       ├── MainActivity.kt
│       ├── AudioEngine.kt        # AAudio + JNI bridge (inc. PF-ENGINE JNI)
│       ├── PresetsScreen.kt      # Selector de presets con panel de parámetros
│       ├── PFEngineScreen.kt     # Amp models + spectral sliders + Evolution
│       ├── AIScreen.kt           # Motor evolutivo + Oráculo de Fase
│       ├── MonitorScreen.kt
│       ├── SimbiosisScreen.kt
│       └── ...
│
├── system/
│   ├── lib64/soundfx/            # libivanna_fft_effect.so (FFT module)
│   ├── vendor/lib64/soundfx/     # libivanna_fusion.so (arm64 compilado)
│   ├── vendor/lib/soundfx/       # libivanna_fusion.so (arm32 fallback)
│   ├── vendor/etc/audio_effects.xml   # Config AudioFlinger (vendor path)
│   └── etc/audio_effects_ivanna.xml   # Config AudioFlinger (system path)
│
├── META-INF/com/google/android/
│   ├── update-binary             # Installer Magisk (detecta XML/CONF, parchea)
│   └── updater-script
│
├── service.sh                    # Verifica carga de .so tras boot
├── module.prop                   # v2.0
├── .github/workflows/            # CI: build-magisk.yml, build-apk.yml
└── docs/                         # Arquitectura, instrucciones compilación
```

---

## 🛠️ Compilación

### Requisitos
- Android NDK r25c+
- CMake 3.22+
- Android Studio Hedgehog+ (o AGP 8.x)
- Dispositivo rooteado con Magisk v24+

### Build del módulo Magisk

```bash
# 1. Clonar
git clone https://github.com/luisurielpimentelperez814-design/IVANNA-FUSION.git
cd IVANNA-FUSION

# 2. Compilar librería nativa
cd src
cmake -DANDROID_ABI=arm64-v8a \
      -DANDROID_NDK=$NDK_HOME \
      -DCMAKE_TOOLCHAIN_FILE=$NDK_HOME/build/cmake/android.toolchain.cmake \
      -DANDROID_PLATFORM=android-26 \
      -DCMAKE_BUILD_TYPE=Release \
      -B build_arm64
cmake --build build_arm64 --target ivanna_fusion

# 3. Copiar .so al módulo
cp build_arm64/libivanna_fusion.so system/vendor/lib64/soundfx/

# 4. Empaquetar .zip de Magisk
zip -r IVANNA-FUSION-v2.0.zip \
    META-INF system presets module.prop service.sh customize.sh sepolicy
```

### Build del APK via GitHub Actions

El workflow `.github/workflows/build-apk.yml` compila el APK automáticamente en cada push a `main`.

Para firmar el APK:
1. `base64 -w0 ivanna-keystore.jks` → secreto `IVANNA_KEYSTORE_B64`
2. Contraseñas: secretos `IVANNA_KEYSTORE_PASSWORD` y `IVANNA_KEY_PASSWORD`

---

## 🎛️ Presets incluidos

| Preset | Amp | Drive | Descripción |
|---|---|---|---|
| Clean Studio | Fender | 0.8 | Grabación vocal/guitarra cristalina |
| Marshall Crunch | Marshall | 3.2 | Stack clásico, crunch brutal |
| Vox Sparkle | Vox | 1.8 | AC30, medios brillantes y chispeantes |
| 70s Rock | Rock70s | 2.8 | Grand Funk / Rush, cuerpo y ataque |
| Psychedelic | Rock70s | 2.2 | Floyd / Hendrix, harmónicos amplios |
| Flat | Bypass | 1.0 | Señal pura, sin coloración |

---

## 📋 Notas de versión

### v2.0 (2026-06-19)
- ✅ Integración PF-ENGINE-PRO-MAX-NEXT v3.0.0 completa
- ✅ Amp modeling: Marshall / Fender / Vox / 70s Rock con sag simulation
- ✅ Spectral parameters: α β γ δ σ
- ✅ Evolution Curve automática (Build→Peak→Decay)
- ✅ FFT spectral effect (ivanna_fft_effect.c) integrado
- ✅ Nueva pantalla PresetsScreen con 6 presets y panel de parámetros
- ✅ Nueva pantalla PFEngineScreen: amp model selector + sliders + Evolution
- ✅ Compilados: libivanna_fusion.so arm64+arm32, libivanna_fft_effect.so
- ✅ audio_effects.xml para vendor path (Motorola/Snapdragon)
- ✅ CMakeLists.txt unificado: IVANNA core + PF-ENGINE + FFT en una sola .so

### v1.0 (2026-06-18)
- Motor DSP inicial: 8-band PEQ + Compressor + Harmonic Exciter
- Magisk module compatible Snapdragon 4 Gen 2

# IVANNA-FUSION TRASCENDENTAL v2.0

> © 2025-2026 Luis Uriel Pimentel Pérez — GORE TNS. Todos los derechos reservados.

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

<<<<<<< HEAD
## 🛠️ Compilación local (alternativa)
Ver `docs/instrucciones_compilacion.md` para el flujo en Android Studio con
tu Moto G85.
 

---

## Errores de compilación resueltos (v2.1)

| # | Archivo | Error | Fix |
|---|---------|-------|-----|
| 1 | `build.gradle` / `settings.gradle` | `pluginManagement {}` y `dependencyResolutionManagement {}` en el build script raíz — métodos inválidos fuera de `settings.gradle`, Gradle fallaba antes de compilar cualquier fuente | Movidos a `settings.gradle` (deben ir primero) |
| 2 | `IvannaNativeLib.kt` | Dos pares de declaraciones `external fun` en la misma línea sin newline ni `;` — error de parseo del compilador Kotlin | Separadas en líneas individuales |
| 3 | `phase_oracle.cpp` | `#define M_PI …f` redefinía M_PI como float tras `#include <cmath>`; Clang 18 (NDK r27c) lo trata como error con `-Wall` | Eliminado; reemplazado por `static constexpr float kPif` |
| 4 | `phase_oracle.cpp` | `#include <arm_neon.h>` incondicional: rompía smoke-test de CI en host x86_64 | Guardado con `#ifdef __aarch64__` |
| 5 | `app/build.gradle` | `-fopenmp -static-openmp` en `cppFlags` sin linking OpenMP en CMakeLists.txt — riesgo de `undefined reference __kmpc_*` en NDK r27c | Flags eliminados (ninguna fuente usa `#pragma omp`) |
| 6 | `ivanna_fusion.cpp` | `loadPreset()` llamaba `reset()` al terminar, borrando estados de filtros recién configurados → clic audible + inconsistencia con path JNI | Eliminado; comentario documenta la responsabilidad del caller |
| 7 | `ivanna_fft_effect.c` | Forward-decl `static const struct effect_interface_s ivanna_itfe;` innecesaria (Clang 18 la marca con `-Wextern-initializer`) | Removida; la definición con inicializador es suficiente en C |

> NDK: ambos workflows ahora usan **r27c (27.2.12479018)** para coherencia diagnóstica.
=======
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
>>>>>>> 82b483f (feat(v2.0): fusión PF-ENGINE v3.0.0 + FFT Effect + Presets + nuevas pantallas UI)

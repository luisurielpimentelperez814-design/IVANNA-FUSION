# IVANNA-FUSION TRASCENDENTAL

> © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.

## ✅ Estado actual

Con el segundo zip (`Ivanna_hija.zip`) se recuperaron los 16 archivos que
faltaban. **El proyecto ya está completo** según el árbol original
(`docs/estructura_original.txt`):

- 9 archivos Kotlin en `app/src/main/java/com/ivannafusion/`
  (incluye `IVANNAApplication.kt`, que no aparecía en el árbol pero es
  requerido por `AndroidManifest.xml`)
- 4 archivos C++ nativos en `app/src/main/cpp/`
- `app/src/main/assets/thermal_sched.bpf`
- `app/src/main/res/raw/copyright.txt`
- `scripts/verify_latency.py`
- `docs/ARQUITECTURA.md` (documento de arquitectura)

## 🔧 Cambios que hice para que el build no falle de inmediato

1. **Recursos faltantes** (`AndroidManifest.xml` los referenciaba pero no
   existían — habrían fallado en `aapt2`/resource linking):
   - `res/values/strings.xml` (`app_name`)
   - `res/values/themes.xml` (`Theme.IVANNAFusion`, basado en un tema nativo
     de Android para no agregar dependencias extra; la UI real la dibuja
     Compose `MaterialTheme` en `MainActivity`)
   - `res/mipmap/ic_launcher.png` y `ic_launcher_round.png` (íconos
     placeholder generados — reemplázalos por el diseño final cuando lo
     tengas)

2. **`app/build.gradle`**: la dependencia `org.vosk:vosk-android:0.3.47` no
   existe con ese groupId en Maven Central; el artefacto real es
   `com.alphacephei:vosk-android:0.3.47`. Lo corregí. Nota: ninguna clase de
   Vosk se usa todavía en el código Kotlin (no hay reconocimiento de voz
   implementado), así que si no lo vas a usar también se puede quitar la
   dependencia por completo.

## ⚠️ Cosas a revisar (no bloquean el build, pero afectan funcionalidad)

- **`evolutionary_kernel.cpp`** exporta 4 funciones JNI
  (`nativeInitializeEvolution`, `nativeGetBestFitness`, `nativeGetGeneration`,
  `nativeEvolveStep`) y **`phase_oracle.cpp`** exporta
  `nativePredictSamples`, pero **`AudioEngine.kt` no las declara como
  `external fun`**. Compilarán dentro del `.so`, pero nunca se llamarán
  desde Kotlin — el algoritmo evolutivo y la predicción de fase quedan
  "muertos" hasta que se agreguen esas declaraciones y se invoquen desde
  `AudioEngine`/`MainActivity`.
- **`ThermalMonitor.kt`** intenta `su -c "bpftool prog load ..."` para cargar
  `thermal_sched.bpf` — esto solo funciona con root y `bpftool` presente
  (coincide con tu "kernel modificado", pero fallará silenciosamente —
  capturado por `catch`— en un dispositivo sin root).
- **Flags de compilación**: `-march=armv8.2-a+fp16+dotprod -std=c++23` con
  NDK r25c — si el build de CI falla en el paso de CMake, prueba primero
  bajando a `-std=c++20` y/o quitando `+dotprod` como diagnóstico.

## 📦 Cómo continuar

1. Sube este zip a un repo nuevo en GitHub (o descomprime y `git init` /
   `add` / `push`).
2. Para que el workflow de Actions firme el APK, sube tu
   `ivanna-keystore.jks` real como secreto `IVANNA_KEYSTORE_B64`
   (`base64 -w0 ivanna-keystore.jks`), y `IVANNA_KEYSTORE_PASSWORD` /
   `IVANNA_KEY_PASSWORD` como secretos del repo.
3. El workflow `.github/workflows/build-apk.yml` corre `assembleRelease` y
   sube el APK como artefacto descargable.

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

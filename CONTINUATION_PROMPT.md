# PROMPT QUIRÚRGICO — IVANNA-FUSION, continuación

Repo: https://github.com/luisurielpimentelperez814-design/IVANNA-FUSION
Último commit verificado: 2a89d29 (rama main)

## ESTADO VERIFICADO (no asumir, ya confirmado por auditoría real):

1. **Base activa de compilación**: SOLO `app/src/main/cpp/` +
   `magisk_module/`. Todo lo demás (`core/`, `edge_ai/`, `pf_engine/`,
   `omega_engine/`) está documentado en README.md como NO conectado al
   build real de Gradle — no tocar esperando que afecte el APK.

2. **DSPState — DOS versiones, una activa**: `com.ivannafusion.DSPState`
   (sin paquete `dsp`, usa DataStore + mutableStateOf) es la ÚNICA en
   uso real (EffectsScreen/AIScreen/PFEngineScreen/SettingsScreen la
   importan). `com.ivannafusion.dsp.DSPState` (con paquete `dsp`,
   SharedPreferences + StateFlow) existe pero NADIE la importa —
   huérfana, no borrar, no usar como referencia para nuevo código.

3. **JNI AudioEngine**: CMakeLists.txt SOLO compila `jni_wrapper.cpp`
   como target `ivanna_jni`. Verificado: los 20 símbolos
   `Java_com_ivannafusion_AudioEngine_*` que Kotlin usa SÍ están todos
   en jni_wrapper.cpp (sin faltantes). Hay más símbolos en
   audio_orchestrator.cpp/evolutionary_kernel.cpp/ivanna_native_lib.cpp/
   phase_oracle.cpp que NO se compilan — no son necesarios actualmente,
   pero si se agrega una función nueva en Kotlin, su implementación
   real debe ir en jni_wrapper.cpp o agregarse su archivo fuente al
   CMakeLists.txt.

4. **Dos "omega_daemon" distintos, mismo nombre, NO confundir**:
   - `app/src/main/cpp/omega_daemon.cpp`: librería JNI para una clase
     `OmegaDaemon.kt` que NO EXISTE — huérfano, compilable pero sin uso.
   - `magisk_module/daemon_src/omega_daemon_main.cpp`: el daemon ROOT
     real, con `int main()`, lanzado por `service.sh`. Compilado por
     `magisk_module/daemon_src/CMakeLists.txt` (no por el de la app).

5. **Módulo Magisk — estabilidad confirmada intacta**: UUID en
   `audio_effects.xml` (8d7d5e0a-...) coincide con `omega_effect.cpp`.
   `service.sh` tiene guard anti-crash-loop (máx 5 reintentos).
   `omega_effect.cpp` implementa la interfaz real `audio_effect_library_t`
   (no solo JNI) — esto resolvió el crash original de audioserver.

6. **YAMNet**: integrado en `YamnetClassifier.kt`, requiere que el
   usuario descargue manualmente `yamnet.tflite` + `yamnet_class_map.csv`
   (instrucciones en `app/src/main/assets/README_MODEL.txt`) — Claude no
   tiene acceso a tfhub.dev desde el sandbox.

7. **CI**: dos workflows — `build-apk.yml` (flavor `universal`, 4 ABIs,
   sin Magisk) e `ivanna-ci.yml` (flavor `enterprise`, arm64-v8a +
   compila y empaqueta el módulo Magisk como artifact zip separado).
   Ambos con debug.keystore versionado en raíz del repo.

## LO QUE FALTA / PRÓXIMO PASO PEDIDO POR EL USUARIO:
Usuario pidió "crear nueva interfaz" — sin especificar qué (rediseño
visual, pantalla nueva, reorganización de navegación). PREGUNTAR
ANTES de construir, no asumir. Estado actual: 6 pantallas funcionales
(Dashboard/Effects/AI/Presets/Settings/PFEngine), 1229 líneas, todas
con persistencia real vía DSPState.

## REGLAS DEL USUARIO (NO romper):
- NUNCA borrar archivos, solo mejorar/consolidar/documentar como no-activo.
- No simular funciones sin motor real (surround/convolver real no
  existen — decirlo explícito en la UI, no fingir).
- Verificar SIEMPRE antes de prometer: Claude no tiene NDK/Gradle en su
  sandbox, no puede compilar — toda corrección es por inspección de
  código + búsqueda de documentación oficial cuando hay duda real de
  sintaxis (ej. Kotlin inner class dentro de object).
- Sesiones paralelas tocan los mismos archivos sin avisar — SIEMPRE
  `git pull` antes de editar, y diff contra el último estado conocido
  antes de asumir que un archivo sigue como se dejó.

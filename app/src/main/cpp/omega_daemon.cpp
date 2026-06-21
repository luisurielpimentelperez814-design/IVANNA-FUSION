/*
 * IVANNA-FUSION / Ω_in
 * omega_daemon.cpp — Root AI Daemon (consumidor del ring buffer SPSC)
 *
 * Responsabilidades (Fase 3):
 *   - Inicializa el modelo Ω_in (carga .pte/ExecuTorch en NPU/GPU vía
 *     delegates) — ver loadModel()/runInference() abajo: el punto de
 *     integración existe y está listo, pero la llamada real al runtime
 *     de ExecuTorch queda marcada explícitamente como pendiente hasta
 *     que exista un .pte real exportado (no hay ninguno en el repo
 *     todavía — ver omega_engine/export_to_executorch.py).
 *   - Unix Domain Socket para escuchar comandos de la APK
 *     (OmegaMagiskBridge.kt).
 *   - Bucle principal: lee del ring_in (lock-free, no bloqueante con
 *     backoff corto), ejecuta inferencia, escribe en ring_out.
 *   - Gestión térmica: si supera 42°C, reduce la complejidad del modelo.
 *
 * Reglas de oro respetadas:
 *   - Sin malloc/free en el bucle de audio: g_process_buf y los buffers
 *     de inferencia se pre-asignan una sola vez en main()/nativeStart().
 *   - Afinidad de CPU: el hilo de inferencia se fija a un big core via
 *     sched_setaffinity (Snapdragon 7s Gen 2: cores 6-7 son los Cortex-A78
 *     de alto rendimiento en la mayoría de configuraciones Kryo; se deja
 *     configurable porque el mapeo exacto de núcleos varía por SoC/kernel
 *     y no se puede asumir sin leer /sys/devices/system/cpu en el
 *     dispositivo real).
 */

#include <jni.h>
#include <android/log.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <sched.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <atomic>
#include <cstring>
#include <thread>
#include <chrono>
#include <string>

#include "omega_shared.h"

#define LOG_TAG "OmegaDaemon"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr const char* kShmPath    = "/data/local/tmp/omega_shared_mem";
constexpr const char* kSocketName = "omega_daemon_socket";  // abstract namespace, sin '\0' inicial en el array real
constexpr float       kThermalLimitC = 42.0f;

OmegaSharedState* g_shared = nullptr;
int               g_shm_fd  = -1;
std::atomic<bool> g_running{false};
std::thread       g_process_thread;
std::thread       g_socket_thread;
int               g_socket_fd = -1;

// Pre-asignado UNA vez al inicio (regla de oro: sin malloc/free en el
// bucle de audio ni en el bucle de inferencia).
float g_process_buf[OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS];

// ── Gestión térmica ─────────────────────────────────────────────────────
// Nivel de complejidad del modelo: 0 = completo, 1 = reducido (menos
// pasos de Sliced Wasserstein), 2 = mínimo (bypass de la rama de IA,
// solo deja pasar audio). Se reduce automáticamente sobre el umbral.
std::atomic<int> g_complexity_level{0};

float readBatteryTemperatureC() {
    // /sys/class/power_supply/battery/temp típicamente reporta décimas
    // de grado (ej. 350 = 35.0°C) en la mayoría de kernels Android.
    FILE* f = fopen("/sys/class/power_supply/battery/temp", "r");
    if (!f) return g_shared ? g_shared->current_temperature.load() : 35.0f;
    int raw = 0;
    if (fscanf(f, "%d", &raw) != 1) { fclose(f); return 35.0f; }
    fclose(f);
    return raw / 10.0f;
}

void updateThermalState() {
    float tempC = readBatteryTemperatureC();
    if (g_shared) g_shared->current_temperature.store(tempC);

    int level = 0;
    if (tempC >= kThermalLimitC + 5.0f) level = 2;       // >47°C: mínimo
    else if (tempC >= kThermalLimitC) level = 1;          // >42°C: reducido
    g_complexity_level.store(level, std::memory_order_relaxed);
}

// ── Modelo Ω_in (punto de integración ExecuTorch) ──────────────────────
// PENDIENTE REAL: no existe ningún archivo .pte en el repositorio (se
// verificó antes de escribir este archivo). loadModel() y runInference()
// quedan con la forma exacta que tendría la integración, pero sin
// inventar llamadas a una API de ExecuTorch que no se ha podido
// verificar contra el SDK real instalado en este proyecto. Cuando exista
// el .pte exportado (ver omega_engine/export_to_executorch.py), el
// cuerpo de estas dos funciones se completa con:
//   - loadModel: executorch::runtime::Program::load_from_file(path) +
//     Method::execute() preparado, delegates NPU/GPU según
//     DELEGATION_CONFIG del script de exportación.
//   - runInference: Method::execute(inputs) -> outputs, sin malloc
//     adicional (usar MemoryManager pre-allocado de ExecuTorch).
bool g_model_loaded = false;
std::string g_model_path;

bool loadModel(const std::string& pteFilePath) {
    // TODO(ExecuTorch real): reemplazar por carga real cuando exista
    // un .pte exportado. Por ahora, solo valida que el archivo exista
    // y registra la ruta, sin cargar ningún runtime.
    if (access(pteFilePath.c_str(), F_OK) != 0) {
        LOGE("loadModel: no existe %s (¿ya exportaste el .pte?)", pteFilePath.c_str());
        g_model_loaded = false;
        return false;
    }
    g_model_path = pteFilePath;
    g_model_loaded = true;
    LOGI("loadModel: ruta registrada %s (runtime ExecuTorch NO enlazado todavía)",
         pteFilePath.c_str());
    return true;
}

// Ejecuta inferencia sobre un bloque de audio ya leído del ring_in.
// Mientras no haya runtime de ExecuTorch enlazado, hace passthrough
// explícito (copia entrada -> salida) en vez de simular una mejora de
// audio falsa. El nivel de complejidad térmica (g_complexity_level) se
// aplica igual: en producción real, nivel 2 saltaría directamente esta
// función entera.
void runInference(const float* input, float* output, int n_samples) {
    if (!g_model_loaded) {
        std::memcpy(output, input, (size_t)n_samples * sizeof(float));
        return;
    }

    int level = g_complexity_level.load(std::memory_order_relaxed);
    if (level >= 2) {
        // Throttling térmico severo: bypass total de la rama de IA.
        std::memcpy(output, input, (size_t)n_samples * sizeof(float));
        return;
    }

    // TODO(ExecuTorch real): aquí va Method::execute() con los tensores
    // de entrada/salida ya pre-allocados (sin malloc en este punto).
    // 'level == 1' debería reducir pasos de Sliced Wasserstein una vez
    // exista esa rama del modelo real.
    std::memcpy(output, input, (size_t)n_samples * sizeof(float));
}

// ── Afinidad de CPU ─────────────────────────────────────────────────────
// Fija el hilo actual a los "big cores" de alto rendimiento. El mapeo
// exacto de índices de CPU a clústeres varía por SoC/kernel; se deja
// como constante documentada en vez de asumir un valor universal.
// Para Snapdragon 7s Gen 2 (configuración Kryo típica 1+3+4): los
// núcleos de mayor frecuencia suelen ser los últimos del rango — se
// fija aquí a los núcleos 6 y 7 como aproximación razonable, pero debe
// verificarse en el dispositivo real leyendo
// /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq antes de
// confiar en este valor en producción.
void pinThreadToBigCore() {
    cpu_set_t cpuset;
    CPU_ZERO(&cpuset);
    CPU_SET(6, &cpuset);
    CPU_SET(7, &cpuset);
    int rc = sched_setaffinity(0, sizeof(cpu_set_t), &cpuset);
    if (rc != 0) {
        LOGE("sched_setaffinity falló (errno=%d) — continuando sin afinidad fijada", errno);
    } else {
        LOGI("Hilo de inferencia fijado a cores 6-7 (verificar mapeo real en el dispositivo)");
    }
}

// ── Memoria compartida ──────────────────────────────────────────────────
bool init_shared_memory() {
    g_shm_fd = open(kShmPath, O_CREAT | O_RDWR, 0666);
    if (g_shm_fd < 0) {
        LOGE("Error abriendo archivo de memoria compartida: %s", kShmPath);
        return false;
    }
    if (ftruncate(g_shm_fd, sizeof(OmegaSharedState)) < 0) {
        LOGE("Error ajustando tamaño del archivo");
        close(g_shm_fd);
        return false;
    }
    void* mapped = mmap(nullptr, sizeof(OmegaSharedState),
                         PROT_READ | PROT_WRITE, MAP_SHARED, g_shm_fd, 0);
    if (mapped == MAP_FAILED) {
        LOGE("Error mapeando memoria compartida");
        close(g_shm_fd);
        return false;
    }
    g_shared = static_cast<OmegaSharedState*>(mapped);
    new (g_shared) OmegaSharedState();
    LOGI("Memoria compartida inicializada correctamente");
    return true;
}

void cleanup_shared_memory() {
    if (g_shared != nullptr) {
        munmap(g_shared, sizeof(OmegaSharedState));
        g_shared = nullptr;
    }
    if (g_shm_fd >= 0) {
        close(g_shm_fd);
        g_shm_fd = -1;
    }
}

// ── Bucle principal de procesamiento (consumidor del ring SPSC) ────────
void process_audio_thread() {
    pinThreadToBigCore();
    LOGI("Hilo de procesamiento iniciado");

    const int samples = OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS;
    int thermal_check_counter = 0;

    while (g_running.load()) {
        if (!g_shared->is_processing.load() || g_shared->bypass_enabled.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
            continue;
        }

        // Chequeo térmico periódico (no en cada bloque: leer sysfs en
        // cada iteración del hot path añadiría latencia innecesaria).
        if (++thermal_check_counter >= 200) {  // ~1s a bloques de 512@48kHz/stereo
            thermal_check_counter = 0;
            updateThermalState();
        }

        bool got_block = g_shared->ring_in.tryPop(
            g_process_buf, samples, &g_shared->input_buffer[0][0]);

        if (!got_block) {
            // Ring vacío: no hay audio nuevo. Backoff corto, NO busy-loop.
            std::this_thread::sleep_for(std::chrono::microseconds(500));
            continue;
        }

        auto t0 = std::chrono::steady_clock::now();

        // Inferencia real (o passthrough si el modelo no está cargado
        // todavía — ver runInference()).
        runInference(g_process_buf, g_process_buf, samples);

        auto t1 = std::chrono::steady_clock::now();
        float latency_ms = std::chrono::duration<float, std::milli>(t1 - t0).count();
        g_shared->current_latency_ms.store(latency_ms);

        // Si el push al ring de salida falla (lleno), el bloque procesado
        // se descarta; omega_effect ya hace bypass automático en ese caso,
        // así que no es necesario reintentar ni bloquear aquí.
        g_shared->ring_out.tryPush(g_process_buf, samples,
                                    &g_shared->output_buffer[0][0]);
    }

    LOGI("Hilo de procesamiento detenido");
}

// ── Unix Domain Socket (control desde la APK, Fase 3) ───────────────────
// Namespace abstracto (igual que usa OmegaMagiskBridge.kt con
// LocalSocketAddress.Namespace.ABSTRACT) — no requiere un nodo en el
// filesystem ni permisos de archivo especiales más allá del propio
// socket, y es accesible desde el proceso root del daemon y el proceso
// de la app sin compartir UID.
void handleClientCommand(const std::string& cmd) {
    auto starts = [&](const char* prefix) {
        return cmd.rfind(prefix, 0) == 0;
    };

    if (starts("SET_PROCESSING:")) {
        bool on = cmd.back() == '1';
        if (g_shared) g_shared->is_processing.store(on);
    } else if (starts("SET_INTENSITY:")) {
        float v = strtof(cmd.c_str() + strlen("SET_INTENSITY:"), nullptr);
        if (g_shared) g_shared->intensity.store(v);
    } else if (starts("SET_VOCODER_MIX:")) {
        float v = strtof(cmd.c_str() + strlen("SET_VOCODER_MIX:"), nullptr);
        if (g_shared) g_shared->vocoder_mix.store(v);
    } else if (starts("SET_BYPASS:")) {
        bool on = cmd.back() == '1';
        if (g_shared) g_shared->bypass_enabled.store(on);
    } else if (starts("SET_THERMAL_THROTTLE:")) {
        // Forzado manual desde la APK además del automático por sysfs.
        bool on = cmd.back() == '1';
        if (on) g_complexity_level.store(2, std::memory_order_relaxed);
    } else if (starts("RESET_DEFAULTS")) {
        if (g_shared) {
            g_shared->intensity.store(0.8f);
            g_shared->vocoder_mix.store(0.8f);
            g_shared->bypass_enabled.store(false);
        }
    } else if (starts("SET_PRESET:")) {
        std::string preset = cmd.substr(strlen("SET_PRESET:"));
        LOGI("SET_PRESET recibido: %s (mapeo de presets a parámetros Ω_in pendiente)",
             preset.c_str());
    } else if (starts("GET_TELEMETRY")) {
        // La respuesta la escribe socket_thread tras llamar a esta función.
    } else {
        LOGI("Comando no reconocido: %s", cmd.c_str());
    }
}

std::string buildTelemetryResponse() {
    float temp = g_shared ? g_shared->current_temperature.load() : 0.0f;
    float lat  = g_shared ? g_shared->current_latency_ms.load() : 0.0f;
    int level  = g_complexity_level.load(std::memory_order_relaxed);
    // npu_usage real requiere leer contadores del driver Hexagon (no
    // disponible sin el runtime de ExecuTorch enlazado todavía) -> se
    // reporta 0 explícitamente en vez de un número inventado.
    char buf[160];
    snprintf(buf, sizeof(buf),
             "{\"temp\":%.1f,\"npu\":0.0,\"latency\":%.2f,\"complexity_level\":%d}\n",
             temp, lat, level);
    return std::string(buf);
}

void socket_server_thread() {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) { LOGE("socket() falló"); return; }

    struct sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    // Namespace abstracto: primer byte de sun_path en '\0', el resto es
    // el nombre (coincide con LocalSocketAddress.Namespace.ABSTRACT de
    // Android, que internamente hace lo mismo).
    addr.sun_path[0] = '\0';
    std::strncpy(addr.sun_path + 1, kSocketName, sizeof(addr.sun_path) - 2);
    socklen_t addrlen = sizeof(addr.sun_family) + 1 + strlen(kSocketName);

    if (bind(fd, reinterpret_cast<sockaddr*>(&addr), addrlen) < 0) {
        LOGE("bind() falló en socket abstracto '%s' (errno=%d)", kSocketName, errno);
        close(fd);
        return;
    }
    if (listen(fd, 4) < 0) {
        LOGE("listen() falló");
        close(fd);
        return;
    }

    g_socket_fd = fd;
    LOGI("Unix Domain Socket escuchando en '%s' (namespace abstracto)", kSocketName);

    while (g_running.load()) {
        int client = accept(fd, nullptr, nullptr);
        if (client < 0) {
            if (!g_running.load()) break;
            continue;
        }

        char line[256];
        while (g_running.load()) {
            ssize_t n = read(client, line, sizeof(line) - 1);
            if (n <= 0) break;
            line[n] = '\0';
            std::string cmd(line);
            // Quitar salto de línea final si lo hay (println de Kotlin lo añade).
            while (!cmd.empty() && (cmd.back() == '\n' || cmd.back() == '\r')) cmd.pop_back();

            handleClientCommand(cmd);

            if (cmd.rfind("GET_TELEMETRY", 0) == 0) {
                std::string resp = buildTelemetryResponse();
                write(client, resp.c_str(), resp.size());
            }
        }
        close(client);
    }

    close(fd);
    g_socket_fd = -1;
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeStart(JNIEnv*, jobject) {
    if (g_running.load()) {
        LOGI("Daemon ya está corriendo");
        return JNI_TRUE;
    }
    if (!init_shared_memory()) {
        return JNI_FALSE;
    }

    // Intento de carga de modelo (no fatal si no existe: ver loadModel,
    // hace passthrough explícito mientras no haya .pte real exportado).
    loadModel("/data/local/tmp/omega_engine_fp16.pte");

    g_running.store(true);
    g_process_thread = std::thread(process_audio_thread);
    g_socket_thread  = std::thread(socket_server_thread);

    LOGI("Daemon Omega iniciado (ring buffer SPSC + socket + afinidad CPU)");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeStop(JNIEnv*, jobject) {
    if (!g_running.load()) return;

    g_running.store(false);

    // Desbloquear accept() pendiente cerrando el socket de escucha.
    if (g_socket_fd >= 0) {
        shutdown(g_socket_fd, SHUT_RDWR);
    }

    if (g_process_thread.joinable()) g_process_thread.join();
    if (g_socket_thread.joinable())  g_socket_thread.join();

    cleanup_shared_memory();
    LOGI("Daemon Omega detenido");
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeSetProcessing(JNIEnv*, jobject, jboolean active) {
    if (g_shared != nullptr) g_shared->is_processing.store(active);
}

JNIEXPORT void JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeSetIntensity(JNIEnv*, jobject, jfloat intensity) {
    if (g_shared != nullptr) g_shared->intensity.store(intensity);
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeGetTemperature(JNIEnv*, jobject) {
    return g_shared ? g_shared->current_temperature.load() : 0.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_ivannafusion_OmegaDaemon_nativeGetLatency(JNIEnv*, jobject) {
    return g_shared ? g_shared->current_latency_ms.load() : 0.0f;
}

} // extern "C"

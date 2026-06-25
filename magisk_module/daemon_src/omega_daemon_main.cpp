/*
 * IVANNA-FUSION / Ω_in
 * omega_daemon_main.cpp — Ejecutable standalone para módulo Magisk
 *
 * CAMBIO CRÍTICO: shm via memfd_create + SCM_RIGHTS
 * ─────────────────────────────────────────────────
 * La versión anterior usaba open("/data/local/tmp/omega_shared_mem") para
 * crear la memoria compartida. audioserver (UID=1041) tiene política SELinux
 * que prohíbe acceso a shell_data_file (/data/local/tmp), por lo que el
 * efecto nunca podía mapear la shm → SIGSEGV o bypass permanente.
 *
 * Solución: memfd_create() crea un fd anónimo sin path en filesystem.
 * El fd se pasa a cada cliente que conecte via SCM_RIGHTS (ancillary data
 * del socket abstracto Unix). audioserver puede recibir un fd pasado
 * via socket sin ninguna restricción SELinux adicional.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/syscall.h>
#include <atomic>
#include <thread>
#include <chrono>
#include <signal.h>
#include <sched.h>

#include "omega_shared.h"

// memfd_create via syscall — seguro desde API 28 (kernel 4.4+)
#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 1U
#endif
#ifndef MFD_ALLOW_SEALING
#define MFD_ALLOW_SEALING 2U
#endif
static int memfd_create_compat(const char* name, unsigned int flags) {
    return (int)syscall(__NR_memfd_create, name, flags);
}

#define SOCKET_NAME      "omega_daemon_socket"
#define THERMAL_LIMIT_C  42.0f

static OmegaSharedState*   g_shared    = nullptr;
static int                 g_shm_fd    = -1;  // memfd — se pasa via SCM_RIGHTS
static std::atomic<bool>   g_running{false};
static std::thread         g_process_thread;
static std::thread         g_socket_thread;
static int                 g_socket_fd = -1;
static float               g_process_buf[OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS];
static std::atomic<int>    g_complexity_level{0};

static void signal_handler(int /*sig*/) { g_running.store(false); }

// ── Térmica ──────────────────────────────────────────────────────────────────
static float read_battery_temperature() {
    FILE* f = fopen("/sys/class/power_supply/battery/temp", "r");
    if (!f) return g_shared ? g_shared->current_temperature.load() : 35.0f;
    int raw = 0;
    if (fscanf(f, "%d", &raw) != 1) { fclose(f); return 35.0f; }
    fclose(f);
    return raw / 10.0f;
}

static void update_thermal_state() {
    float temp = read_battery_temperature();
    if (g_shared) g_shared->current_temperature.store(temp);
    int level = (temp >= THERMAL_LIMIT_C + 5.0f) ? 2 : (temp >= THERMAL_LIMIT_C) ? 1 : 0;
    g_complexity_level.store(level);
}

// ── Memoria compartida (memfd, sin path filesystem) ──────────────────────────
static bool init_shared_memory() {
    g_shm_fd = memfd_create_compat("omega_fusion_shm", MFD_CLOEXEC | MFD_ALLOW_SEALING);
    if (g_shm_fd < 0) {
        fprintf(stderr, "[omega_daemon] memfd_create falló (errno=%d)\n", errno);
        return false;
    }
    if (ftruncate(g_shm_fd, (off_t)sizeof(OmegaSharedState)) < 0) {
        fprintf(stderr, "[omega_daemon] ftruncate falló\n");
        close(g_shm_fd); g_shm_fd = -1;
        return false;
    }
    void* mapped = mmap(nullptr, sizeof(OmegaSharedState),
                        PROT_READ | PROT_WRITE, MAP_SHARED, g_shm_fd, 0);
    if (mapped == MAP_FAILED) {
        fprintf(stderr, "[omega_daemon] mmap falló\n");
        close(g_shm_fd); g_shm_fd = -1;
        return false;
    }
    g_shared = static_cast<OmegaSharedState*>(mapped);
    new (g_shared) OmegaSharedState();
    printf("[omega_daemon] Memoria compartida lista (memfd=%d, size=%zu)\n",
           g_shm_fd, sizeof(OmegaSharedState));
    return true;
}

static void cleanup_shared_memory() {
    if (g_shared) { munmap(g_shared, sizeof(OmegaSharedState)); g_shared = nullptr; }
    if (g_shm_fd >= 0) { close(g_shm_fd); g_shm_fd = -1; }
}

// ── SCM_RIGHTS: enviar fd al cliente recién conectado ────────────────────────
static bool send_shm_fd(int client_fd) {
    char  data   = 0;            // byte carrier (ancillary data necesita >=1 byte de datos)
    struct iovec iov  = { &data, 1 };
    char cmsgbuf[CMSG_SPACE(sizeof(int))];
    struct msghdr msg = {};
    msg.msg_iov        = &iov;
    msg.msg_iovlen     = 1;
    msg.msg_control    = cmsgbuf;
    msg.msg_controllen = sizeof(cmsgbuf);
    struct cmsghdr* cmsg = CMSG_FIRSTHDR(&msg);
    cmsg->cmsg_level = SOL_SOCKET;
    cmsg->cmsg_type  = SCM_RIGHTS;
    cmsg->cmsg_len   = CMSG_LEN(sizeof(int));
    memcpy(CMSG_DATA(cmsg), &g_shm_fd, sizeof(int));
    ssize_t n = sendmsg(client_fd, &msg, 0);
    if (n < 0) fprintf(stderr, "[omega_daemon] sendmsg SCM_RIGHTS falló (errno=%d)\n", errno);
    return n >= 0;
}

// ── Inferencia (passthrough hasta que haya .pte real) ────────────────────────
static void run_inference(const float* in, float* out, int n) {
    if (g_complexity_level.load() >= 2) { memcpy(out, in, n * sizeof(float)); return; }
    memcpy(out, in, n * sizeof(float));
}

// ── Bucle de audio ───────────────────────────────────────────────────────────
static void process_audio_thread() {
    // SM4450 (Snapdragon 4 Gen 2): big cores = A78 en slots 4-7
    cpu_set_t cpuset; CPU_ZERO(&cpuset);
    for (int c = 4; c <= 7; ++c) CPU_SET(c, &cpuset);
    if (sched_setaffinity(0, sizeof(cpuset), &cpuset) != 0)
        fprintf(stderr, "[omega_daemon] sched_setaffinity falló (errno=%d)\n", errno);

    printf("[omega_daemon] Hilo de audio iniciado (cores 4-7)\n");
    const int samples = OMEGA_BLOCK_SIZE * OMEGA_MAX_CHANNELS;
    int thermal_counter = 0;

    while (g_running.load()) {
        if (!g_shared->is_processing.load() || g_shared->bypass_enabled.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(5));
            continue;
        }
        if (++thermal_counter >= 200) { thermal_counter = 0; update_thermal_state(); }

        if (!g_shared->ring_in.tryPop(g_process_buf, samples, &g_shared->input_buffer[0][0])) {
            std::this_thread::sleep_for(std::chrono::microseconds(500));
            continue;
        }
        auto t0 = std::chrono::steady_clock::now();
        run_inference(g_process_buf, g_process_buf, samples);
        auto t1 = std::chrono::steady_clock::now();
        g_shared->current_latency_ms.store(
            std::chrono::duration<float, std::milli>(t1 - t0).count());
        g_shared->ring_out.tryPush(g_process_buf, samples, &g_shared->output_buffer[0][0]);
    }
    printf("[omega_daemon] Hilo de audio detenido\n");
}

// ── Comandos del socket ───────────────────────────────────────────────────────
static void handle_command(const std::string& cmd, int client_fd) {
    auto starts = [&](const char* p) { return cmd.rfind(p, 0) == 0; };

    if      (starts("SET_PROCESSING:"))  { if (g_shared) g_shared->is_processing.store(cmd.back()=='1'); }
    else if (starts("SET_INTENSITY:"))   { if (g_shared) g_shared->intensity.store(strtof(cmd.c_str()+14,nullptr)); }
    else if (starts("SET_VOCODER_MIX:")) { if (g_shared) g_shared->vocoder_mix.store(strtof(cmd.c_str()+16,nullptr)); }
    else if (starts("SET_BYPASS:"))      { if (g_shared) g_shared->bypass_enabled.store(cmd.back()=='1'); }
    else if (starts("SET_THERMAL_THROTTLE:")) { if (cmd.back()=='1') g_complexity_level.store(2); }
    else if (starts("RESET_DEFAULTS"))   {
        if (g_shared) { g_shared->intensity.store(0.8f); g_shared->vocoder_mix.store(0.8f); g_shared->bypass_enabled.store(false); }
    }
    else if (starts("SET_PRESET:"))      { printf("[omega_daemon] Preset: %s\n", cmd.c_str()+11); }
    else if (starts("GET_TELEMETRY")) {
        float temp = g_shared ? g_shared->current_temperature.load() : 0.0f;
        float lat  = g_shared ? g_shared->current_latency_ms.load()  : 0.0f;
        char buf[160];
        snprintf(buf, sizeof(buf),
                 "{\"temp\":%.1f,\"npu\":0.0,\"latency\":%.2f,\"complexity_level\":%d}\n",
                 temp, lat, g_complexity_level.load());
        write(client_fd, buf, strlen(buf));
        return;
    }
    const char* ack = "OK\n";
    write(client_fd, ack, 3);
}

// ── Servidor de socket ────────────────────────────────────────────────────────
static void socket_server_thread() {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) { fprintf(stderr, "[omega_daemon] socket() falló\n"); return; }

    struct sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, SOCKET_NAME, sizeof(addr.sun_path) - 2);
    socklen_t addrlen = (socklen_t)(sizeof(addr.sun_family) + 1 + strlen(SOCKET_NAME));

    if (bind(fd, reinterpret_cast<sockaddr*>(&addr), addrlen) < 0) {
        fprintf(stderr, "[omega_daemon] bind() falló en '%s' (errno=%d)\n", SOCKET_NAME, errno);
        close(fd); return;
    }
    if (listen(fd, 8) < 0) { fprintf(stderr, "[omega_daemon] listen() falló\n"); close(fd); return; }

    g_socket_fd = fd;
    printf("[omega_daemon] Escuchando en socket abstracto '%s'\n", SOCKET_NAME);

    while (g_running.load()) {
        int client = accept(fd, nullptr, nullptr);
        if (client < 0) { if (!g_running.load()) break; continue; }

        // Primer mensaje al cliente: fd de shm via SCM_RIGHTS.
        // El efecto (audioserver) hará recvmsg para obtenerlo y mapearlo.
        // La APK simplemente lee/descarta el byte carrier con read() y
        // luego envía comandos normalmente.
        send_shm_fd(client);

        char line[256];
        while (g_running.load()) {
            ssize_t n = read(client, line, sizeof(line) - 1);
            if (n <= 0) break;
            line[n] = '\0';
            std::string cmd(line);
            while (!cmd.empty() && (cmd.back()=='\n'||cmd.back()=='\r')) cmd.pop_back();
            if (!cmd.empty()) handle_command(cmd, client);
        }
        close(client);
    }
    close(fd); g_socket_fd = -1;
}

// ── main ─────────────────────────────────────────────────────────────────────
int main(int /*argc*/, char** /*argv*/) {
    printf("[omega_daemon] Ω_in Edge AI Daemon arrancando (PID=%d)...\n", getpid());
    signal(SIGINT,  signal_handler);
    signal(SIGTERM, signal_handler);

    if (!init_shared_memory()) return 1;

    g_running.store(true);
    g_process_thread = std::thread(process_audio_thread);
    g_socket_thread  = std::thread(socket_server_thread);

    while (g_running.load())
        std::this_thread::sleep_for(std::chrono::seconds(1));

    printf("[omega_daemon] Apagando...\n");
    if (g_socket_fd >= 0) shutdown(g_socket_fd, SHUT_RDWR);
    if (g_process_thread.joinable()) g_process_thread.join();
    if (g_socket_thread.joinable())  g_socket_thread.join();
    cleanup_shared_memory();
    printf("[omega_daemon] Daemon detenido\n");
    return 0;
}

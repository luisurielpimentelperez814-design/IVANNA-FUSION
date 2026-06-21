#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <pthread.h>
#include <time.h>

#define SOCKET_NAME "omega_daemon_socket"
#define SHM_NAME "/omega_telemetry"
#define BUFFER_SIZE 1024
#define MAX_CLIENTS 5

typedef struct {
    float temp;
    float npu;
    float latency;
    int complexity_level;
    int processing_enabled;
    int intensity;
    float vocoder_mix;
} SharedTelemetry;

typedef struct {
    int client_fds[MAX_CLIENTS];
    pthread_mutex_t mutex;
    SharedTelemetry* shm;
    int running;
} DaemonContext;

static DaemonContext ctx;

// Leer temperatura real del SoC
float read_cpu_temp(void) {
    FILE* f = fopen("/sys/class/thermal/thermal_zone0/temp", "r");
    if (!f) return 35.0f; // Valor por defecto si no se puede leer
    int temp;
    if (fscanf(f, "%d", &temp) == 1) {
        fclose(f);
        return temp / 1000.0f;
    }
    fclose(f);
    return 35.0f;
}

// Leer uso de GPU/NPU
float read_npu_usage(void) {
    // Intentar leer GPU (Qualcomm)
    FILE* f = fopen("/sys/class/devfreq/soc:qcom,kgsl-3d0/gpu_busy_percent", "r");
    if (f) {
        int usage;
        if (fscanf(f, "%d", &usage) == 1) {
            fclose(f);
            return (float)usage;
        }
        fclose(f);
    }
    
    // Intentar alternativa (MediaTek)
    f = fopen("/sys/class/misc/mali0/device/utilization", "r");
    if (f) {
        int usage;
        if (fscanf(f, "%d", &usage) == 1) {
            fclose(f);
            return (float)usage;
        }
        fclose(f);
    }
    
    return 0.0f;
}

// Calcular latencia simulada basada en carga
float calculate_latency(int processing_enabled, int intensity) {
    if (!processing_enabled) return 0.0f;
    
    // Latencia base + variación por intensidad
    float base_latency = 1.5f;
    float intensity_factor = (intensity / 100.0f) * 3.0f;
    float noise = ((float)(rand() % 100) / 100.0f) * 0.5f;
    
    return base_latency + intensity_factor + noise;
}

// Determinar nivel de complejidad
int calculate_complexity_level(int intensity) {
    if (intensity < 20) return 1;
    if (intensity < 40) return 2;
    if (intensity < 60) return 3;
    if (intensity < 80) return 4;
    return 5;
}

// Actualizar telemetría con datos reales
void update_telemetry(void) {
    pthread_mutex_lock(&ctx.mutex);
    
    ctx.shm->temp = read_cpu_temp();
    
    if (ctx.shm->processing_enabled) {
        ctx.shm->npu = read_npu_usage();
        ctx.shm->latency = calculate_latency(1, ctx.shm->intensity);
        ctx.shm->complexity_level = calculate_complexity_level(ctx.shm->intensity);
    } else {
        ctx.shm->npu = 0.0f;
        ctx.shm->latency = 0.0f;
        ctx.shm->complexity_level = 0;
    }
    
    pthread_mutex_unlock(&ctx.mutex);
}

// Hilo de actualización de telemetría
void* telemetry_thread(void* arg) {
    while (ctx.running) {
        update_telemetry();
        usleep(500000); // Actualizar cada 500ms
    }
    return NULL;
}

// Inicializar memoria compartida
int init_shared_memory(void) {
    int fd = shm_open(SHM_NAME, O_CREAT | O_RDWR, 0666);
    if (fd < 0) {
        perror("shm_open failed");
        return -1;
    }
    
    ftruncate(fd, sizeof(SharedTelemetry));
    ctx.shm = mmap(NULL, sizeof(SharedTelemetry), PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    close(fd);
    
    if (ctx.shm == MAP_FAILED) {
        perror("mmap failed");
        return -1;
    }
    
    // Inicializar valores
    ctx.shm->temp = 35.0f;
    ctx.shm->npu = 0.0f;
    ctx.shm->latency = 0.0f;
    ctx.shm->complexity_level = 0;
    ctx.shm->processing_enabled = 0;
    ctx.shm->intensity = 50;
    ctx.shm->vocoder_mix = 0.5f;
    
    return 0;
}

// Procesar comando del cliente
void process_command(int client_fd, const char* cmd) {
    char response[BUFFER_SIZE];
    
    if (strncmp(cmd, "GET_TELEMETRY", 13) == 0) {
        pthread_mutex_lock(&ctx.mutex);
        snprintf(response, sizeof(response),
                 "{\"temp\":%.1f,\"npu\":%.1f,\"latency\":%.2f,\"complexity_level\":%d}",
                 ctx.shm->temp, ctx.shm->npu, ctx.shm->latency, ctx.shm->complexity_level);
        pthread_mutex_unlock(&ctx.mutex);
    }
    else if (strncmp(cmd, "SET_PROCESSING", 14) == 0) {
        int enabled = atoi(cmd + 15);
        pthread_mutex_lock(&ctx.mutex);
        ctx.shm->processing_enabled = enabled;
        pthread_mutex_unlock(&ctx.mutex);
        snprintf(response, sizeof(response), "{\"status\":\"ok\",\"processing\":%d}", enabled);
    }
    else if (strncmp(cmd, "SET_INTENSITY", 13) == 0) {
        int level = atoi(cmd + 14);
        if (level < 0) level = 0;
        if (level > 100) level = 100;
        pthread_mutex_lock(&ctx.mutex);
        ctx.shm->intensity = level;
        pthread_mutex_unlock(&ctx.mutex);
        snprintf(response, sizeof(response), "{\"status\":\"ok\",\"intensity\":%d}", level);
    }
    else if (strncmp(cmd, "SET_VOCODER_MIX", 15) == 0) {
        float mix = atof(cmd + 16);
        if (mix < 0.0f) mix = 0.0f;
        if (mix > 1.0f) mix = 1.0f;
        pthread_mutex_lock(&ctx.mutex);
        ctx.shm->vocoder_mix = mix;
        pthread_mutex_unlock(&ctx.mutex);
        snprintf(response, sizeof(response), "{\"status\":\"ok\",\"vocoder_mix\":%.2f}", mix);
    }
    else if (strncmp(cmd, "RESET_DEFAULTS", 14) == 0) {
        pthread_mutex_lock(&ctx.mutex);
        ctx.shm->processing_enabled = 0;
        ctx.shm->intensity = 50;
        ctx.shm->vocoder_mix = 0.5f;
        pthread_mutex_unlock(&ctx.mutex);
        snprintf(response, sizeof(response), "{\"status\":\"ok\",\"reset\":true}");
    }
    else {
        snprintf(response, sizeof(response), "{\"error\":\"unknown command\"}");
    }
    
    write(client_fd, response, strlen(response));
}

// Hilo de cliente
void* client_handler(void* arg) {
    int client_fd = *(int*)arg;
    char buffer[BUFFER_SIZE];
    ssize_t len;
    
    while ((len = read(client_fd, buffer, sizeof(buffer) - 1)) > 0) {
        buffer[len] = '\0';
        // Eliminar nueva línea
        char* newline = strchr(buffer, '\n');
        if (newline) *newline = '\0';
        
        process_command(client_fd, buffer);
    }
    
    close(client_fd);
    return NULL;
}

// Manejador de señales
void signal_handler(int sig) {
    ctx.running = 0;
    shm_unlink(SHM_NAME);
    exit(0);
}

int main(void) {
    int server_fd, client_fd;
    struct sockaddr_un addr;
    pthread_t telemetry_tid;
    
    printf("Ω_in Edge AI Audio Engine Daemon starting...\n");
    
    // Configurar manejador de señales
    signal(SIGINT, signal_handler);
    signal(SIGTERM, signal_handler);
    
    // Inicializar contexto
    memset(&ctx, 0, sizeof(ctx));
    pthread_mutex_init(&ctx.mutex, NULL);
    ctx.running = 1;
    
    // Inicializar memoria compartida
    if (init_shared_memory() < 0) {
        return 1;
    }
    printf("Shared memory initialized\n");
    
    // Crear socket
    server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd < 0) {
        perror("socket failed");
        return 1;
    }
    
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path + 1, SOCKET_NAME, sizeof(addr.sun_path) - 2);
    
    if (bind(server_fd, (struct sockaddr*)&addr, 
             sizeof(sa_family_t) + strlen(SOCKET_NAME) + 1) < 0) {
        perror("bind failed");
        close(server_fd);
        return 1;
    }
    
    if (listen(server_fd, MAX_CLIENTS) < 0) {
        perror("listen failed");
        close(server_fd);
        return 1;
    }
    
    printf("Daemon running (PID: %d)\n", getpid());
    printf("Unix Domain Socket listening on '%s'\n", SOCKET_NAME);
    
    // Iniciar hilo de telemetría
    pthread_create(&telemetry_tid, NULL, telemetry_thread, NULL);
    printf("Telemetry thread started\n");
    
    // Bucle principal
    while (ctx.running) {
        client_fd = accept(server_fd, NULL, NULL);
        if (client_fd < 0) {
            if (ctx.running) perror("accept failed");
            continue;
        }
        
        pthread_t client_tid;
        pthread_create(&client_tid, NULL, client_handler, &client_fd);
        pthread_detach(client_tid);
    }
    
    // Limpieza
    close(server_fd);
    pthread_join(telemetry_tid, NULL);
    shm_unlink(SHM_NAME);
    
    return 0;
}

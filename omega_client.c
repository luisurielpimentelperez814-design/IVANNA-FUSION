#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <errno.h>

#define SOCKET_NAME "omega_daemon_socket"
#define BUFFER_SIZE 1024

typedef struct {
    float temp;
    float npu;
    float latency;
    int complexity_level;
} Telemetry;

int connect_to_daemon(void) {
    int sock;
    struct sockaddr_un addr;
    
    sock = socket(AF_UNIX, SOCK_STREAM, 0);
    if (sock < 0) {
        perror("Error creando socket");
        return -1;
    }
    
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path + 1, SOCKET_NAME, sizeof(addr.sun_path) - 2);
    
    if (connect(sock, (struct sockaddr*)&addr, 
                sizeof(sa_family_t) + strlen(SOCKET_NAME) + 1) < 0) {
        perror("Error conectando al daemon");
        close(sock);
        return -1;
    }
    
    return sock;
}

int send_command(int sock, const char* cmd, char* response, size_t resp_size) {
    ssize_t len;
    
    if (write(sock, cmd, strlen(cmd)) < 0) {
        perror("Error enviando comando");
        return -1;
    }
    
    len = read(sock, response, resp_size - 1);
    if (len < 0) {
        perror("Error leyendo respuesta");
        return -1;
    }
    
    response[len] = '\0';
    return 0;
}

Telemetry get_telemetry(int sock) {
    char response[BUFFER_SIZE];
    Telemetry tel = {0};
    
    if (send_command(sock, "GET_TELEMETRY", response, sizeof(response)) == 0) {
        sscanf(response, "{\"temp\":%f,\"npu\":%f,\"latency\":%f,\"complexity_level\":%d}",
               &tel.temp, &tel.npu, &tel.latency, &tel.complexity_level);
    }
    
    return tel;
}

void set_processing(int sock, int enabled) {
    char cmd[32];
    char response[BUFFER_SIZE];
    snprintf(cmd, sizeof(cmd), "SET_PROCESSING %d", enabled);
    send_command(sock, cmd, response, sizeof(response));
}

void set_intensity(int sock, int level) {
    char cmd[32];
    char response[BUFFER_SIZE];
    snprintf(cmd, sizeof(cmd), "SET_INTENSITY %d", level);
    send_command(sock, cmd, response, sizeof(response));
}

void set_vocoder_mix(int sock, float mix) {
    char cmd[32];
    char response[BUFFER_SIZE];
    snprintf(cmd, sizeof(cmd), "SET_VOCODER_MIX %.2f", mix);
    send_command(sock, cmd, response, sizeof(response));
}

void reset_defaults(int sock) {
    char response[BUFFER_SIZE];
    send_command(sock, "RESET_DEFAULTS", response, sizeof(response));
}

int main(int argc, char* argv[]) {
    int sock = connect_to_daemon();
    if (sock < 0) {
        return 1;
    }
    
    printf("Conectado al daemon omega_daemon\n");
    
    if (argc > 1) {
        if (strcmp(argv[1], "telemetry") == 0) {
            Telemetry tel = get_telemetry(sock);
            printf("Temp: %.1fC | NPU: %.1f%% | Latency: %.2fms | Level: %d\n",
                   tel.temp, tel.npu, tel.latency, tel.complexity_level);
        }
        else if (strcmp(argv[1], "processing") == 0 && argc > 2) {
            set_processing(sock, atoi(argv[2]));
            printf("Processing %s\n", atoi(argv[2]) ? "activado" : "desactivado");
        }
        else if (strcmp(argv[1], "intensity") == 0 && argc > 2) {
            set_intensity(sock, atoi(argv[2]));
            printf("Intensidad: %d\n", atoi(argv[2]));
        }
        else if (strcmp(argv[1], "vocoder") == 0 && argc > 2) {
            set_vocoder_mix(sock, atof(argv[2]));
            printf("Vocoder mix: %.2f\n", atof(argv[2]));
        }
        else if (strcmp(argv[1], "reset") == 0) {
            reset_defaults(sock);
            printf("Valores reseteados\n");
        }
        else {
            printf("Comandos disponibles:\n");
            printf("  telemetry              - Ver telemetria\n");
            printf("  processing <0|1>       - Activar/desactivar\n");
            printf("  intensity <0-100>      - Nivel de intensidad\n");
            printf("  vocoder <0.0-1.0>      - Mix vocoder\n");
            printf("  reset                  - Resetear valores\n");
        }
    }
    else {
        Telemetry tel = get_telemetry(sock);
        printf("Telemetria: Temp=%.1fC NPU=%.1f%% Latency=%.2fms Level=%d\n",
               tel.temp, tel.npu, tel.latency, tel.complexity_level);
    }
    
    close(sock);
    return 0;
}

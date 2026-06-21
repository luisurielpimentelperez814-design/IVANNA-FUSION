#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

int main(int argc, char** argv) {
    if (argc < 2) {
        printf("Uso: %s COMANDO\n", argv[0]);
        return 1;
    }
    
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    struct sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, "omega_daemon_socket", sizeof(addr.sun_path) - 2);
    
    socklen_t addrlen = sizeof(addr.sun_family) + 1 + strlen("omega_daemon_socket");
    
    if (connect(fd, (sockaddr*)&addr, addrlen) < 0) {
        perror("connect");
        return 1;
    }
    
    char cmd[256];
    snprintf(cmd, sizeof(cmd), "%s\n", argv[1]);
    write(fd, cmd, strlen(cmd));
    
    char buf[256];
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    if (n > 0) {
        buf[n] = '\0';
        printf("Response: %s", buf);
    }
    
    close(fd);
    return 0;
}

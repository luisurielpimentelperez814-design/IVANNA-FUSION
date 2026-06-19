#include "../core/pf_engine.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <pthread.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>

#define BUF_SIZE   512
#define BACKLOG    8

static volatile int g_running = 1;
static int          g_sock_fd = -1;

static void handle_sig(int s) {
    (void)s;
    g_running = 0;
    if (g_sock_fd >= 0) close(g_sock_fd);
}

static void log_msg(const char *msg) {
    FILE *f = fopen(PF_LOG_PATH, "a");
    if (!f) return;
    fprintf(f, "[pf-daemon] %s\n", msg);
    fclose(f);
}

/* ── Client handler thread ──────────────────────────────────────────── */
static void *client_thread(void *arg) {
    int fd  = *(int *)arg;
    free(arg);
    char buf[BUF_SIZE];

    while (1) {
        ssize_t n = recv(fd, buf, BUF_SIZE-1, 0);
        if (n <= 0) break;
        buf[n] = '\0';

        /* strip trailing newline */
        char *nl = strchr(buf, '\n');
        if (nl) *nl = '\0';

        if (strcmp(buf, "quit") == 0) {
            send(fd, "OK:quit\n", 8, 0);
            break;
        }
        if (strcmp(buf, "status") == 0) {
            PFEngineState *st = pf_get_state();
            char resp[256];
            snprintf(resp, sizeof(resp),
                "bar=%u;amp=%d;drive=%.3f;wet=%.3f;alpha=%.3f;"
                "delta=%.3f;beta=%.3f\n",
                st->bar,
                (int)st->master.amp,
                st->master.drive,
                st->master.wet,
                st->master.alpha,
                st->master.delta,
                st->master.beta);
            send(fd, resp, strlen(resp), 0);
            continue;
        }
        if (strncmp(buf, "load:", 5) == 0) {
            int r = pf_load_preset(buf+5);
            char resp[64];
            snprintf(resp, sizeof(resp), r==0 ? "OK:loaded:%s\n" : "ERR:load\n", buf+5);
            send(fd, resp, strlen(resp), 0);
            continue;
        }
        if (strncmp(buf, "save:", 5) == 0) {
            int r = pf_save_preset(buf+5);
            char resp[64];
            snprintf(resp, sizeof(resp), r==0 ? "OK:saved:%s\n" : "ERR:save\n", buf+5);
            send(fd, resp, strlen(resp), 0);
            continue;
        }
        if (strncmp(buf, "amp:", 4) == 0) {
            int model = atoi(buf+4);
            pf_set_amp((PFAmpModel)model);
            send(fd, "OK:amp\n", 7, 0);
            continue;
        }
        if (strncmp(buf, "bar:", 4) == 0) {
            pf_advance_bar();
            send(fd, "OK:bar\n", 7, 0);
            continue;
        }

        /* Default: parameter command  alpha=1.2;drive=2.0;wet=0.7 */
        int r = pf_parse_command(buf);
        if (r == 0) {
            send(fd, "OK\n", 3, 0);
            log_msg(buf);
        } else {
            send(fd, "ERR:parse\n", 10, 0);
        }
    }
    close(fd);
    return NULL;
}

/* ── Main daemon loop ───────────────────────────────────────────────── */
int main(int argc, char **argv) {
    (void)argc; (void)argv;

    signal(SIGTERM, handle_sig);
    signal(SIGINT,  handle_sig);
    signal(SIGPIPE, SIG_IGN);

    /* ensure /data/pf exists */
    mkdir("/data/pf",          0755);
    mkdir(PF_PRESET_DIR,       0755);

    pf_init(48000, 256);
    log_msg("pf-daemon v" PF_VERSION " starting");

    /* create UNIX domain socket */
    unlink(PF_SOCKET_PATH);
    g_sock_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (g_sock_fd < 0) { perror("socket"); return 1; }

    struct sockaddr_un addr = {};
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, PF_SOCKET_PATH, sizeof(addr.sun_path)-1);

    if (bind(g_sock_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        perror("bind"); return 1;
    }
    chmod(PF_SOCKET_PATH, 0660);
    listen(g_sock_fd, BACKLOG);
    log_msg("socket ready at " PF_SOCKET_PATH);

    while (g_running) {
        int *cfd = (int*)malloc(sizeof(int));
        if (!cfd) continue;
        *cfd = accept(g_sock_fd, NULL, NULL);
        if (*cfd < 0) { free(cfd); continue; }
        pthread_t tid;
        pthread_create(&tid, NULL, client_thread, cfd);
        pthread_detach(tid);
    }

    pf_shutdown();
    unlink(PF_SOCKET_PATH);
    log_msg("pf-daemon stopped");
    return 0;
}

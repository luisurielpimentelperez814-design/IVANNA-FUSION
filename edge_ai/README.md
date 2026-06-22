# Omega_in Mobile Edge AI Engine

## Architecture (3 Layers)
- **Layer 1: Audio Effect Plugin** (`libomega_effect.so`) - Intercepts audio from AudioFlinger via lock-free SPSC ring buffer (shared memory)
- **Layer 2: Root Daemon** (`omega_daemon`) - Runs inference via ExecuTorch/QNN Hexagon, thermal governor
- **Layer 3: APK Bridge** (`OmegaMagiskBridge.kt`) - Control via Unix Domain Socket, telemetry via StateFlow

## Target Hardware
- Snapdragon 7s Gen 2 (Cortex-A78 big cores 6-7)
- Hexagon NPU + Adreno 710 GPU
- 8GB RAM | Budget: <50MB total

## Math Approximations (vs original Omega_in)
| Original | Mobile Approximation | Complexity | Error |
|----------|---------------------|------------|-------|
| Sinkhorn (W2) | Sliced Wasserstein Distance | O(N log N) | O(1/sqrt(P)) |
| GNN/TDA Phase | Complex 1D CNN | O(N) | Phase drift |
| HiFi-GAN | HiFi-GAN Tiny (INT8) | O(N) | <2% PESQ |
| Recurrent Denoise | Mamba-Tiny SSM | O(N) | N/A |

## Performance Budget
- Block size: 256 frames @ 48kHz = 5.33ms
- STFT: 2.67ms
- NPU Inference: 6-8ms
- iSTFT: 2.67ms
- **Total: ~11-13ms (<15ms target)**

## Build (NDK r26+)
Use CMake with ANDROID_ABI=arm64-v8a, ANDROID_PLATFORM=android-28.

## Deploy
1. Build with NDK
2. Package as Magisk module (see magisk_module/)
3. Copy omega_in_mobile.pte to /data/adb/omega/
4. Install module via Magisk Manager
5. Reboot

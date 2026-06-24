cmake_minimum_required(VERSION 3.22)
project(omega_daemon)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(omega_daemon
    omega_daemon_main.cpp
)

target_include_directories(omega_daemon PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../app/src/main/cpp
)

# Compilar para Android arm64-v8a
set(CMAKE_SYSTEM_NAME Android)
set(CMAKE_ANDROID_ARCH_ABI arm64-v8a)
set(CMAKE_ANDROID_STANDALONE_TOOLCHAIN ${ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64)
set(CMAKE_ANDROID_API 24)

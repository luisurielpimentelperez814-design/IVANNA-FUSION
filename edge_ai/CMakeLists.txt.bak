cmake_minimum_required(VERSION 3.22)
project(omega_in_mobile CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG -flto")
set(CMAKE_CXX_FLAGS "-Wall -Wextra -Wno-unused-parameter")

# Audio Effect Plugin
add_library(omega_effect SHARED effect/omega_effect.cpp)
target_include_directories(omega_effect PRIVATE ${ANDROID_NDK}/sysroot/usr/include)
target_link_libraries(omega_effect log)

# Root Daemon
add_executable(omega_daemon daemon/omega_daemon.cpp)
target_link_libraries(omega_daemon log)

set_target_properties(omega_effect PROPERTIES OUTPUT_NAME "libomega_effect")

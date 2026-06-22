cmake_minimum_required(VERSION 3.22)
project(pf_engine VERSION 3.0 LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

set(CMAKE_CXX_FLAGS_RELEASE
    "-O3 -ffast-math -march=armv8-a+simd -ftree-vectorize \
     -fno-exceptions -fno-rtti \
     -ffunction-sections -fdata-sections")

set(PF_SOURCES
    core/pf_engine.cpp
    core/pf_evolution.cpp
    dsp/pf_dsp.cpp
    amps/amp_models.cpp
    learning/pf_fft.cpp
    learning/pf_learning.cpp
)

add_library(pf_engine SHARED ${PF_SOURCES})

target_include_directories(pf_engine PRIVATE
    core
    ${CMAKE_SYSROOT}/usr/include
)

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden")

find_library(log-lib log)
target_link_libraries(pf_engine PRIVATE ${log-lib})

set_target_properties(pf_engine PROPERTIES
    LINK_FLAGS "-Wl,--gc-sections -Wl,-soname,libpf_engine.so"
)

message(STATUS "PF-ENGINE-PRO-MAX-NEXT v3.0 — ABI: ${ANDROID_ABI}")

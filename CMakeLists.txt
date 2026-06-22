cmake_minimum_required(VERSION 3.22)
project(ivanna_core VERSION 2.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Options for different targets
option(IVANNA_BUILD_ANDROID "Build Android .so" OFF)
option(IVANNA_BUILD_VST3 "Build VST3 plugin" OFF)
option(IVANNA_BUILD_IOS "Build iOS framework" OFF)
option(IVANNA_BUILD_LINUX "Build Linux library" OFF)
option(IVANNA_BUILD_TESTS "Build tests" ON)

# Core DSP library (HEADER-ONLY INTERFACE)
# All implementations are inline in headers for zero-overhead and SIMD optimization
add_library(ivanna_core INTERFACE)

target_include_directories(ivanna_core INTERFACE
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}>
    $<INSTALL_INTERFACE:include>
)

target_compile_features(ivanna_core INTERFACE cxx_std_20)

# Enable NEON on ARM platforms
if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm|aarch64")
    target_compile_options(ivanna_core INTERFACE -mfpu=neon -mfloat-abi=softfp)
endif()

# API versioning
set_target_properties(ivanna_core PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION ${PROJECT_VERSION_MAJOR})

# Simple test program to validate compilation
if(IVANNA_BUILD_TESTS)
    enable_testing()
    
    add_executable(ivanna_test tests/ivanna_test.cpp)
    target_link_libraries(ivanna_test PRIVATE ivanna_core)
    
    add_test(NAME core_compile_test COMMAND ivanna_test)
    message(STATUS "Tests enabled - ivanna_test will validate core compilation")
endif()

# Platform-specific integrations
if(IVANNA_BUILD_ANDROID)
    message(STATUS "Building Android integration")
    add_library(ivanna_android SHARED
        integration/android/audio_hal_module.cpp
    )
    target_link_libraries(ivanna_android PRIVATE ivanna_core)
endif()

message(STATUS "IVANNA Core v${PROJECT_VERSION} configured successfully (header-only)")

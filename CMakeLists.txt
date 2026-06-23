cmake_minimum_required(VERSION 3.16)
project(IvannaFusion VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# SIMD Detection
include(CheckCXXCompilerFlag)

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64|ARM64|armv8")
    message(STATUS "IVANNA: ARM64 detected. Enabling NEON.")
    add_compile_options(-march=armv8-a+simd)
    add_compile_definitions(IVANNA_SIMD_NEON=1)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64|x86|i386|i686")
    message(STATUS "IVANNA: x86_64 detected.")
    check_cxx_compiler_flag("-mavx2" COMPILER_SUPPORTS_AVX2)
    if(COMPILER_SUPPORTS_AVX2)
        add_compile_options(-mavx2 -mfma)
        add_compile_definitions(IVANNA_SIMD_AVX2=1)
    else()
        add_compile_options(-msse2)
        add_compile_definitions(IVANNA_SIMD_SSE2=1)
    endif()
else()
    add_compile_definitions(IVANNA_SIMD_SCALAR=1)
endif()

# Include directories - both root core/ and nested IVANNA-FUSION/
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/core)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/IVANNA-FUSION/src/cpp)
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/IVANNA-FUSION/pf_engine/core)

# Source files - use real code from nested directory
file(GLOB_RECURSE IVANNA_SOURCES
    "core/*.cpp"
    "IVANNA-FUSION/src/cpp/*.cpp"
    "IVANNA-FUSION/pf_engine/core/*.cpp"
    "IVANNA-FUSION/pf_engine/dsp/*.cpp"
)

# Remove duplicates
list(REMOVE_DUPLICATES IVANNA_SOURCES)

# Library target
if(IVANNA_SOURCES)
    add_library(ivanna_core STATIC ${IVANNA_SOURCES})
    target_include_directories(ivanna_core PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/core>
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/IVANNA-FUSION/src/cpp>
        $<INSTALL_INTERFACE:include>
    )
    message(STATUS "IVANNA: Building with ${CMAKE_CURRENT_LIST_FILE}")
    message(STATUS "IVANNA: Found ${IVANNA_SOURCES} source files")
else()
    message(WARNING "IVANNA: No source files found. Creating empty library.")
    file(WRITE ${CMAKE_CURRENT_BINARY_DIR}/empty.cpp "// Empty stub\n")
    add_library(ivanna_core STATIC ${CMAKE_CURRENT_BINARY_DIR}/empty.cpp)
endif()

message(STATUS "IVANNA-FUSION: ${PROJECT_VERSION} on ${CMAKE_SYSTEM_PROCESSOR}")

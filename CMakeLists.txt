cmake_minimum_required(VERSION 3.16)
project(IvannaFusion VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ==============================================================================
# SIMD DETECTION & OPTIMIZATION (IVANNA-FUSION)
# ==============================================================================
include(CheckCXXCompilerFlag)

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64|ARM64|armv8")
    message(STATUS "IVANNA: ARM64 detected. Enabling NEON.")
    add_compile_options(-march=armv8-a+simd)
    add_compile_definitions(IVANNA_SIMD_NEON=1)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64|x86|i386|i686")
    message(STATUS "IVANNA: x86_64 detected.")
    check_cxx_compiler_flag("-mavx2" COMPILER_SUPPORTS_AVX2)
    if(COMPILER_SUPPORTS_AVX2)
        message(STATUS "IVANNA: Enabling AVX2.")
        add_compile_options(-mavx2 -mfma)
        add_compile_definitions(IVANNA_SIMD_AVX2=1)
    else()
        message(STATUS "IVANNA: Falling back to SSE2.")
        add_compile_options(-msse2)
        add_compile_definitions(IVANNA_SIMD_SSE2=1)
    endif()
else()
    message(WARNING "IVANNA: Unknown architecture. Using scalar fallback.")
    add_compile_definitions(IVANNA_SIMD_SCALAR=1)
endif()

# ==============================================================================
# BUILD OPTIONS
# ==============================================================================
option(IVANNA_BUILD_TESTS "Build unit tests" ON)

# ==============================================================================
# INCLUDE DIRECTORIES
# ==============================================================================
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# ==============================================================================
# SOURCE FILES
# ==============================================================================
file(GLOB_RECURSE IVANNA_SOURCES
    "core/*.cpp"
    "core/*.h"
    "include/*.h"
)

# ==============================================================================
# LIBRARY TARGET
# ==============================================================================
add_library(ivanna_core STATIC ${IVANNA_SOURCES})

target_include_directories(ivanna_core PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include>
)

# ==============================================================================
# TESTS
# ==============================================================================
if(IVANNA_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()

# ==============================================================================
# INSTALL
# ==============================================================================
install(TARGETS ivanna_core
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION bin
)

install(DIRECTORY include/ DESTINATION include)

message(STATUS "IVANNA-FUSION Configuration:")
message(STATUS "  Version: ${PROJECT_VERSION}")
message(STATUS "  Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "  System processor: ${CMAKE_SYSTEM_PROCESSOR}")

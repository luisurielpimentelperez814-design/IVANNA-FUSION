cmake_minimum_required(VERSION 3.22.1)
project("ivannafusion")

add_library(ivanna_native_lib SHARED
    ivanna_native_lib.cpp
)

target_include_directories(ivanna_native_lib PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

find_library(log-lib log)

target_link_libraries(ivanna_native_lib
    ${log-lib}
)

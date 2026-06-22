# IVANNA-FUSION — Tests
cmake_minimum_required(VERSION 3.16)

add_executable(ivanna_test ivanna_test.cpp)

target_include_directories(ivanna_test PRIVATE
    ${CMAKE_SOURCE_DIR}
    ${CMAKE_SOURCE_DIR}/include
)

target_link_libraries(ivanna_test PRIVATE ivanna_core)

set_target_properties(ivanna_test PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

add_test(NAME ivanna_core_compile_test COMMAND ivanna_test)

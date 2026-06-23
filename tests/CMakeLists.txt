add_executable(ivanna_test ivanna_test.cpp)
target_include_directories(ivanna_test PRIVATE
    ${CMAKE_SOURCE_DIR}/core
    ${CMAKE_SOURCE_DIR}/include
)
target_link_libraries(ivanna_test PRIVATE ivanna_core)
target_compile_features(ivanna_test PRIVATE cxx_std_17)
add_test(NAME ivanna_core_test COMMAND ivanna_test)

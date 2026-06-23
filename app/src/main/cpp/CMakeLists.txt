cmake_minimum_required(VERSION 3.18.1)
project("ivanna_jni")

add_library(ivanna_jni SHARED jni_wrapper.cpp)

target_link_libraries(ivanna_jni log android)

cmake_minimum_required(VERSION 3.22.1)
project(nova_engine)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(nova_engine SHARED
    native_engine.cpp
    nova_runtime.cpp
)

target_compile_options(nova_engine PRIVATE
    -Wall
    -Wextra
    -Wpedantic
    -O2
)

target_include_directories(nova_engine PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})

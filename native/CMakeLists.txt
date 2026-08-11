cmake_minimum_required(VERSION 3.22)
project(future2d-native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(future2d SHARED
    src/android_main.cpp
    src/Engine.cpp
    src/GLContext.cpp
    src/ThreadPool.cpp
    src/AssetLoader.cpp
    src/Shader.cpp
    src/Texture.cpp
    src/Renderer.cpp
    src/LuaVM.cpp
    src/Physics.cpp
    src/TextureAtlas.cpp
    src/ResourceManager.cpp
)

target_include_directories(future2d PRIVATE include)

find_library(log-lib log)

find_package(Box2D QUIET)

# Platform-specific libraries: on Android link android/log, EGL and GLES; on host link desktop OpenGL
if (ANDROID)
    set(PLATFORM_LIBS ${log-lib} android EGL GLESv3 GLESv2)
else()
    find_package(OpenGL REQUIRED)
    set(PLATFORM_LIBS ${OPENGL_gl_LIBRARY})
endif()

# Optional Lua shipped in third_party/lua
if (EXISTS "${CMAKE_SOURCE_DIR}/third_party/lua")
    add_subdirectory(third_party/lua)
    set(HAVE_LUA 1)
else()
    set(HAVE_LUA 0)
endif()

target_link_libraries(future2d PRIVATE ${PLATFORM_LIBS})
if (Box2D_FOUND)
    target_include_directories(future2d PRIVATE ${Box2D_INCLUDE_DIRS})
    target_link_libraries(future2d PRIVATE ${Box2D_LIBRARIES})
    message(STATUS "Box2D found and linked")
else()
    message(STATUS "Box2D not found - physics will be stubbed")
endif()

if (HAVE_LUA)
    target_include_directories(future2d PRIVATE ${CMAKE_SOURCE_DIR}/third_party/lua)
    target_link_libraries(future2d PRIVATE lua)
    target_compile_definitions(future2d PRIVATE FUTURE2D_HAS_LUA=1)
else()
    target_compile_definitions(future2d PRIVATE FUTURE2D_HAS_LUA=0)
endif()

target_compile_options(future2d PRIVATE -Wall -Wextra -O2)

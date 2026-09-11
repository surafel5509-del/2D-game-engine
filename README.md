# Nova 2D Engine

A native Android 2D game engine foundation written in Kotlin and C++.

## Features
- Kotlin Android application/editor shell
- C++ native ECS-style world and fixed-step physics
- JNI bridge between Kotlin and C++
- 2D scene, entities, transforms, sprites and colliders
- Camera abstraction and touch input
- Android release build configuration
- GitHub Actions workflow for APK builds

## Build
Open the project in Android Studio with NDK/CMake support, or run `./gradlew assembleDebug`.

The generated debug APK is under `app/build/outputs/apk/debug/`.

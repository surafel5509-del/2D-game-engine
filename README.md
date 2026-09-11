# Nova 2D Engine

A native Android 2D game-engine foundation written in Kotlin and C++.

## Stage 2 — GPU renderer + scene layer
- OpenGL ES 2.0 rendering backend with shader compilation and GPU quad rendering.
- Camera2D viewport/zoom abstraction.
- Continuous render loop with native fixed-step simulation.
- Touch input wired to the native runtime.
- Scene2D/entity/component model for transform, sprite and body data.
- Stable dependency-free scene serialization format designed for Git-friendly project files.
- Native C++ simulation remains isolated behind a small JNI API.

## Build
Open the project in Android Studio with an installed Android SDK and NDK 27.2.12479018, or use the GitHub Actions workflow.

## Roadmap
1. GPU renderer + camera **(implemented)**
2. Texture/asset pipeline and sprite atlas
3. Collision layers + Box2D-style physics
4. Tilemap + animation + particles
5. Audio/resource manager
6. Visual scene editor and inspector
7. Prefabs, scripting and project serialization
8. Debug profiler and runtime console
9. Release APK/AAB pipeline and automated tests

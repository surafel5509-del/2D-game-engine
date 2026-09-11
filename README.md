# Nova 2D Engine

A powerful Android-first 2D game engine/editor written in Kotlin + C++.

## Editor UI — Stage 3
The app now launches directly into a touch-friendly Unity-style 2D editor instead of an empty canvas.

### Editor workspace
- Dark professional editor chrome with top toolbar.
- Scene hierarchy with entity selection.
- 2D viewport with grid, selection outlines and transform tools.
- Inspector panel for Transform, Sprite, Render and Physics properties.
- Bottom console with runtime/editor diagnostics.
- Play / stop controls and editor status.
- In-viewport entity dragging and scene selection.
- Built-in demo scene so a fresh install is never visually empty.

### Engine systems
- OpenGL ES 2.0 GPU rendering foundation.
- Kotlin/C++ JNI runtime boundary.
- Deterministic fixed-step physics.
- Layer/mask AABB collision world.
- Asset registry and project asset model.
- Sprite animation clips and deterministic AnimationPlayer.
- Layered tilemap + painting tools.
- 2D particle emitter/VFX foundation.
- Scene serialization foundation.
- Android NDK/CMake build configuration.
- GitHub Actions debug APK pipeline.

## Roadmap to a production-grade engine
1. **Editor workspace + runtime foundation** — implemented
2. **Assets / animations / tilemap / particles** — implemented foundation
3. Texture loading, atlas packing, batching and shader material system
4. Full Box2D-quality contacts, joints, sensors and character controller
5. Audio mixer, streaming music and spatial SFX
6. Prefabs, scene importer/exporter and project database
7. Visual scripting and gameplay scripting API
8. Timeline, animation state machine and cutscene tools
9. UI layout engine and game UI designer
10. Profiler, debugger, remote inspector and crash reporting
11. Automated tests, release signing and AAB/APK distribution

## Build
Open the project in Android Studio with Android SDK 35 and NDK 27.2.12479018, or use GitHub Actions. The CI workflow builds `app-debug.apk` and uploads it as an Actions artifact.

## Repository
https://github.com/surafel5509-del/2D-game-engine

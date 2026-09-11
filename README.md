# Nova 2D Engine

Android-first, Kotlin + C++ 2D game engine and in-app editor. The project is being built as a real engine architecture rather than a demo canvas.

## Current editor
- Touch-first dark editor workspace
- Scene hierarchy and entity selection
- 2D viewport, grid, move/rotate/scale tools
- Inspector for transform/sprite/render/physics data
- Play / stop state and runtime diagnostics
- Demo scene on first launch
- Asset and animation panels foundation

## Production engine architecture
- Kotlin editor/application layer
- C++ native runtime through JNI
- Fixed-step deterministic simulation
- Layer/mask collision foundation
- Scene graph with parent/child nodes
- Versioned JSON scene serialization
- Undo/redo command system (200 actions)
- Asset index with persistent metadata
- Animation runtime clock and clips
- Multi-layer tilemap data model
- Material/shader configuration model
- Particle emitter configuration
- Input action mapping
- Audio bus/mixer model
- Persistent project settings

## Target production feature set
The architecture is intentionally being expanded toward a complete 2D toolchain: texture importing and atlases, GPU batching, materials/shaders, robust contacts/joints/sensors, character controllers, audio streaming/spatial SFX, prefabs, visual scripting, animation state machines, timeline/cutscenes, UI designer, profiler/debugger, remote inspection, automated tests and signed Android release builds.

## Build
Android SDK 35, NDK 27.2.12479018, Java 17 and Gradle 8.10 are used by CI. GitHub Actions builds the debug APK and publishes it as an Actions artifact after a successful build.

## Status
This repository is an active engine build. It is **not yet honestly labeled production-complete** until the remaining runtime/editor systems and release validation are finished. CI failures are treated as blockers rather than being hidden.

## Repository
https://github.com/surafel5509-del/2D-game-engine

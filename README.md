# Nova 2D Engine

Android-first Kotlin + C++ 2D game engine and in-app editor. Nova is designed as a focused production toolchain: create scenes, author 2D gameplay, manage assets, preview, profile and export Android builds from one project.

## Editor
- Touch-first dark editor workspace
- Scene hierarchy and entity selection
- 2D viewport and grid
- Transform tools and inspector
- Play/stop workflow
- Console and runtime diagnostics

## Engine
- Kotlin editor/application layer
- C++ native runtime through JNI
- Fixed-step deterministic simulation
- Dynamic/static/sensor bodies and collision layers/masks
- Scene graph and versioned scene data
- Undo/redo command history
- Animation clips and timeline interpolation
- Tilemap and particle data systems
- Material/shader configuration
- Input action mapping
- Audio service/mixer foundation

## Production suite
- SHA-256 asset indexing/import records
- Deterministic sprite-atlas packing
- Prefab library
- UI document/layout model and hit testing
- Runtime diagnostics with rolling frame history
- Project export manifest
- Engine self-test suite

## Android release pipeline
GitHub Actions builds:
- Debug APK
- Release APK
- Release AAB

The release pipeline intentionally does not store private signing keys. Configure a production Android keystore through protected CI secrets before publishing to an app store.

## Quality status
The repository contains the production architecture and release pipeline, but it is not falsely labeled as a 100% Unity-equivalent engine. Store-ready status still requires successful CI, device validation, native ABI checks, scene round-trip tests, Play Console validation and production signing.

See `docs/PRODUCTION_READINESS.md` for the release gate.

## Repository
https://github.com/surafel5509-del/2D-game-engine

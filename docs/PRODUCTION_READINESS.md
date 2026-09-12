# Nova 2D Engine — Production Readiness

Nova is an Android-first 2D engine/editor with Kotlin tooling and a native C++ runtime.

## Editor
- Scene hierarchy, 2D viewport and grid
- Transform tools and inspector
- Play/stop workflow
- Console and frame diagnostics

## Runtime
- Fixed-step simulation
- Dynamic/static/sensor bodies and collision filtering
- Sprite/atlas data model
- Animation/timeline primitives
- Tilemap and particle systems
- Input mapping and audio service

## Production services
- SHA-256 asset records
- Deterministic atlas packing
- Prefab library
- UI document/layout model with hit testing
- Timeline interpolation
- Rolling diagnostics
- Export manifest
- Engine self-tests

## Release pipeline
GitHub Actions builds Debug APK, Release APK and Release AAB.

A real Android keystore must be supplied through protected CI secrets before public store distribution. Private signing keys are never committed to the repository.

## Quality gate
- CI build succeeds
- APK installs on supported API levels
- AAB passes Play Console checks
- JNI loads on supported ABIs
- Scene save/load round-trip passes
- Renderer and input are tested on physical devices
- Release is signed with a private production keystore

Nova is a focused 2D production toolchain; it is not claimed to be feature-equivalent to Unity.
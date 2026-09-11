# Nova 2D Engine - architecture

## Runtime
- `NativeEngine` owns simulation state in C++20.
- Kotlin `GameView` owns Android lifecycle, input and presentation.
- JNI is intentionally small so the native runtime can later be reused by a dedicated renderer.

## Roadmap
1. OpenGL ES renderer and texture atlas
2. Entity/component registry and scene serialization
3. Box2D integration
4. Animation state machines and particle system
5. Tilemap editor and prefab system
6. Audio mixer and resource cache
7. Visual scripting
8. In-app editor and project manager
9. Playtest/debug overlay
10. Signed Android AAB/APK release pipeline

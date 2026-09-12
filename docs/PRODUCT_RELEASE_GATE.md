# Nova 2D Engine — Product Release Gate

## Goal
This document defines the minimum gate before Nova is marketed or distributed as a production-ready Android 2D game engine.

## Engine gates
- [ ] Renderer draws imported PNG/JPEG/WebP assets on-device.
- [ ] Sprite atlas regions render with correct UVs.
- [ ] Sprite batching reduces draw calls for large scenes.
- [ ] Animation clips play, loop, blend and stop deterministically.
- [ ] Tilemaps support painting, erasing, layers and collision metadata.
- [ ] Physics contacts, sensors, layers/masks and joints are covered by tests.
- [ ] Audio playback, music streaming, buses and volume control work on device.
- [ ] Prefabs can be created, instantiated, overridden and saved.
- [ ] Scene save/load round-trips without data loss.
- [ ] UI nodes support layout, anchors, input and rendering.
- [ ] Script lifecycle and error reporting are stable.

## Editor gates
- [ ] Hierarchy supports create, delete, rename, duplicate and reparent.
- [ ] Inspector edits are undoable and redoable.
- [ ] Gizmos support move, rotate and scale.
- [ ] Asset browser imports and previews supported formats.
- [ ] Scene tabs and project settings persist between launches.
- [ ] Console exposes actionable errors and warnings.
- [ ] Play mode isolates editor state and can safely stop.
- [ ] Profiler reports frame, render, physics and memory metrics.

## Quality gates
- [ ] Unit tests pass.
- [ ] Instrumentation tests pass on API 24+.
- [ ] Release APK installs on a physical Android device.
- [ ] Release AAB passes bundle validation.
- [ ] 32-bit/64-bit native ABI packaging is intentional and verified.
- [ ] R8/minification is tested with JNI entry points.
- [ ] No debug secrets or signing keys are committed.
- [ ] Production signing uses protected CI secrets.
- [ ] Crash reporting and privacy documentation are configured before public distribution.

## Current status
Nova has a substantial Kotlin/C++ engine/editor architecture and CI release pipeline. It must not be labeled fully production-complete until the unchecked device, rendering, audio, physics, scripting and release gates above have been verified by automated and physical-device testing.

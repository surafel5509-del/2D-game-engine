#include <jni.h>
#include <mutex>
#include <vector>
#include "nova_runtime.hpp"

namespace {
    nova::NativeRuntime runtime;
    std::mutex runtimeMutex;
    bool valid(int id) { return id > 0 && runtime.world.body(static_cast<nova::BodyId>(id)) != nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_init(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    runtime.reset();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_nova_engine_NativeEngine_createEntity(JNIEnv*, jobject, jfloat x, jfloat y) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    return static_cast<jint>(runtime.world.createBody(x, y, nova::BodyType::Dynamic));
}

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_setVelocity(JNIEnv*, jobject, jint id, jfloat vx, jfloat vy) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    if (valid(id)) runtime.world.setVelocity(static_cast<nova::BodyId>(id), {vx, vy});
}

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_setBody(JNIEnv*, jobject, jint id, jboolean dynamic, jboolean sensor) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    if (!valid(id)) return;
    auto* b = runtime.world.body(static_cast<nova::BodyId>(id));
    if (!b) return;
    b->type = dynamic ? nova::BodyType::Dynamic : nova::BodyType::Static;
    b->inverseMass = dynamic ? 1.0f : 0.0f;
    b->inverseInertia = dynamic ? 1.0f : 0.0f;
    b->sensor = sensor;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_setSize(JNIEnv*, jobject, jint id, jfloat w, jfloat h) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    if (valid(id)) runtime.world.setShapeBox(static_cast<nova::BodyId>(id), {w, h});
}

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_setCollisionFilter(JNIEnv*, jobject, jint id, jint layer, jint mask) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    if (valid(id)) runtime.world.setFilter(static_cast<nova::BodyId>(id), static_cast<std::uint32_t>(layer), static_cast<std::uint32_t>(mask));
}

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_applyImpulse(JNIEnv*, jobject, jint id, jfloat ix, jfloat iy) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    if (valid(id)) runtime.world.applyImpulse(static_cast<nova::BodyId>(id), {ix, iy});
}

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_destroyEntity(JNIEnv*, jobject, jint id) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    if (valid(id)) runtime.world.destroyBody(static_cast<nova::BodyId>(id));
}

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_step(JNIEnv*, jobject, jfloat dt) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    runtime.step(dt);
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_nova_engine_NativeEngine_getTransforms(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    const auto data = runtime.transforms();
    jfloatArray out = env->NewFloatArray(static_cast<jsize>(data.size()));
    if (!out) return nullptr;
    if (!data.empty()) env->SetFloatArrayRegion(out, 0, static_cast<jsize>(data.size()), data.data());
    return out;
}

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_clear(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(runtimeMutex);
    runtime.world.clear();
}

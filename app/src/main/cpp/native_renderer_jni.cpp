#include <jni.h>
#include <mutex>
#include "nova_renderer.hpp"

namespace { nova::render::Renderer2D renderer; std::mutex rendererMutex; }

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nova_engine_NativeEngine_initializeRenderer(JNIEnv*, jobject, jint width, jint height){ std::lock_guard<std::mutex> lock(rendererMutex); const bool ok=renderer.initialize(); if(ok) renderer.resize(width,height); return ok?JNI_TRUE:JNI_FALSE; }

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_resizeRenderer(JNIEnv*, jobject, jint width, jint height){ std::lock_guard<std::mutex> lock(rendererMutex); renderer.resize(width,height); }

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_beginRenderer(JNIEnv*, jobject, jfloat r, jfloat g, jfloat b, jfloat a){ std::lock_guard<std::mutex> lock(rendererMutex); renderer.beginFrame(r,g,b,a); }

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_submitRendererSprite(JNIEnv*, jobject, jint texture, jfloat x, jfloat y, jfloat width, jfloat height, jfloat rotation, jfloat pivotX, jfloat pivotY, jfloat u0, jfloat v0, jfloat u1, jfloat v1, jfloat r, jfloat g, jfloat b, jfloat a){
    std::lock_guard<std::mutex> lock(rendererMutex); nova::render::Sprite s; s.texture=static_cast<std::uint32_t>(texture); s.x=x;s.y=y;s.width=width;s.height=height;s.rotation=rotation;s.pivotX=pivotX;s.pivotY=pivotY;s.u0=u0;s.v0=v0;s.u1=u1;s.v1=v1;s.color={r,g,b,a}; renderer.submit(s);
}

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_endRenderer(JNIEnv*, jobject){ std::lock_guard<std::mutex> lock(rendererMutex); renderer.endFrame(); }

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_nova_engine_NativeEngine_rendererStats(JNIEnv* env, jobject){ std::lock_guard<std::mutex> lock(rendererMutex); const auto s=renderer.stats(); const jlong values[]={static_cast<jlong>(s.frame),static_cast<jlong>(s.submitted),static_cast<jlong>(s.visible),static_cast<jlong>(s.culled),static_cast<jlong>(s.batches),static_cast<jlong>(s.drawCalls)}; jlongArray out=env->NewLongArray(6); if(out) env->SetLongArrayRegion(out,0,6,values); return out; }

extern "C" JNIEXPORT void JNICALL
Java_com_nova_engine_NativeEngine_shutdownRenderer(JNIEnv*, jobject){ std::lock_guard<std::mutex> lock(rendererMutex); renderer.shutdown(); }

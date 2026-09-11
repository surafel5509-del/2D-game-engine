#include <jni.h>
#include <cmath>
#include <mutex>
#include <vector>

struct Entity { float x=0,y=0,vx=0,vy=0,w=64,h=64; bool dynamic=true; };
static std::vector<Entity> world;
static std::mutex mutex;
static float accumulator=0.0f;
static constexpr float FIXED_DT=1.0f/60.0f;

extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_init(JNIEnv*, jobject) {
    std::lock_guard lock(mutex); world.clear(); world.push_back({200,200,0,0,64,64,true});
}
extern "C" JNIEXPORT jint JNICALL Java_com_nova_engine_NativeEngine_createEntity(JNIEnv*, jobject, jfloat x, jfloat y) {
    std::lock_guard lock(mutex); world.push_back({x,y,0,0,64,64,true}); return (jint)world.size()-1;
}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_setVelocity(JNIEnv*, jobject, jint id, jfloat vx, jfloat vy) {
    std::lock_guard lock(mutex); if(id>=0 && (size_t)id<world.size()){world[id].vx=vx;world[id].vy=vy;}
}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_step(JNIEnv*, jobject, jfloat dt) {
    std::lock_guard lock(mutex); accumulator += std::min(dt,0.25f);
    while(accumulator >= FIXED_DT){ for(auto &e:world) if(e.dynamic){e.vy += 980.0f*FIXED_DT; e.x += e.vx*FIXED_DT; e.y += e.vy*FIXED_DT; if(e.y>900){e.y=900;e.vy=0;} } accumulator-=FIXED_DT; }
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_nova_engine_NativeEngine_getTransforms(JNIEnv* env, jobject) {
    std::lock_guard lock(mutex); jfloatArray out=env->NewFloatArray(world.size()*4); std::vector<float> data; data.reserve(world.size()*4); for(auto&e:world){data.push_back(e.x);data.push_back(e.y);data.push_back(e.w);data.push_back(e.h);} env->SetFloatArrayRegion(out,0,data.size(),data.data()); return out;
}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_clear(JNIEnv*, jobject){std::lock_guard lock(mutex);world.clear();}

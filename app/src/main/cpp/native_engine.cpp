#include <jni.h>
#include <algorithm>
#include <cmath>
#include <mutex>
#include <vector>

namespace nova {
struct Entity { float x=0,y=0,vx=0,vy=0,w=64,h=64; bool dynamic=true; bool sensor=false; int layer=1; int mask=-1; };
static std::vector<Entity> world;
static std::mutex mutex;
static float accumulator=0.0f;
static constexpr float FIXED_DT=1.0f/60.0f;
static constexpr float GRAVITY=980.0f;
static constexpr float WORLD_FLOOR=900.0f;

static bool overlaps(const Entity&a,const Entity&b){return a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y;}

static void simulateFixed(){
    for(auto &e:world) if(e.dynamic){
        e.vy += GRAVITY*FIXED_DT;
        e.x += e.vx*FIXED_DT;
        e.y += e.vy*FIXED_DT;
        if(e.y+e.h>WORLD_FLOOR){e.y=WORLD_FLOOR-e.h;e.vy=0.0f;}
    }
    // Stable, deterministic AABB resolution. Static bodies are represented by dynamic=false.
    for(size_t i=0;i<world.size();++i) for(size_t j=i+1;j<world.size();++j){
        auto &a=world[i]; auto &b=world[j];
        if(a.sensor||b.sensor||!(a.layer&b.mask)&&!(b.layer&a.mask)||!overlaps(a,b)) continue;
        if(a.dynamic==b.dynamic) continue;
        Entity &d=a.dynamic?a:b; const Entity &s=a.dynamic?b:a;
        const float left=s.x-d.w, right=s.x+s.w, top=s.y-d.h, bottom=s.y+s.h;
        const float px=std::min(d.x+d.w,right)-std::max(d.x,left);
        const float py=std::min(d.y+d.h,bottom)-std::max(d.y,top);
        if(py<=px){ d.y = s.y-d.h; if(d.vy>0)d.vy=0; d.vx*=0.92f; }
        else { if(d.x<s.x)d.x=s.x-d.w;else d.x=s.x+s.w; d.vx=0; }
    }
}
}

extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_init(JNIEnv*, jobject){
    std::lock_guard lock(nova::mutex); nova::world.clear(); nova::accumulator=0; nova::world.push_back({200,200,0,0,64,64,true,false,1,-1});
}
extern "C" JNIEXPORT jint JNICALL Java_com_nova_engine_NativeEngine_createEntity(JNIEnv*, jobject,jfloat x,jfloat y){
    std::lock_guard lock(nova::mutex); nova::world.push_back({x,y,0,0,64,64,true,false,1,-1}); return (jint)nova::world.size()-1;
}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_setVelocity(JNIEnv*, jobject,jint id,jfloat vx,jfloat vy){
    std::lock_guard lock(nova::mutex); if(id>=0&&(size_t)id<nova::world.size()){nova::world[id].vx=vx;nova::world[id].vy=vy;}
}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_step(JNIEnv*, jobject,jfloat dt){
    std::lock_guard lock(nova::mutex); nova::accumulator+=std::min(std::max(dt,0.0f),0.25f);
    int steps=0; while(nova::accumulator>=nova::FIXED_DT&&steps++<8){nova::simulateFixed();nova::accumulator-=nova::FIXED_DT;}
}
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_nova_engine_NativeEngine_getTransforms(JNIEnv* env,jobject){
    std::lock_guard lock(nova::mutex); jfloatArray out=env->NewFloatArray((jsize)nova::world.size()*4); if(!out)return nullptr;
    std::vector<float> data; data.reserve(nova::world.size()*4); for(const auto&e:nova::world){data.push_back(e.x);data.push_back(e.y);data.push_back(e.w);data.push_back(e.h);} env->SetFloatArrayRegion(out,0,(jsize)data.size(),data.data()); return out;
}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_clear(JNIEnv*,jobject){std::lock_guard lock(nova::mutex);nova::world.clear();nova::accumulator=0;}

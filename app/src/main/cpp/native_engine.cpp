#include <jni.h>
#include <algorithm>
#include <cmath>
#include <mutex>
#include <vector>
namespace nova {
struct Entity { float x=0,y=0,vx=0,vy=0,w=64,h=64; bool dynamic=true,sensor=false,alive=true; int layer=1,mask=-1; float restitution=0.0f; };
static std::vector<Entity> world; static std::mutex mutex; static float accumulator=0.0f;
static constexpr float FIXED_DT=1.0f/60.0f,GRAVITY=980.0f,WORLD_FLOOR=900.0f;
static bool overlaps(const Entity&a,const Entity&b){return a.alive&&b.alive&&a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y;}
static void simulateFixed(){
 for(auto&e:world)if(e.alive&&e.dynamic){e.vy+=GRAVITY*FIXED_DT;e.x+=e.vx*FIXED_DT;e.y+=e.vy*FIXED_DT;if(e.y+e.h>WORLD_FLOOR){e.y=WORLD_FLOOR-e.h;if(e.vy>0)e.vy=-e.vy*e.restitution;if(std::abs(e.vy)<2)e.vy=0;}}
 for(size_t i=0;i<world.size();++i)for(size_t j=i+1;j<world.size();++j){auto&a=world[i];auto&b=world[j];if(!overlaps(a,b)||a.sensor||b.sensor||((a.layer&b.mask)==0&&(b.layer&a.mask)==0)||a.dynamic==b.dynamic)continue;Entity&d=a.dynamic?a:b;const Entity&s=a.dynamic?b:a;float px=std::min(d.x+d.w,s.x+s.w)-std::max(d.x,s.x);float py=std::min(d.y+d.h,s.y+s.h)-std::max(d.y,s.y);if(py<=px){if(d.y<s.y)d.y=s.y-d.h;else d.y=s.y+s.h;if(d.vy>0)d.vy=-d.vy*d.restitution;d.vx*=.90f;if(std::abs(d.vy)<2)d.vy=0;}else{if(d.x<s.x)d.x=s.x-d.w;else d.x=s.x+s.w;d.vx=0;}}
}
}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_init(JNIEnv*,jobject){std::lock_guard lock(nova::mutex);nova::world.clear();nova::accumulator=0;nova::world.push_back({200,200,0,0,64,64,true,false,true,1,-1,0});}
extern "C" JNIEXPORT jint JNICALL Java_com_nova_engine_NativeEngine_createEntity(JNIEnv*,jobject,jfloat x,jfloat y){std::lock_guard lock(nova::mutex);nova::world.push_back({x,y,0,0,64,64,true,false,true,1,-1,0});return(jint)nova::world.size()-1;}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_setVelocity(JNIEnv*,jobject,jint id,jfloat vx,jfloat vy){std::lock_guard lock(nova::mutex);if(id>=0&&(size_t)id<nova::world.size()){nova::world[id].vx=vx;nova::world[id].vy=vy;}}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_setBody(JNIEnv*,jobject,jint id,jboolean dynamic,jboolean sensor){std::lock_guard lock(nova::mutex);if(id>=0&&(size_t)id<nova::world.size()){nova::world[id].dynamic=dynamic;nova::world[id].sensor=sensor;}}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_setSize(JNIEnv*,jobject,jint id,jfloat w,jfloat h){std::lock_guard lock(nova::mutex);if(id>=0&&(size_t)id<nova::world.size()){nova::world[id].w=std::max(1.0f,w);nova::world[id].h=std::max(1.0f,h);}}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_setCollisionFilter(JNIEnv*,jobject,jint id,jint layer,jint mask){std::lock_guard lock(nova::mutex);if(id>=0&&(size_t)id<nova::world.size()){nova::world[id].layer=layer;nova::world[id].mask=mask;}}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_applyImpulse(JNIEnv*,jobject,jint id,jfloat ix,jfloat iy){std::lock_guard lock(nova::mutex);if(id>=0&&(size_t)id<nova::world.size()&&nova::world[id].dynamic){nova::world[id].vx+=ix;nova::world[id].vy+=iy;}}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_destroyEntity(JNIEnv*,jobject,jint id){std::lock_guard lock(nova::mutex);if(id>=0&&(size_t)id<nova::world.size())nova::world[id].alive=false;}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_step(JNIEnv*,jobject,jfloat dt){std::lock_guard lock(nova::mutex);nova::accumulator+=std::min(std::max(dt,0.0f),0.25f);int steps=0;while(nova::accumulator>=nova::FIXED_DT&&steps++<8){nova::simulateFixed();nova::accumulator-=nova::FIXED_DT;}}
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_nova_engine_NativeEngine_getTransforms(JNIEnv* env,jobject){std::lock_guard lock(nova::mutex);jfloatArray out=env->NewFloatArray((jsize)nova::world.size()*4);if(!out)return nullptr;std::vector<float>data;data.reserve(nova::world.size()*4);for(const auto&e:nova::world){data.push_back(e.alive?e.x:-100000);data.push_back(e.alive?e.y:-100000);data.push_back(e.alive?e.w:0);data.push_back(e.alive?e.h:0);}env->SetFloatArrayRegion(out,0,(jsize)data.size(),data.data());return out;}
extern "C" JNIEXPORT void JNICALL Java_com_nova_engine_NativeEngine_clear(JNIEnv*,jobject){std::lock_guard lock(nova::mutex);nova::world.clear();nova::accumulator=0;}

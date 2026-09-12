#pragma once
#include <cstdint>
#include <vector>
#include <mutex>
#include <cmath>
#include <algorithm>
namespace nova {
using BodyId=std::uint32_t; constexpr BodyId INVALID_BODY=0xffffffffu;
enum class BodyType:std::uint8_t{Static,Kinematic,Dynamic}; enum class ShapeType:std::uint8_t{Box,Circle};
struct Vec2{float x=0,y=0; Vec2()=default; Vec2(float X,float Y):x(X),y(Y){} Vec2 operator+(Vec2 o)const{return{x+o.x,y+o.y};} Vec2 operator-(Vec2 o)const{return{x-o.x,y-o.y};} Vec2 operator*(float s)const{return{x*s,y*s};} Vec2& operator+=(Vec2 o){x+=o.x;y+=o.y;return *this;}};
inline float dot(Vec2 a,Vec2 b){return a.x*b.x+a.y*b.y;} inline float len2(Vec2 a){return dot(a,a);} inline float len(Vec2 a){return std::sqrt(len2(a));} inline Vec2 norm(Vec2 a){float l=len(a);return l>1e-6f?a*(1.0f/l):Vec2{};}
struct Material2D{float density=1,friction=.6f,restitution=0;}; struct Filter2D{std::uint32_t layer=1,mask=0xffffffffu;};
struct Shape2D{ShapeType type=ShapeType::Box;Vec2 size{64,64};float radius=32;};
struct Body2D{BodyId id=INVALID_BODY;Vec2 position{},velocity{},force{};float angle=0,angularVelocity=0,torque=0,inverseMass=1,inverseInertia=1;BodyType type=BodyType::Dynamic;Shape2D shape{};Material2D material{};Filter2D filter{};bool sensor=false,awake=true,grounded=false;float sleepTime=0;};
struct Contact2D{BodyId a=INVALID_BODY,b=INVALID_BODY;Vec2 normal{},point{};float penetration=0;bool sensor=false;}; struct RayHit2D{BodyId body=INVALID_BODY;Vec2 point{},normal{};float fraction=1;};
struct Aabb2D{Vec2 min{},max{};bool overlaps(Aabb2D b)const{return min.x<=b.max.x&&max.x>=b.min.x&&min.y<=b.max.y&&max.y>=b.min.y;}};
class PhysicsWorld{public:PhysicsWorld();BodyId createBody(float,float,BodyType=BodyType::Dynamic);void destroyBody(BodyId);Body2D* body(BodyId);const Body2D* body(BodyId)const;void setVelocity(BodyId,Vec2);void setShapeBox(BodyId,Vec2);void setShapeCircle(BodyId,float);void setSensor(BodyId,bool);void setFilter(BodyId,std::uint32_t,std::uint32_t);void setMaterial(BodyId,float,float,float);void applyForce(BodyId,Vec2);void applyImpulse(BodyId,Vec2);void step(float);const std::vector<Contact2D>& contacts()const{return contacts_;}bool raycast(Vec2,Vec2,float,RayHit2D&)const;std::vector<BodyId> queryAabb(Aabb2D)const;void clear();float gravityY=980;float fixedStep=1.0f/60;int maxSubSteps=8;private:std::vector<Body2D>bodies_;std::vector<Contact2D>contacts_;float accumulator_=0;BodyId nextId_=1;mutable std::mutex mutex_;void tick(float);void integrate(Body2D&,float);void solveContacts();bool collide(const Body2D&,const Body2D&,Contact2D&)const;bool boxBox(const Body2D&,const Body2D&,Contact2D&)const;bool circleCircle(const Body2D&,const Body2D&,Contact2D&)const;bool boxCircle(const Body2D&,const Body2D&,Contact2D&)const;Aabb2D aabb(const Body2D&)const;bool canCollide(const Body2D&,const Body2D&)const;void wake(Body2D&);void sleeping(Body2D&,float);};
class NativeRuntime{public:PhysicsWorld world;void reset();void step(float);std::vector<float>transforms()const;};
}

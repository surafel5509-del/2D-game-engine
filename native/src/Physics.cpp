#include "../include/Physics.h"
#include <android/log.h>
#include <vector>
#include <cmath>
#include "../include/Renderer.h"

// Try to include Box2D if available; otherwise provide stubs.
#if __has_include(<box2d/box2d.h>)
#include <box2d/box2d.h>
#define FUTURE2D_HAS_BOX2D 1
#elif __has_include(<Box2D/Box2D.h>)
#include <Box2D/Box2D.h>
#define FUTURE2D_HAS_BOX2D 1
#else
#define FUTURE2D_HAS_BOX2D 0
#endif

#if FUTURE2D_HAS_BOX2D
// Debug draw implementation that forwards to Renderer
class DebugDraw : public b2Draw {
public:
    DebugDraw(future2d::Renderer* r): renderer(r) {}
    void DrawPolygon(const b2Vec2* vertices, int32 vertexCount, const b2Color& color) override {
        if (!renderer) return;
        std::vector<float> pts(vertexCount * 2);
        for (int i = 0; i < vertexCount; ++i) { pts[i*2+0] = vertices[i].x; pts[i*2+1] = vertices[i].y; }
        unsigned int col = (static_cast<int>(color.a*255)<<24) | (static_cast<int>(color.r*255)<<16) | (static_cast<int>(color.g*255)<<8) | static_cast<int>(color.b*255);
        renderer->drawPolygon(pts.data(), vertexCount, col, true);
    }
    void DrawSolidPolygon(const b2Vec2* vertices, int32 vertexCount, const b2Color& color) override {
        DrawPolygon(vertices, vertexCount, color);
    }
    void DrawCircle(const b2Vec2& center, float radius, const b2Color& color) override {
        if (!renderer) return;
        const int segs = 16;
        std::vector<float> pts(segs * 2);
        for (int i = 0; i < segs; ++i) {
            float a = (2.0f * 3.14159265358979323846f * i) / segs;
            pts[i*2+0] = center.x + cosf(a) * radius;
            pts[i*2+1] = center.y + sinf(a) * radius;
        }
        unsigned int col = (static_cast<int>(color.a*255)<<24) | (static_cast<int>(color.r*255)<<16) | (static_cast<int>(color.g*255)<<8) | static_cast<int>(color.b*255);
        renderer->drawPolygon(pts.data(), segs, col, true);
    }
    void DrawSolidCircle(const b2Vec2& center, float radius, const b2Vec2& axis, const b2Color& color) override { DrawCircle(center, radius, color); }
    void DrawSegment(const b2Vec2& p1, const b2Vec2& p2, const b2Color& color) override {
        if (!renderer) return;
        unsigned int col = (static_cast<int>(color.a*255)<<24) | (static_cast<int>(color.r*255)<<16) | (static_cast<int>(color.g*255)<<8) | static_cast<int>(color.b*255);
        renderer->drawLine(p1.x, p1.y, p2.x, p2.y, col);
    }
    void DrawTransform(const b2Transform& xf) override {}
    void setRenderer(future2d::Renderer* r) { renderer = r; }
private:
    future2d::Renderer* renderer = nullptr;
};
#endif

namespace future2d {

struct PhysicsWorld::Impl {
#if FUTURE2D_HAS_BOX2D
    b2World* world = nullptr;
    std::vector<b2Body*> bodies;
    DebugDraw* debugDraw = nullptr;
#else
    // stubs
#endif
    PhysicsConfig cfg;
    std::function<void()> debugCb;
    Impl(const PhysicsConfig& c): cfg(c) {}
};

PhysicsWorld::PhysicsWorld(const PhysicsConfig& cfg) : impl_(new Impl(cfg)) {
#if FUTURE2D_HAS_BOX2D
    b2Vec2 g(cfg.gravityX, cfg.gravityY);
    impl_->world = new b2World(g);
#endif
}

PhysicsWorld::~PhysicsWorld() {
#if FUTURE2D_HAS_BOX2D
    if (impl_->world) delete impl_->world;
    if (impl_->debugDraw) delete impl_->debugDraw;
#endif
}

void PhysicsWorld::step(float dt, int velocityIterations, int positionIterations) {
#if FUTURE2D_HAS_BOX2D
    if (impl_->world) impl_->world->Step(dt, velocityIterations, positionIterations);
#endif
}

int PhysicsWorld::createBox(float x, float y, float hx, float hy, bool dynamic) {
#if FUTURE2D_HAS_BOX2D
    if (!impl_->world) return -1;
    b2BodyDef bd;
    bd.position.Set(x, y);
    bd.type = dynamic ? b2_dynamicBody : b2_staticBody;
    b2Body* body = impl_->world->CreateBody(&bd);
    b2PolygonShape box;
    box.SetAsBox(hx, hy);
    b2FixtureDef fd;
    fd.shape = &box;
    fd.density = dynamic ? 1.0f : 0.0f;
    fd.friction = 0.3f;
    body->CreateFixture(&fd);
    impl_->bodies.push_back(body);
    return static_cast<int>(impl_->bodies.size() - 1);
#else
    return -1;
#endif
}

int PhysicsWorld::createCircle(float x, float y, float r, bool dynamic) {
#if FUTURE2D_HAS_BOX2D
    if (!impl_->world) return -1;
    b2BodyDef bd;
    bd.position.Set(x, y);
    bd.type = dynamic ? b2_dynamicBody : b2_staticBody;
    b2Body* body = impl_->world->CreateBody(&bd);
    b2CircleShape cs;
    cs.m_p.Set(0,0);
    cs.m_radius = r;
    b2FixtureDef fd;
    fd.shape = &cs;
    fd.density = dynamic ? 1.0f : 0.0f;
    fd.friction = 0.3f;
    body->CreateFixture(&fd);
    impl_->bodies.push_back(body);
    return static_cast<int>(impl_->bodies.size() - 1);
#else
    return -1;
#endif
}

void PhysicsWorld::applyForce(int bodyId, float fx, float fy) {
#if FUTURE2D_HAS_BOX2D
    if (bodyId < 0 || bodyId >= static_cast<int>(impl_->bodies.size())) return;
    b2Body* b = impl_->bodies[bodyId];
    b->ApplyForceToCenter(b2Vec2(fx, fy), true);
#endif
}

void PhysicsWorld::setDebugDrawCallback(std::function<void()> cb) { impl_->debugCb = cb; }

void PhysicsWorld::setDebugRenderer(future2d::Renderer* r) {
#if FUTURE2D_HAS_BOX2D
    if (!impl_->world) return;
    if (!impl_->debugDraw) {
        impl_->debugDraw = new DebugDraw(r);
        impl_->world->SetDebugDraw(impl_->debugDraw);
    } else {
        impl_->debugDraw->setRenderer(r);
    }
#else
    (void)r;
#endif
}

std::pair<float,float> PhysicsWorld::getBodyPosition(int bodyId) const {
#if FUTURE2D_HAS_BOX2D
    if (bodyId < 0 || bodyId >= static_cast<int>(impl_->bodies.size())) return {0.0f, 0.0f};
    b2Body* b = impl_->bodies[bodyId];
    b2Vec2 p = b->GetPosition();
    return {p.x, p.y};
#else
    return {0.0f, 0.0f};
#endif
}

float PhysicsWorld::getBodyAngle(int bodyId) const {
#if FUTURE2D_HAS_BOX2D
    if (bodyId < 0 || bodyId >= static_cast<int>(impl_->bodies.size())) return 0.0f;
    b2Body* b = impl_->bodies[bodyId];
    return b->GetAngle();
#else
    return 0.0f;
#endif
}

} // namespace future2d

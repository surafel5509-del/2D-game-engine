#pragma once

#include <memory>
#include <functional>

namespace future2d {

struct PhysicsConfig {
    float gravityX = 0.0f;
    float gravityY = 9.8f;
};

class PhysicsWorld {
public:
    PhysicsWorld(const PhysicsConfig& cfg = {});
    ~PhysicsWorld();

    void step(float dt, int velocityIterations = 8, int positionIterations = 3);

    // Body creation helpers
    int createBox(float x, float y, float hx, float hy, bool dynamic = true);
    int createCircle(float x, float y, float r, bool dynamic = true);

    void applyForce(int bodyId, float fx, float fy);

    // Debug draw callback (if enabled) will call the provided function with vertex lists
    void setDebugDrawCallback(std::function<void()> cb);
    // Attach a renderer for debug drawing (optional)
    void setDebugRenderer(class future2d::Renderer* r);

    // Query body transforms
    std::pair<float,float> getBodyPosition(int bodyId) const;
    float getBodyAngle(int bodyId) const;

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace future2d

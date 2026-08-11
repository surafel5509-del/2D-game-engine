#pragma once

#include <memory>
#include <string>

namespace future2d {
class PhysicsWorld;

class LuaVM {
public:
    LuaVM();
    ~LuaVM();

    // Initialize VM and register bindings; returns false if Lua not available
    bool init(PhysicsWorld* physics = nullptr);

    // Execute Lua code string
    bool doString(const std::string& code, const std::string& name = "chunk");

    // Call `onUpdate(dt)` if defined
    void callOnUpdate(float dt);

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace future2d

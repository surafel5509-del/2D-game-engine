#pragma once

#include <atomic>
#include <memory>
#include <functional>

#include "ThreadPool.h"

struct android_app;

namespace future2d {

class GLContext;
class Renderer;
class PhysicsWorld;

class Engine {
public:
    Engine(android_app* app);
    ~Engine();

    void init();
    void run();
    void requestExit();
    void pause();
    void resume();
    bool isPaused() const;

    // Submit a task to the engine's worker pool.
    template<class F, class... Args>
    auto submitTask(F&& f, Args&&... args) -> std::future<typename std::invoke_result_t<F, Args...>> {
        return threadPool_.submit(std::forward<F>(f), std::forward<Args>(args)...);
    }

    // Callbacks for native app glue
    void handleCmd(int32_t cmd);
    int32_t handleInput(AInputEvent* event);

        // Callbacks
        std::function<void(float)> onUpdate;        // variable timestep update (dt seconds)
        std::function<void(float)> onFixedUpdate;   // fixed timestep update (dt seconds)
        std::function<void()> onRender;             // render callback

        // Fixed timestep configuration
        void setFixedTimestepHz(int hz);

        // Access renderer for advanced operations (atlas creation, etc.)
        Renderer* renderer();
        // Access physics world
        PhysicsWorld* physics();

private:
    struct Impl;
    std::unique_ptr<Impl> impl_;
    // Lightweight thread pool for async asset loading and background tasks
    ThreadPool threadPool_;
    std::unique_ptr<Renderer> renderer_;
};

} // namespace future2d

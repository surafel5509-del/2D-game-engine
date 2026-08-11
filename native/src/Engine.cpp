#include "../include/Engine.h"
#include "../include/GLContext.h"
#include <android/log.h>
#include <android_native_app_glue.h>
#include <thread>
#include <chrono>
#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include "../include/Renderer.h"
#include "../include/AssetLoader.h"
#include "../include/TextureAtlas.h"
#include "../include/Physics.h"
#include "../include/LuaVM.h"

using namespace std::chrono_literals;

namespace future2d {

struct Engine::Impl {
    android_app* app = nullptr;
    std::atomic<bool> running{false};
    std::unique_ptr<GLContext> gl;
    bool hasWindow = false;
    double fixedDt = 1.0 / 60.0;
    double maxFrameTime = 0.25; // clamp to avoid spiral of death
    std::atomic<bool> paused{false};
    std::unique_ptr<AssetLoader> loader;
    std::future<std::shared_ptr<ImageAsset>> imageFuture;
    std::shared_ptr<ImageAsset> demoImage;
    std::optional<AtlasRegion> demoRegion;
    std::unique_ptr<PhysicsWorld> physics;
    std::unique_ptr<LuaVM> lua;
    int demoBodyId = -1;
    Impl(android_app* a): app(a) {}
};

static void onAppCmd(struct android_app* app, int32_t cmd) {
    Engine* engine = reinterpret_cast<Engine*>(app->userData);
    if (engine) engine->handleCmd(cmd);
}

static int32_t onInputEvent(struct android_app* app, AInputEvent* event) {
    Engine* engine = reinterpret_cast<Engine*>(app->userData);
    if (engine) return engine->handleInput(event);
    return 0;
}

Engine::Engine(android_app* app)
    : impl_(new Impl(app)) {
    app->userData = this;
    app->onAppCmd = onAppCmd;
    app->onInputEvent = onInputEvent;
    renderer_ = std::make_unique<future2d::Renderer>();
}

Renderer* Engine::renderer() { return renderer_.get(); }

PhysicsWorld* Engine::physics() { return impl_ ? impl_->physics.get() : nullptr; }

Engine::~Engine() = default;

void Engine::init() {
    __android_log_print(ANDROID_LOG_INFO, "Future2D", "Engine init");
    impl_->running = true;
}

void Engine::handleCmd(int32_t cmd) {
    switch (cmd) {
        case APP_CMD_INIT_WINDOW:
            if (impl_->app->window) {
                impl_->gl = std::make_unique<GLContext>();
                if (impl_->gl->create(impl_->app->window)) {
                    impl_->hasWindow = true;
                    // initialize renderer with asset manager and surface size
                    AAssetManager* mgr = impl_->app->activity->assetManager;
                    if (renderer_) renderer_->init(mgr, impl_->gl->width(), impl_->gl->height());
                        // create physics world
                        impl_->physics = std::make_unique<PhysicsWorld>();
                        if (impl_->physics) impl_->physics->setDebugRenderer(renderer_.get());
                        // create Lua VM and register physics bindings
                        impl_->lua = std::make_unique<LuaVM>();
                        if (impl_->lua) impl_->lua->init(impl_->physics.get());
                    // create asset loader and kick off demo image load
                    impl_->loader = std::make_unique<AssetLoader>(mgr, 2);
                    impl_->imageFuture = impl_->loader->loadImageAsync("textures/demo.png");
                }
            }
            break;
        case APP_CMD_TERM_WINDOW:
            if (impl_->gl) {
                impl_->gl->destroy();
                impl_->gl.reset();
            }
            impl_->hasWindow = false;
            if (renderer_) renderer_->shutdown();
            break;
        case APP_CMD_PAUSE:
            impl_->paused = true;
            __android_log_print(ANDROID_LOG_INFO, "Future2D", "Engine paused");
            break;
        case APP_CMD_RESUME:
            impl_->paused = false;
            __android_log_print(ANDROID_LOG_INFO, "Future2D", "Engine resumed");
            break;
        case APP_CMD_GAINED_FOCUS:
            break;
        case APP_CMD_LOST_FOCUS:
            break;
        default:
            break;
    }
}

int32_t Engine::handleInput(AInputEvent* event) {
    // Basic touch handling scaffold
    int type = AInputEvent_getType(event);
    if (type == AINPUT_EVENT_TYPE_MOTION) {
        // let caller handle motion events via user callbacks if provided
        // return 1 to indicate event handled
        return 1;
    } else if (type == AINPUT_EVENT_TYPE_KEY) {
        // key events can be routed to callbacks in the future
        return 0; // not handled by default
    }
    return 0;
}

void Engine::run() {
    init();
    using clock = std::chrono::steady_clock;
    auto previous = clock::now();
    double accumulator = 0.0;

    while (impl_->running) {
        int ident;
        int events;
        struct android_poll_source* source;

        // Poll events; block 0 ms
        while ((ident = ALooper_pollAll(0, nullptr, &events, (void**)&source)) >= 0) {
            if (source) source->process(impl_->app, source);
            if (impl_->app->destroyRequested) {
                requestExit();
                break;
            }
        }

        if (impl_->paused) {
            // When paused, keep polling for events so resume is processed.
            int ident;
            int events;
            struct android_poll_source* source;
            while ((ident = ALooper_pollAll(0, nullptr, &events, (void**)&source)) >= 0) {
                if (source) source->process(impl_->app, source);
                if (impl_->app->destroyRequested) {
                    requestExit();
                    break;
                }
            }
            std::this_thread::sleep_for(50ms);
            continue;
        }

        auto now = clock::now();
        std::chrono::duration<double> frameTime = now - previous;
        previous = now;
        double dt = frameTime.count();
        if (dt > impl_->maxFrameTime) dt = impl_->maxFrameTime;
        accumulator += dt;

        // Fixed updates
        while (accumulator >= impl_->fixedDt) {
            if (onFixedUpdate) onFixedUpdate(static_cast<float>(impl_->fixedDt));
            if (impl_->physics) impl_->physics->step(static_cast<float>(impl_->fixedDt));
            accumulator -= impl_->fixedDt;
        }

        // Variable update with interpolation alpha
        if (onUpdate) onUpdate(static_cast<float>(dt));
        // Call Lua per-frame update if available
        if (impl_->lua) impl_->lua->callOnUpdate(static_cast<float>(dt));

        // Render
        if (impl_->hasWindow && impl_->gl) {
            impl_->gl->makeCurrent();
            glViewport(0, 0, impl_->gl->width(), impl_->gl->height());
            glClearColor(0.1f, 0.1f, 0.15f, 1.0f);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            if (onRender) onRender();
            // Check demo image load
            if (impl_->imageFuture.valid() && !impl_->demoImage) {
                using namespace std::chrono_literals;
                if (impl_->imageFuture.wait_for(0ms) == std::future_status::ready) {
                    impl_->demoImage = impl_->imageFuture.get();
                    if (impl_->demoImage && renderer_) {
                        // create atlas and insert
                        if (!renderer_->getAtlas()) renderer_->createAtlas(1024, 1024);
                        if (renderer_->getAtlas()) {
                            auto opt = renderer_->getAtlas()->insert(impl_->demoImage->width, impl_->demoImage->height, impl_->demoImage->data->data());
                            if (opt) impl_->demoRegion = *opt;
                            // create a physics body for the demo sprite (centered)
                            if (impl_->demoRegion && impl_->physics && impl_->demoBodyId == -1) {
                                auto r = impl_->demoRegion.value();
                                float x = 50.0f + static_cast<float>(r.w) * 0.5f;
                                float y = 50.0f + static_cast<float>(r.h) * 0.5f;
                                impl_->demoBodyId = impl_->physics->createBox(x, y, static_cast<float>(r.w) * 0.5f, static_cast<float>(r.h) * 0.5f, true);
                            }
                        }
                    }
                }
            }

            // Demo draw using atlas region at physics body's position (if exists)
            if (impl_->demoRegion && renderer_) {
                auto r = *impl_->demoRegion;
                float w = static_cast<float>(r.w);
                float h = static_cast<float>(r.h);
                float x = 50.0f, y = 50.0f;
                if (impl_->physics && impl_->demoBodyId != -1) {
                    auto pos = impl_->physics->getBodyPosition(impl_->demoBodyId);
                    x = pos.first - w * 0.5f;
                    y = pos.second - h * 0.5f;
                }
                renderer_->begin();
                renderer_->drawSprite(renderer_->getAtlas()->textureId(), x, y, w, h, r.u0, r.v0, r.u1, r.v1, 0xFFFFFFFF);
                renderer_->end();
            }
            impl_->gl->swapBuffers();
        } else {
            std::this_thread::sleep_for(10ms);
        }
    }
}

void Engine::requestExit() {
    impl_->running = false;
}

void Engine::setFixedTimestepHz(int hz) {
    if (hz <= 0) return;
    impl_->fixedDt = 1.0 / static_cast<double>(hz);
}

} // namespace future2d

#include <android_native_app_glue.h>
#include <android/log.h>
#include "../include/Engine.h"

using namespace future2d;

void android_main(struct android_app* state) {
    android_setenv("SDL_ANDROID_BLOCK_ON_PAUSE", "0");
    app_dummy(); // ensure glue is linked

    Engine engine(state);

    // Run the engine (this will block until exit)
    engine.run();

    __android_log_print(ANDROID_LOG_INFO, "Future2D", "android_main exiting");
}

#pragma once

#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <android/native_window.h>

namespace future2d {

class GLContext {
public:
    GLContext();
    ~GLContext();

    bool create(ANativeWindow* window);
    void destroy();
    void makeCurrent();
    void swapBuffers();
    int width() const { return width_; }
    int height() const { return height_; }

private:
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLSurface surface_ = EGL_NO_SURFACE;
    EGLContext context_ = EGL_NO_CONTEXT;
    EGLConfig config_ = nullptr;
    int width_ = 0;
    int height_ = 0;
};

} // namespace future2d

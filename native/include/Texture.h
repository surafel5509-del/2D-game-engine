#pragma once

#include <GLES3/gl3.h>
#include <memory>

class Texture {
public:
    Texture();
    ~Texture();

    bool createFromRGBA(int width, int height, const unsigned char* data, bool generateMipmap = true);
    void destroy();
    void bind(int unit = 0) const;

    GLuint id() const { return tex_; }
    int width() const { return w_; }
    int height() const { return h_; }

private:
    GLuint tex_ = 0;
    int w_ = 0, h_ = 0;
};

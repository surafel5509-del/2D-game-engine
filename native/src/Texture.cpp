#include "../include/Texture.h"
#include <android/log.h>

Texture::Texture() {}

Texture::~Texture() { destroy(); }

bool Texture::createFromRGBA(int width, int height, const unsigned char* data, bool generateMipmap) {
    if (!data || width <= 0 || height <= 0) return false;
    if (tex_) destroy();
    w_ = width; h_ = height;
    glGenTextures(1, &tex_);
    glBindTexture(GL_TEXTURE_2D, tex_);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w_, h_, 0, GL_RGBA, GL_UNSIGNED_BYTE, data);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, generateMipmap ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    if (generateMipmap) glGenerateMipmap(GL_TEXTURE_2D);
    glBindTexture(GL_TEXTURE_2D, 0);
    return true;
}

void Texture::destroy() {
    if (tex_) {
        glDeleteTextures(1, &tex_);
        tex_ = 0;
    }
    w_ = h_ = 0;
}

void Texture::bind(int unit) const {
    if (!tex_) return;
    glActiveTexture(GL_TEXTURE0 + unit);
    glBindTexture(GL_TEXTURE_2D, tex_);
}

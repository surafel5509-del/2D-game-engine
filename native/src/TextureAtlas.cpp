#include "../include/TextureAtlas.h"
#include <android/log.h>
#include <cstring>
#include <climits>

static void LOGE(const char* msg) { __android_log_print(ANDROID_LOG_ERROR, "Future2D", "%s", msg); }

namespace future2d {

TextureAtlas::TextureAtlas() {}

TextureAtlas::~TextureAtlas() { if (tex_) glDeleteTextures(1, &tex_); }

bool TextureAtlas::create(int width, int height, bool rgba8) {
    if (width <= 0 || height <= 0) return false;
    w_ = width; h_ = height;
    glGenTextures(1, &tex_);
    glBindTexture(GL_TEXTURE_2D, tex_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    // allocate empty texture
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w_, h_, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
    glBindTexture(GL_TEXTURE_2D, 0);
    skyline.clear();
    skyline.push_back({0,0,w_});
    usedHeight = 0;
    return true;
}

std::optional<AtlasRegion> TextureAtlas::insert(int ww, int hh, const uint8_t* rgbaData) {
    if (!tex_ || ww <= 0 || hh <= 0 || ww > w_ || hh > h_) return std::nullopt;

    // Find best-fit skyline node
    int bestIndex = -1;
    int bestX = 0;
    int bestY = INT_MAX;
    for (size_t i = 0; i < skyline.size(); ++i) {
        int x = skyline[i].x;
        int y = skyline[i].y;
        int widthLeft = 0;
        int j = static_cast<int>(i);
        int maxY = y;
        int span = 0;
        while (j < static_cast<int>(skyline.size()) && span < ww) {
            if (skyline[j].y > maxY) maxY = skyline[j].y;
            span += skyline[j].w;
            ++j;
        }
        if (span >= ww) {
            if (maxY < bestY) {
                bestY = maxY;
                bestIndex = static_cast<int>(i);
                bestX = x;
            }
        }
    }

    if (bestIndex == -1) return std::nullopt; // no fit

    int rx = bestX;
    int ry = bestY;

    // Insert node
    TextureAtlas::Node newNode{rx, ry + hh, ww};
    skyline.insert(skyline.begin() + bestIndex, newNode);

    // Merge nodes
    for (size_t i = 0; i + 1 < skyline.size(); ) {
        if (skyline[i].y == skyline[i+1].y) {
            skyline[i].w += skyline[i+1].w;
            skyline.erase(skyline.begin() + i + 1);
        } else {
            ++i;
        }
    }

    if (ry + hh > usedHeight) usedHeight = ry + hh;
    if (usedHeight > h_) return std::nullopt;

    // upload subimage
    glBindTexture(GL_TEXTURE_2D, tex_);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
    glTexSubImage2D(GL_TEXTURE_2D, 0, rx, ry, ww, hh, GL_RGBA, GL_UNSIGNED_BYTE, rgbaData);
    glBindTexture(GL_TEXTURE_2D, 0);

    AtlasRegion r;
    r.x = rx; r.y = ry; r.w = ww; r.h = hh;
    r.u0 = static_cast<float>(rx) / static_cast<float>(w_);
    r.v0 = static_cast<float>(ry) / static_cast<float>(h_);
    r.u1 = static_cast<float>(rx + ww) / static_cast<float>(w_);
    r.v1 = static_cast<float>(ry + hh) / static_cast<float>(h_);
    return r;
}

} // namespace future2d

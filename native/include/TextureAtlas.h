#pragma once

#include <GLES3/gl3.h>
#include <vector>
#include <cstdint>
#include <optional>

namespace future2d {

struct AtlasRegion {
    float u0, v0, u1, v1;
    int x, y, w, h;
};

class TextureAtlas {
public:
    TextureAtlas();
    ~TextureAtlas();

    // Create an empty atlas with given size (power-of-two recommended)
    bool create(int width, int height, bool rgba8 = true);

    // Try to insert an RGBA8 image into the atlas. Returns region on success.
    std::optional<AtlasRegion> insert(int w, int h, const uint8_t* rgbaData);

    GLuint textureId() const { return tex_; }

    int width() const { return w_; }
    int height() const { return h_; }

private:
    GLuint tex_ = 0;
    int w_ = 0, h_ = 0;

    // Skyline allocator nodes (x, y, width)
    struct Node { int x, y, w; };
    std::vector<Node> skyline;
    int usedHeight = 0;
};

} // namespace future2d

#pragma once

#include <GLES3/gl3.h>
#include <memory>
#include <vector>
#include <android/asset_manager.h>
#include "Shader.h"
#include "Texture.h"
#include "TextureAtlas.h"

namespace future2d {

class Renderer {
public:
    Renderer();
    ~Renderer();

    // Initialize renderer with asset manager and initial screen size
    bool init(AAssetManager* assets, int screenW, int screenH);
    void shutdown();

    void onResize(int w, int h);

    // Begin a frame; subsequent drawSprite calls will batch
    void begin();
    // Draw a sprite using a GL texture id; batches are flushed when texture changes
    void drawSprite(GLuint texId, float x, float y, float w, float h,
                    float u0 = 0.0f, float v0 = 0.0f, float u1 = 1.0f, float v1 = 1.0f,
                    unsigned int color = 0xFFFFFFFF);
    void end();

    // Simple stats overlay
    void renderStats();

    // Primitive drawing for debug: lines and polygons
    void drawLine(float x0, float y0, float x1, float y1, unsigned int color = 0xFFFFFFFF);
    void drawPolygon(const float* pts, int count, unsigned int color = 0xFFFFFFFF, bool closed = true);

    // Atlas helpers
    bool createAtlas(int width, int height);
    TextureAtlas* getAtlas() { return atlas_.get(); }

private:
    bool createBuffers();
    std::string loadAssetText(const char* path);
    void updateProjection();

    Shader shader_;
    GLuint vao_ = 0;
    GLuint vbo_ = 0;
    GLuint ibo_ = 0;
    GLuint currentTexture_ = 0; // GL texture currently being batched

    static const int MaxTextureSlots = 8;
    GLuint textureSlots_[MaxTextureSlots] = {0};
    int textureSlotCount_ = 0;

    AAssetManager* assets_ = nullptr;
    int width_ = 0;
    int height_ = 0;

    static const size_t MaxSprites = 4096; // initial capacity
    size_t spriteCount_ = 0;
    std::vector<float> vertexBuffer_; // interleaved per-vertex data
    std::vector<uint16_t> indexBuffer_;
    // single-batch only for now; will flush on texture change
    std::unique_ptr<TextureAtlas> atlas_;
    // line rendering resources
    GLuint lineVao_ = 0;
    GLuint lineVbo_ = 0;
    Shader lineShader_;
    bool createLineBuffers();
    
    void resetTextureSlots();
    int findOrAssignTextureSlot(GLuint tex);
};

} // namespace future2d

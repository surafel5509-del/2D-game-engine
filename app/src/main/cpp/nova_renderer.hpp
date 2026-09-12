#pragma once

#include <cstdint>
#include <string>
#include <unordered_map>
#include <vector>

namespace nova::render {

struct Vec2 { float x=0.f; float y=0.f; };
struct Color { float r=1.f; float g=1.f; float b=1.f; float a=1.f; };
struct Camera { Vec2 position{}; float zoom=1.f; float rotation=0.f; float width=1.f; float height=1.f; };
struct Sprite { std::uint32_t texture=0; float x=0.f; float y=0.f; float width=1.f; float height=1.f; float rotation=0.f; float pivotX=.5f; float pivotY=.5f; float u0=0.f; float v0=0.f; float u1=1.f; float v1=1.f; Color color{}; int layer=0; int z=0; };
struct FrameStats { std::uint64_t frame=0; std::uint32_t submitted=0; std::uint32_t visible=0; std::uint32_t culled=0; std::uint32_t batches=0; std::uint32_t drawCalls=0; };

class Renderer2D {
public:
    Renderer2D();
    ~Renderer2D();
    Renderer2D(const Renderer2D&) = delete;
    Renderer2D& operator=(const Renderer2D&) = delete;

    bool initialize();
    void shutdown();
    void resize(int width, int height);
    void beginFrame(float r=0.08f, float g=0.09f, float b=0.12f, float a=1.f);
    void submit(const Sprite& sprite);
    void endFrame();
    FrameStats stats() const { return stats_; }
    Camera& camera() { return camera_; }

    std::uint32_t createTextureRGBA(int width, int height, const std::uint8_t* pixels, bool linear=true);
    void destroyTexture(std::uint32_t texture);
    void clearTextures();

private:
    struct Vertex { float x,y,u,v,r,g,b,a; };
    struct BatchKey { std::uint32_t texture=0; bool operator==(const BatchKey& o) const { return texture==o.texture; } };
    struct BatchKeyHash { std::size_t operator()(const BatchKey& k) const { return std::hash<std::uint32_t>{}(k.texture); } };

    bool createProgram();
    bool createBuffers();
    bool ensureContextResources();
    bool visible(const Sprite& sprite) const;
    void buildVertices(const Sprite& sprite);
    void flush();
    void destroyGlResources();

    std::uint32_t program_=0;
    std::uint32_t vbo_=0;
    int positionLoc_=-1;
    int uvLoc_=-1;
    int colorLoc_=-1;
    int textureLoc_=-1;
    int projectionLoc_=-1;
    std::vector<Vertex> vertices_;
    std::uint32_t currentTexture_=0;
    std::uint32_t maxSprites_=2048;
    Camera camera_{};
    FrameStats stats_{};
    bool initialized_=false;
    bool frameActive_=false;
};

} // namespace nova::render

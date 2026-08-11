#include "../include/Renderer.h"
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <cstring>

static void LOGI(const char* msg) { __android_log_print(ANDROID_LOG_INFO, "Future2D", "%s", msg); }
static void LOGE(const char* msg) { __android_log_print(ANDROID_LOG_ERROR, "Future2D", "%s", msg); }

namespace future2d {

static const char* defaultVert =
    "#version 300 es\n"
    "layout(location = 0) in vec2 aPos;\n"
    "layout(location = 1) in vec2 aUV;\n"
    "layout(location = 2) in vec3 aColor;\n"
    "layout(location = 3) in float aTexIndex;\n"
    "uniform mat4 uProj;\n"
    "out vec2 vUV;\n"
    "out vec4 vColor;\n"
    "out float vTexIndex;\n"
    "void main() { vUV = aUV; vColor = vec4(aColor, 1.0); vTexIndex = aTexIndex; gl_Position = uProj * vec4(aPos, 0.0, 1.0); }\n";

static const char* defaultFrag =
    "#version 300 es\n"
    "precision mediump float;\n"
    "in vec2 vUV;\n"
    "in vec4 vColor;\n"
    "in float vTexIndex;\n"
    "uniform sampler2D uTex[8];\n"
    "out vec4 fragColor;\n"
    "void main() {\n"
    "  int idx = int(round(vTexIndex * 7.0));\n"
    "  vec4 c = texture(uTex[idx], vUV);\n"
    "  fragColor = c * vColor;\n"
    "}\n";

Renderer::Renderer() {}

Renderer::~Renderer() { shutdown(); }

bool Renderer::createAtlas(int width, int height) {
    atlas_ = std::make_unique<TextureAtlas>();
    return atlas_->create(width, height, true);
}

std::string Renderer::loadAssetText(const char* path) {
    if (!assets_) return std::string();
    AAsset* asset = AAssetManager_open(assets_, path, AASSET_MODE_BUFFER);
    if (!asset) return std::string();
    off_t len = AAsset_getLength(asset);
    std::string s;
    s.resize(len);
    int r = AAsset_read(asset, &s[0], len);
    AAsset_close(asset);
    if (r <= 0) return std::string();
    return s;
}

bool Renderer::createBuffers() {
    if (vao_ || vbo_ || ibo_) return true;

    glGenVertexArrays(1, &vao_);
    glBindVertexArray(vao_);

    glGenBuffers(1, &vbo_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    // allocate enough for MaxSprites
    // position (2), uv (2), color (4), texIndex (1)
    size_t vertSize = MaxSprites * 4 * (2 + 2 + 3 + 1) * sizeof(float);
    glBufferData(GL_ARRAY_BUFFER, vertSize, nullptr, GL_DYNAMIC_DRAW);

    // position (2), uv (2), color (3), texIndex (1)
    GLsizei stride = (2 + 2 + 3 + 1) * sizeof(float);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, stride, (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, stride, (void*)(2 * sizeof(float)));
    glEnableVertexAttribArray(2);
    glVertexAttribPointer(2, 3, GL_FLOAT, GL_FALSE, stride, (void*)(4 * sizeof(float)));
    glEnableVertexAttribArray(3);
    glVertexAttribPointer(3, 1, GL_FLOAT, GL_FALSE, stride, (void*)((4 + 3) * sizeof(float)));

    // index buffer
    glGenBuffers(1, &ibo_);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo_);
    size_t idxSize = MaxSprites * 6 * sizeof(uint16_t);
    std::vector<uint16_t> idx(MaxSprites * 6);
    for (size_t i = 0; i < MaxSprites; ++i) {
        uint16_t base = static_cast<uint16_t>(i * 4);
        idx[i * 6 + 0] = base + 0;
        idx[i * 6 + 1] = base + 1;
        idx[i * 6 + 2] = base + 2;
        idx[i * 6 + 3] = base + 0;
        idx[i * 6 + 4] = base + 2;
        idx[i * 6 + 5] = base + 3;
    }
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, idxSize, idx.data(), GL_STATIC_DRAW);

    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    return true;
}

bool Renderer::init(AAssetManager* assets, int screenW, int screenH) {
    assets_ = assets;
    width_ = screenW; height_ = screenH;

    // try load shader from assets
    std::string vert = loadAssetText("shaders/sprite.vert");
    std::string frag = loadAssetText("shaders/sprite.frag");
    if (vert.empty() || frag.empty()) {
        vert = defaultVert; frag = defaultFrag;
    }

    if (!shader_.loadFromSource(vert, frag)) {
        LOGE("Failed to compile default sprite shader");
        return false;
    }

    // set sampler array once
    shader_.use();
    GLint loc = glGetUniformLocation(shader_.id(), "uTex[0]");
    if (loc >= 0) {
        GLint units[MaxTextureSlots];
        for (int i = 0; i < MaxTextureSlots; ++i) units[i] = i;
        glUniform1iv(loc, MaxTextureSlots, units);
    }

    if (!createBuffers()) return false;
    if (!createLineBuffers()) {
        LOGE("Failed to create line buffers");
        // not fatal
    }

    vertexBuffer_.reserve(MaxSprites * 4 * (2 + 2 + 3 + 1));
    indexBuffer_.reserve(MaxSprites * 6);
    return true;
}

bool Renderer::createLineBuffers() {
    if (lineVao_ || lineVbo_) return true;
    const char* lvert = "#version 300 es\nlayout(location=0) in vec2 aPos;layout(location=1) in vec4 aColor;uniform mat4 uProj;out vec4 vColor;void main(){vColor=aColor;gl_Position=uProj*vec4(aPos,0.0,1.0);}";
    const char* lfrag = "#version 300 es\nprecision mediump float;in vec4 vColor;out vec4 fragColor;void main(){fragColor=vColor;}";
    if (!lineShader_.loadFromSource(lvert, lfrag)) return false;

    glGenVertexArrays(1, &lineVao_);
    glBindVertexArray(lineVao_);
    glGenBuffers(1, &lineVbo_);
    glBindBuffer(GL_ARRAY_BUFFER, lineVbo_);
    // dynamic buffer for a few hundred verts
    glBufferData(GL_ARRAY_BUFFER, 1024 * sizeof(float) * 6, nullptr, GL_DYNAMIC_DRAW);
    // aPos (2), aColor (4)
    GLsizei stride = (2 + 4) * sizeof(float);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, stride, (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, stride, (void*)(2 * sizeof(float)));
    glBindVertexArray(0);
    return true;
}

static void unpackColor(unsigned int color, float out[4]) {
    out[0] = ((color >> 16) & 0xFF) / 255.0f;
    out[1] = ((color >> 8) & 0xFF) / 255.0f;
    out[2] = ((color >> 0) & 0xFF) / 255.0f;
    out[3] = ((color >> 24) & 0xFF) / 255.0f;
}

void Renderer::drawLine(float x0, float y0, float x1, float y1, unsigned int color) {
    if (!lineVao_ || !lineVbo_) return;
    float col[4]; unpackColor(color, col);
    float verts[2 * (2 + 4)];
    // v0
    verts[0] = x0; verts[1] = y0;
    verts[2] = col[0]; verts[3] = col[1]; verts[4] = col[2]; verts[5] = col[3];
    // v1
    verts[6] = x1; verts[7] = y1;
    verts[8] = col[0]; verts[9] = col[1]; verts[10] = col[2]; verts[11] = col[3];

    lineShader_.use();
    updateProjection(); // sets uProj on current shader; ensure proj set
    glBindVertexArray(lineVao_);
    glBindBuffer(GL_ARRAY_BUFFER, lineVbo_);
    glBufferSubData(GL_ARRAY_BUFFER, 0, sizeof(verts), verts);
    glDrawArrays(GL_LINES, 0, 2);
    glBindVertexArray(0);
}

void Renderer::drawPolygon(const float* pts, int count, unsigned int color, bool closed) {
    if (!lineVao_ || !lineVbo_ || count <= 1) return;
    float col[4]; unpackColor(color, col);
    std::vector<float> buf;
    buf.reserve(count * (2 + 4) + 8);
    for (int i = 0; i < count; ++i) {
        float x = pts[i * 2 + 0];
        float y = pts[i * 2 + 1];
        buf.push_back(x); buf.push_back(y);
        buf.push_back(col[0]); buf.push_back(col[1]); buf.push_back(col[2]); buf.push_back(col[3]);
    }
    if (closed) {
        // append first vertex to close loop
        buf.push_back(pts[0]); buf.push_back(pts[1]);
        buf.push_back(col[0]); buf.push_back(col[1]); buf.push_back(col[2]); buf.push_back(col[3]);
    }
    lineShader_.use();
    updateProjection();
    glBindVertexArray(lineVao_);
    glBindBuffer(GL_ARRAY_BUFFER, lineVbo_);
    glBufferSubData(GL_ARRAY_BUFFER, 0, buf.size() * sizeof(float), buf.data());
    glDrawArrays(GL_LINE_STRIP, 0, static_cast<GLsizei>(buf.size() / 6));
    glBindVertexArray(0);
}

void Renderer::shutdown() {
    if (vbo_) { glDeleteBuffers(1, &vbo_); vbo_ = 0; }
    if (ibo_) { glDeleteBuffers(1, &ibo_); ibo_ = 0; }
    if (vao_) { glDeleteVertexArrays(1, &vao_); vao_ = 0; }
}

void Renderer::onResize(int w, int h) {
    width_ = w; height_ = h;
}

static void ortho(float* out, float left, float right, float bottom, float top) {
    // column-major
    float rl = 1.0f / (right - left);
    float tb = 1.0f / (top - bottom);
    out[0] = 2.0f * rl; out[4] = 0; out[8] = 0; out[12] = -(right + left) * rl;
    out[1] = 0; out[5] = 2.0f * tb; out[9] = 0; out[13] = -(top + bottom) * tb;
    out[2] = 0; out[6] = 0; out[10] = -1; out[14] = 0;
    out[3] = 0; out[7] = 0; out[11] = 0; out[15] = 1;
}

void Renderer::updateProjection() {
    float proj[16];
    ortho(proj, 0.0f, static_cast<float>(width_), static_cast<float>(height_), 0.0f);
    shader_.use();
    GLint loc = glGetUniformLocation(shader_.id(), "uProj");
    if (loc >= 0) glUniformMatrix4fv(loc, 1, GL_FALSE, proj);
}

void Renderer::begin() {
    spriteCount_ = 0;
    vertexBuffer_.clear();
    // enable blending
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    shader_.use();
    updateProjection();
    resetTextureSlots();
}

void Renderer::drawSprite(GLuint texId, float x, float y, float w, float h,
                          float u0, float v0, float u1, float v1, unsigned int color) {
    // If texture changed, flush current batch
    if (currentTexture_ != 0 && currentTexture_ != texId) {
        // flush
        end();
        begin();
    }
    currentTexture_ = texId;

    if (spriteCount_ >= MaxSprites) {
        end();
        begin();
    }

    // decompose color (0xAARRGGBB) into floats
    float a = ((color >> 24) & 0xFF) / 255.0f;
    float r = ((color >> 16) & 0xFF) / 255.0f;
    float g = ((color >> 8) & 0xFF) / 255.0f;
    float b = ((color >> 0) & 0xFF) / 255.0f;

    // vertices: top-left, top-right, bottom-right, bottom-left (y down)
    float x0 = x;
    float y0 = y;
    float x1 = x + w;
    float y1 = y + h;

    int slot = findOrAssignTextureSlot(texId);
    if (slot < 0) {
        // flush and retry
        end();
        begin();
        slot = findOrAssignTextureSlot(texId);
        if (slot < 0) {
            // cannot batch texture (too many slots)
            return;
        }
    }

    // Append 4 verts with texture slot encoded in the color alpha channel's sign bit (placeholder)
    auto pushVert = [&](float px, float py, float u, float v) {
        vertexBuffer_.push_back(px);
        vertexBuffer_.push_back(py);
        vertexBuffer_.push_back(u);
        vertexBuffer_.push_back(v);
        vertexBuffer_.push_back(r);
        vertexBuffer_.push_back(g);
        vertexBuffer_.push_back(b);
        // store slot as normalized float in [0,1) by dividing by MaxTextureSlots
        vertexBuffer_.push_back(static_cast<float>(slot) / static_cast<float>(MaxTextureSlots));
    };

    pushVert(x0, y0, u0, v0);
    pushVert(x1, y0, u1, v0);
    pushVert(x1, y1, u1, v1);
    pushVert(x0, y1, u0, v1);

    spriteCount_++;
}

void Renderer::end() {
    if (spriteCount_ == 0) return;

    // upload vertices
    glBindVertexArray(vao_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    size_t vbytes = vertexBuffer_.size() * sizeof(float);
    glBufferSubData(GL_ARRAY_BUFFER, 0, vbytes, vertexBuffer_.data());

    // bind texture slots
    for (int i = 0; i < textureSlotCount_; ++i) {
        glActiveTexture(GL_TEXTURE0 + i);
        glBindTexture(GL_TEXTURE_2D, textureSlots_[i]);
    }
    GLint texLoc = glGetUniformLocation(shader_.id(), "uTex");
    if (texLoc >= 0) glUniform1i(texLoc, 0);

    // draw only the used indices
    glDrawElements(GL_TRIANGLES, static_cast<GLsizei>(spriteCount_ * 6), GL_UNSIGNED_SHORT, 0);

    // reset batch
    spriteCount_ = 0;
    vertexBuffer_.clear();
    currentTexture_ = 0;

    glBindVertexArray(0);
}

void Renderer::resetTextureSlots() {
    textureSlotCount_ = 0;
    for (int i = 0; i < MaxTextureSlots; ++i) textureSlots_[i] = 0;
}

int Renderer::findOrAssignTextureSlot(GLuint tex) {
    if (tex == 0) return -1;
    for (int i = 0; i < textureSlotCount_; ++i) if (textureSlots_[i] == tex) return i;
    if (textureSlotCount_ < MaxTextureSlots) {
        textureSlots_[textureSlotCount_] = tex;
        return textureSlotCount_++;
    }
    return -1; // no free slot
}

void Renderer::renderStats() {
    // placeholder: advanced overlay will be implemented later
}

} // namespace future2d

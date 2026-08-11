#include "../include/ResourceManager.h"
#include <fstream>
#include <sstream>
#include <android/log.h>

static std::string readFileToString(const std::string& path) {
    std::ifstream ifs(path);
    if (!ifs) return std::string();
    std::stringstream ss; ss << ifs.rdbuf();
    return ss.str();
}

ResourceManager::ResourceManager() {}
ResourceManager::~ResourceManager() {}

std::shared_ptr<Shader> ResourceManager::loadShaderFromFiles(const std::string& name, const std::string& vertexPath, const std::string& fragmentPath) {
    std::string vsrc = readFileToString(vertexPath);
    std::string fsrc = readFileToString(fragmentPath);
    if (vsrc.empty() || fsrc.empty()) {
        __android_log_print(ANDROID_LOG_WARN, "Future2D", "Failed to read shader files: %s %s", vertexPath.c_str(), fragmentPath.c_str());
        return nullptr;
    }
    auto shader = std::make_shared<Shader>();
    if (!shader->loadFromSource(vsrc, fsrc)) return nullptr;
    shaders_[name] = shader;
    return shader;
}

std::shared_ptr<Shader> ResourceManager::getShader(const std::string& name) {
    auto it = shaders_.find(name);
    if (it == shaders_.end()) return nullptr;
    return it->second;
}

std::shared_ptr<Texture> ResourceManager::createTextureFromRGBA(const std::string& name, int w, int h, const unsigned char* data) {
    auto tex = std::make_shared<Texture>();
    if (!tex->createFromRGBA(w, h, data)) return nullptr;
    textures_[name] = tex;
    return tex;
}

std::shared_ptr<Texture> ResourceManager::getTexture(const std::string& name) {
    auto it = textures_.find(name);
    if (it == textures_.end()) return nullptr;
    return it->second;
}

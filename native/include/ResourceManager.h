#pragma once

#include <string>
#include <memory>
#include <unordered_map>
#include "Shader.h"
#include "Texture.h"

class AssetLoader;

class ResourceManager {
public:
    ResourceManager();
    ~ResourceManager();

    std::shared_ptr<Shader> loadShaderFromFiles(const std::string& name, const std::string& vertexPath, const std::string& fragmentPath);
    std::shared_ptr<Shader> getShader(const std::string& name);

    std::shared_ptr<Texture> createTextureFromRGBA(const std::string& name, int w, int h, const unsigned char* data);
    std::shared_ptr<Texture> getTexture(const std::string& name);

private:
    std::unordered_map<std::string, std::shared_ptr<Shader>> shaders_;
    std::unordered_map<std::string, std::shared_ptr<Texture>> textures_;
};

#include "../include/AssetLoader.h"
#include <android/log.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <sys/stat.h>
#include <fstream>

#define STB_IMAGE_IMPLEMENTATION
#include "../third_party/stb_image.h"

AssetLoader::AssetLoader(AAssetManager* mgr, size_t threads)
    : mgr_(mgr), pool_(threads), cache_(256) {}

AssetLoader::~AssetLoader() {}

std::future<std::shared_ptr<std::vector<uint8_t>>> AssetLoader::loadFileAsync(const std::string& path) {
    auto cached = cache_.get(path);
    if (cached) {
        std::promise<std::shared_ptr<std::vector<uint8_t>>> p;
        p.set_value(*cached);
        return p.get_future();
    }

    return pool_.submit([this, path]() -> std::shared_ptr<std::vector<uint8_t>> {
        std::shared_ptr<std::vector<uint8_t>> data = std::make_shared<std::vector<uint8_t>>();
        if (mgr_) {
            AAsset* asset = AAssetManager_open(mgr_, path.c_str(), AASSET_MODE_BUFFER);
            if (asset) {
                off_t len = AAsset_getLength(asset);
                data->resize(len);
                int read = AAsset_read(asset, data->data(), len);
                AAsset_close(asset);
                if (read <= 0) return nullptr;
                cache_.put(path, data);
                return data;
            }
        }

        // fallback to filesystem
        std::ifstream ifs(path, std::ios::binary | std::ios::ate);
        if (!ifs) return nullptr;
        std::ifstream::pos_type pos = ifs.tellg();
        data->resize(static_cast<size_t>(pos));
        ifs.seekg(0, std::ios::beg);
        ifs.read(reinterpret_cast<char*>(data->data()), pos);
        cache_.put(path, data);
        return data;
    });
}

std::future<std::shared_ptr<ImageAsset>> AssetLoader::loadImageAsync(const std::string& path) {
    // Decode image into RGBA using stb_image
    return pool_.submit([this, path]() -> std::shared_ptr<ImageAsset> {
        auto fileFuture = loadFileAsync(path);
        auto fileData = fileFuture.get();
        if (!fileData) return nullptr;
        int w = 0, h = 0, channels = 0;
        unsigned char* decoded = stbi_load_from_memory(fileData->data(), static_cast<int>(fileData->size()), &w, &h, &channels, 4);
        if (!decoded) {
            __android_log_print(ANDROID_LOG_ERROR, "Future2D", "stb_image failed to decode %s", path.c_str());
            return nullptr;
        }
        size_t datasz = static_cast<size_t>(w) * static_cast<size_t>(h) * 4;
        auto img = std::make_shared<ImageAsset>();
        img->data = std::make_shared<std::vector<unsigned char>>(decoded, decoded + datasz);
        img->channels = 4;
        img->width = w;
        img->height = h;
        stbi_image_free(decoded);
        return img;
    });
}

void AssetLoader::setCacheCapacity(size_t items) {
    // simplistic: recreate cache
    cache_ = LRUCache<std::string, std::shared_ptr<std::vector<uint8_t>>>(items);
}

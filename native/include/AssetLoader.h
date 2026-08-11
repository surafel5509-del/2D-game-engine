#pragma once

#include <string>
#include <vector>
#include <memory>
#include <future>
#include <android/asset_manager.h>
#include "ThreadPool.h"
#include "LRUCache.h"

struct ImageAsset {
    int width = 0;
    int height = 0;
    int channels = 0;
    std::shared_ptr<std::vector<unsigned char>> data;
};

class AssetLoader {
public:
    AssetLoader(AAssetManager* mgr, size_t threads = 2);
    ~AssetLoader();

    std::future<std::shared_ptr<std::vector<uint8_t>>> loadFileAsync(const std::string& path);
    std::future<std::shared_ptr<ImageAsset>> loadImageAsync(const std::string& path);

    void setCacheCapacity(size_t items);

private:
    AAssetManager* mgr_ = nullptr;
    ThreadPool pool_;
    LRUCache<std::string, std::shared_ptr<std::vector<uint8_t>>> cache_;
};

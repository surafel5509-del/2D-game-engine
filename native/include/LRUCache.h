#pragma once

#include <unordered_map>
#include <list>
#include <mutex>
#include <optional>

template<typename Key, typename Value>
class LRUCache {
public:
    LRUCache(size_t capacity = 128): capacity_(capacity) {}

    void put(const Key& key, const Value& value) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = map_.find(key);
        if (it != map_.end()) {
            list_.erase(it->second.second);
            map_.erase(it);
        }
        list_.push_front(key);
        map_[key] = { value, list_.begin() };
        if (map_.size() > capacity_) {
            Key back = list_.back();
            list_.pop_back();
            map_.erase(back);
        }
    }

    std::optional<Value> get(const Key& key) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = map_.find(key);
        if (it == map_.end()) return std::nullopt;
        // move to front
        list_.splice(list_.begin(), list_, it->second.second);
        return it->second.first;
    }

private:
    size_t capacity_;
    std::list<Key> list_;
    std::unordered_map<Key, std::pair<Value, typename std::list<Key>::iterator>> map_;
    std::mutex mutex_;
};

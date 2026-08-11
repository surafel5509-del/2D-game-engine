#include "../include/ThreadPool.h"

ThreadPool::ThreadPool(size_t threads) {
    if (threads == 0) threads = 1;
    for (size_t i = 0; i < threads; ++i) {
        workers_.emplace_back([this]() {
            for (;;) {
                std::function<void()> task;
                {
                    std::unique_lock<std::mutex> lock(this->mutex_);
                    this->cond_.wait(lock, [this]() { return this->stop_ || !this->tasks_.empty(); });
                    if (this->stop_ && this->tasks_.empty()) return;
                    task = std::move(this->tasks_.front());
                    this->tasks_.pop();
                }
                task();
            }
        });
    }
}

ThreadPool::~ThreadPool() {
    {
        std::unique_lock<std::mutex> lock(mutex_);
        stop_ = true;
    }
    cond_.notify_all();
    for (auto &worker : workers_) worker.join();
}

template<class F, class... Args>
auto ThreadPool::submit(F&& f, Args&&... args) -> std::future<typename std::invoke_result_t<F, Args...>> {
    using return_type = typename std::invoke_result_t<F, Args...>;
    auto task = std::make_shared<std::packaged_task<return_type()>>(std::bind(std::forward<F>(f), std::forward<Args>(args)...));
    std::future<return_type> res = task->get_future();
    {
        std::unique_lock<std::mutex> lock(mutex_);
        if (stop_) throw std::runtime_error("submit on stopped ThreadPool");
        tasks_.emplace([task]() { (*task)(); });
    }
    cond_.notify_one();
    return res;
}

// Explicitly instantiate common template usage to avoid missing symbols in separate compilation units.
template std::future<void> ThreadPool::submit(std::function<void()> &&);

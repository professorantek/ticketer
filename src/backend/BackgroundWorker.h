#pragma once

#include <thread>
#include <atomic>

// ======================================================
// Wymóg: Parallel programming (std::thread)
// - wątek diagnostyczny aplikacji, działający w tle
// ======================================================
class BackgroundWorker {
public:
    BackgroundWorker();
    void start();
    void stop();
    ~BackgroundWorker();

private:
    std::atomic_bool running_;
    std::thread worker_;
};

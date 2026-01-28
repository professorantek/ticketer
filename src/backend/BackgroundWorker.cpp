#include "BackgroundWorker.h"
#include <iostream>
#include <chrono>

BackgroundWorker::BackgroundWorker()
    : running_(false) {}

void BackgroundWorker::start() {
    running_.store(true);
    worker_ = std::thread([this]() {
        while (running_.load()) {
            std::this_thread::sleep_for(std::chrono::seconds(5));
            std::cerr << "[diag] server alive\n";
        }
    });
}

void BackgroundWorker::stop() {
    running_.store(false);
    if (worker_.joinable()) worker_.join();
}

BackgroundWorker::~BackgroundWorker() {
    stop();
}

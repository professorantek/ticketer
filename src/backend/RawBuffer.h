#pragma once

#include <cstddef>

// ======================================================
// Wymóg: Dynamic allocation + Destructor (non-empty)
// ======================================================
class RawBuffer {
public:
    explicit RawBuffer(size_t size);
    ~RawBuffer();

    RawBuffer(const RawBuffer&) = delete;
    RawBuffer& operator=(const RawBuffer&) = delete;

    RawBuffer(RawBuffer&& other) noexcept;
    RawBuffer& operator=(RawBuffer&& other) noexcept;

    char* data();
    size_t size() const;

private:
    size_t size_{0};
    char* data_{nullptr};
};

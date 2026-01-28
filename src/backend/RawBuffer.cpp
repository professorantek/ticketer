#include "RawBuffer.h"

RawBuffer::RawBuffer(size_t size)
    : size_(size), data_(new char[size]()) {} // dynamic allocation (new)

RawBuffer::~RawBuffer() { // non-empty destructor
    delete[] data_;
    data_ = nullptr;
    size_ = 0;
}

RawBuffer::RawBuffer(RawBuffer&& other) noexcept
    : size_(other.size_), data_(other.data_) {
    other.size_ = 0;
    other.data_ = nullptr;
}

RawBuffer& RawBuffer::operator=(RawBuffer&& other) noexcept {
    if (this != &other) {
        delete[] data_;
        size_ = other.size_;
        data_ = other.data_;
        other.size_ = 0;
        other.data_ = nullptr;
    }
    return *this;
}

char* RawBuffer::data() { return data_; }
size_t RawBuffer::size() const { return size_; }

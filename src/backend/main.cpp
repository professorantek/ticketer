// main.cpp
// Build with CMake or manually:
// g++ -std=c++20 -O2 -pthread main.cpp AppServer.cpp BackgroundWorker.cpp Controllers.cpp Services.cpp FileRepositories.cpp Models.cpp JsonUtils.cpp RawBuffer.cpp -o server

#include "AppServer.h"

#ifndef UNIT_TESTS

int main()
{
    AppServer app("127.0.0.1", 8080);
    app.start();
    return 0;
}

#endif

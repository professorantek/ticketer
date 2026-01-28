cmake_minimum_required(VERSION 3.15)
project(ticketer_backend CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find threads package for pthread support
find_package(Threads REQUIRED)

# Common source files (everything except main.cpp and tests.cpp)
set(COMMON_SOURCES
    RawBuffer.cpp
    JsonUtils.cpp
    Models.cpp
    FileRepositories.cpp
    Services.cpp
    Controllers.cpp
    BackgroundWorker.cpp
    AppServer.cpp
)

# Server executable
add_executable(server
    main.cpp
    ${COMMON_SOURCES}
)

target_link_libraries(server PRIVATE Threads::Threads)

# Compiler flags
target_compile_options(server PRIVATE
    -O2
    -pthread
    -Wall
    -Wextra
)

# Tests executable
add_executable(tests
    tests.cpp
    ${COMMON_SOURCES}
)

target_compile_definitions(tests PRIVATE UNIT_TESTS)
target_link_libraries(tests PRIVATE Threads::Threads)

target_compile_options(tests PRIVATE
    -O2
    -pthread
    -Wall
    -Wextra
)

# Install targets (optional)
install(TARGETS server DESTINATION bin)
install(TARGETS tests DESTINATION bin)

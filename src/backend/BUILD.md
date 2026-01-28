# Backend Build Instructions

## Project Structure

The backend has been split into the following files:

- **RawBuffer.h/cpp** - Dynamic memory management class
- **JsonUtils.h/cpp** - JSON parsing utilities (with templates)
- **Models.h/cpp** - Data models (User, Ticket, Purchase) with operator overloading
- **Repositories.h** - Repository interfaces (inheritance & polymorphism)
- **FileRepositories.h/cpp** - File-based repository implementations
- **Services.h/cpp** - Business logic services (with enum classes)
- **Controllers.h/cpp** - HTTP request controllers
- **BackgroundWorker.h/cpp** - Background thread for diagnostics
- **AppServer.h/cpp** - Main application server class
- **main.cpp** - Server entry point
- **tests.cpp** - Unit tests

## Building with CMake

### 1. Create build directory:
```bash
cd /Users/antoni/Coding/OOP/ticketer/src/backend
mkdir build
cd build
```

### 2. Configure CMake:
```bash
cmake ..
```

### 3. Build the project:
```bash
cmake --build .
```

This will create two executables:
- `server` - the main application
- `tests` - unit test suite

### 4. Run the server:
```bash
./server
```

### 5. Run the tests:
```bash
./tests
```

## Building Manually (without CMake)

### Build server:
```bash
g++ -std=c++20 -O2 -pthread main.cpp AppServer.cpp BackgroundWorker.cpp Controllers.cpp Services.cpp FileRepositories.cpp Models.cpp JsonUtils.cpp RawBuffer.cpp -o server
```

### Build tests:
```bash
g++ -std=c++20 -O2 -pthread -DUNIT_TESTS tests.cpp AppServer.cpp BackgroundWorker.cpp Controllers.cpp Services.cpp FileRepositories.cpp Models.cpp JsonUtils.cpp RawBuffer.cpp -o tests
```

## API Endpoints

The server runs on `http://127.0.0.1:8080` and provides:

- `GET  /random` - Get random number
- `POST /register` - Register new user
- `POST /login` - Login user
- `POST /user/get` - Get user info
- `POST /tickets/create` - Create ticket (admin)
- `POST /tickets/update` - Update ticket (admin)
- `POST /tickets/delete` - Delete ticket (admin)
- `GET  /tickets` - List all tickets
- `GET  /tickets/{id}` - Get ticket by ID
- `POST /purchase` - Purchase tickets
- `POST /purchases/by-user` - List user purchases

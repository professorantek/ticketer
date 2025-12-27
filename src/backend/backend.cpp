#include <iostream>
#include <random>
#include <string>
#include "httplib.h"

// Serwis (logika biznesowa) - OOP
class RandomNumberService {
public:
    RandomNumberService()
        : rng_(std::random_device{}()),
          dist_(1, 20) {}

    int generate() {
        return dist_(rng_);
    }

private:
    std::mt19937 rng_;
    std::uniform_int_distribution<int> dist_;
};

// Kontroler (warstwa HTTP) - OOP
class RandomController {
public:
    explicit RandomController(RandomNumberService& service)
        : service_(service) {}

    void registerRoutes(httplib::Server& server) {
        server.Get("/random", [this](const httplib::Request&, httplib::Response& res) {
            int value = service_.generate();
            res.set_content(std::to_string(value), "text/plain; charset=utf-8");
        });
    }

private:
    RandomNumberService& service_;
};

// Aplikacja serwera (kompozycja) - OOP
class AppServer {
public:
    AppServer(std::string host, int port)
        : host_(std::move(host)), port_(port) {}

    void start() {
        RandomNumberService randomService;
        RandomController randomController(randomService);
        randomController.registerRoutes(server_);

        std::cout << "Listening on http://" << host_ << ":" << port_ << "\n";
        std::cout << "Endpoint: GET /random\n";

        server_.listen(host_.c_str(), port_);
    }

private:
    std::string host_;
    int port_;
    httplib::Server server_;
};

int main() {
    AppServer app("127.0.0.1", 8080);
    app.start();
    return 0;
}

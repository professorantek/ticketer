 // backend.cpp
// Build (server):  g++ -std=c++20 -O2 -pthread backend.cpp -o server
// Run:             ./server
//
// Build (tests):   g++ -std=c++20 -O2 -pthread -DUNIT_TESTS backend.cpp -o tests
// Run tests:       ./tests

#include <iostream>
#include <random>
#include <string>
#include "httplib.h"

#include <fstream>
#include <sstream>
#include <optional>
#include <mutex>
#include <cctype>
#include <vector>
#include <map>
#include <algorithm>   // std::sort, std::count_if
#include <thread>      // std::thread
#include <atomic>      // std::atomic_bool
#include <chrono>      // std::chrono
#include <memory>      // std::unique_ptr
#include <stdexcept>   // std::runtime_error
#include <cassert>     // unit tests

// ======================================================
// Wymóg: Dynamic allocation + Destructor (non-empty)
// ======================================================
class RawBuffer {
public:
    explicit RawBuffer(size_t size)
        : size_(size), data_(new char[size]()) {} // dynamic allocation (new)

    ~RawBuffer() { // non-empty destructor
        delete[] data_;
        data_ = nullptr;
        size_ = 0;
    }

    RawBuffer(const RawBuffer&) = delete;
    RawBuffer& operator=(const RawBuffer&) = delete;

    RawBuffer(RawBuffer&& other) noexcept
        : size_(other.size_), data_(other.data_) {
        other.size_ = 0;
        other.data_ = nullptr;
    }
    RawBuffer& operator=(RawBuffer&& other) noexcept {
        if (this != &other) {
            delete[] data_;
            size_ = other.size_;
            data_ = other.data_;
            other.size_ = 0;
            other.data_ = nullptr;
        }
        return *this;
    }

    char* data() { return data_; }
    size_t size() const { return size_; }

private:
    size_t size_{0};
    char* data_{nullptr};
};

// ======================================================
// Minimalne narzędzia JSON (bez bibliotek)
// + Wymóg: Generic (template)
// ======================================================
static std::optional<std::string> extractJsonStringField(const std::string& body, const std::string& key) {
    const std::string pattern = "\"" + key + "\"";
    size_t pos = body.find(pattern);
    if (pos == std::string::npos) return std::nullopt;

    pos = body.find(':', pos);
    if (pos == std::string::npos) return std::nullopt;

    pos = body.find('"', pos);
    if (pos == std::string::npos) return std::nullopt;

    size_t start = pos + 1;
    size_t end = body.find('"', start);
    if (end == std::string::npos) return std::nullopt;

    return body.substr(start, end - start);
}

static std::optional<double> extractJsonNumberField(const std::string& body, const std::string& key) {
    const std::string pattern = "\"" + key + "\"";
    size_t pos = body.find(pattern);
    if (pos == std::string::npos) return std::nullopt;

    pos = body.find(':', pos);
    if (pos == std::string::npos) return std::nullopt;

    pos++;
    while (pos < body.size() && std::isspace(static_cast<unsigned char>(body[pos]))) pos++;

    size_t end = pos;
    while (end < body.size()) {
        char c = body[end];
        if (!(std::isdigit(static_cast<unsigned char>(c)) || c == '.' || c == '-')) break;
        end++;
    }
    if (end == pos) return std::nullopt;

    try {
        return std::stod(body.substr(pos, end - pos));
    } catch (...) {
        return std::nullopt;
    }
}

// Generic: template parser liczby całkowitej z JSON (na bazie extractJsonNumberField)
template <typename IntT>
static std::optional<IntT> extractJsonIntFieldT(const std::string& body, const std::string& key) {
    auto numOpt = extractJsonNumberField(body, key);
    if (!numOpt) return std::nullopt;
    return static_cast<IntT>(*numOpt);
}

static std::optional<long long> extractJsonIntField(const std::string& body, const std::string& key) {
    return extractJsonIntFieldT<long long>(body, key);
}

// proste escapowanie do JSON
static std::string escapeJsonString(std::string s) {
    size_t pos = 0;
    while ((pos = s.find('"', pos)) != std::string::npos) {
        s.insert(pos, "\\");
        pos += 2;
    }
    return s;
}

// ======================================================
// Modele + operator overloading
// ======================================================
struct User {
    std::string login;
    std::string password; // (zgodnie z Twoim wymaganiem, choć to niebezpieczne w realnym systemie)
};

struct Ticket {
    long long id;
    double price;
    std::string name;
};

struct Purchase {
    long long purchaseId;
    long long ticketId;
    std::string login;
};

// Wymóg: Operator overloading
std::ostream& operator<<(std::ostream& os, const Ticket& t) {
    os << "Ticket{id=" << t.id << ", price=" << t.price << ", name=" << t.name << "}";
    return os;
}
std::ostream& operator<<(std::ostream& os, const User& u) {
    os << "User{login=" << u.login << ", password=" << u.password << "}";
    return os;
}

// ======================================================
// Wymóg: Inheritance + Virtual method + Polymorphism
// Tworzymy interfejsy repozytoriów (klasy bazowe)
// ======================================================
class IUserRepository {
public:
    virtual ~IUserRepository() = default;
    virtual bool exists(const std::string& login) const = 0;
    virtual bool verifyCredentials(const std::string& login, const std::string& password) const = 0;
    virtual bool addUser(const std::string& login, const std::string& password) = 0;
    virtual std::optional<User> getUserByLogin(const std::string& login) const = 0;
};

class ITicketRepository {
public:
    virtual ~ITicketRepository() = default;
    virtual long long generateNextId() const = 0;
    virtual void addTicket(const Ticket& t) = 0;
    virtual bool deleteById(long long id) = 0;
    virtual std::vector<Ticket> listAll() const = 0;
    virtual bool existsById(long long id) const = 0;
    virtual std::optional<Ticket> getById(long long id) const = 0;
};

class IPurchaseRepository {
public:
    virtual ~IPurchaseRepository() = default;
    virtual long long generateNextPurchaseId() const = 0;
    virtual void addPurchase(const Purchase& p) = 0;
    virtual std::vector<std::pair<long long, long long>> getTicketCountsForLogin(const std::string& login) const = 0;
};

// ======================================================
// Implementacje plikowe repozytoriów (dziedziczenie)
// + File I/O
// + Error handling (exceptions)
// ======================================================
class FileUserRepository final : public IUserRepository {
public:
    explicit FileUserRepository(std::string dbPath)
        : dbPath_(std::move(dbPath)) {}

    bool exists(const std::string& login) const override {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ifstream in(dbPath_);
        if (!in.is_open()) return false;

        std::string line;
        while (std::getline(in, line)) {
            std::string fileLogin, filePass;
            if (parseLine(line, fileLogin, filePass) && fileLogin == login) return true;
        }
        return false;
    }

    bool verifyCredentials(const std::string& login, const std::string& password) const override {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ifstream in(dbPath_);
        if (!in.is_open()) return false;

        std::string line;
        while (std::getline(in, line)) {
            std::string fileLogin, filePass;
            if (parseLine(line, fileLogin, filePass) && fileLogin == login) {
                return filePass == password;
            }
        }
        return false;
    }

    bool addUser(const std::string& login, const std::string& password) override {
        std::lock_guard<std::mutex> lock(mtx_);

        // ponowna walidacja pod lockiem
        if (existsUnlocked(login)) return false;

        std::ofstream out(dbPath_, std::ios::app);
        if (!out.is_open()) throw std::runtime_error("Cannot open users db file for writing");

        out << login << " " << password << "\n";
        return true;
    }

    std::optional<User> getUserByLogin(const std::string& login) const override {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ifstream in(dbPath_);
        if (!in.is_open()) return std::nullopt;

        std::string line;
        while (std::getline(in, line)) {
            std::string fileLogin, filePass;
            if (parseLine(line, fileLogin, filePass) && fileLogin == login) {
                return User{fileLogin, filePass};
            }
        }
        return std::nullopt;
    }

private:
    bool existsUnlocked(const std::string& login) const {
        std::ifstream in(dbPath_);
        if (!in.is_open()) return false;

        std::string line;
        while (std::getline(in, line)) {
            std::string fileLogin, filePass;
            if (parseLine(line, fileLogin, filePass) && fileLogin == login) return true;
        }
        return false;
    }

    static bool parseLine(const std::string& line, std::string& login, std::string& pass) {
        std::istringstream iss(line);
        iss >> login >> pass;
        return !iss.fail();
    }

    std::string dbPath_;
    mutable std::mutex mtx_;
};

class FileTicketRepository final : public ITicketRepository {
public:
    explicit FileTicketRepository(std::string dbPath)
        : dbPath_(std::move(dbPath)) {}

    long long generateNextId() const override {
        std::lock_guard<std::mutex> lock(mtx_);
        long long maxId = 0;

        std::ifstream in(dbPath_);
        if (!in.is_open()) return 1;

        std::string line;
        while (std::getline(in, line)) {
            Ticket t;
            if (parseLine(line, t)) maxId = std::max(maxId, t.id);
        }
        return maxId + 1;
    }

    void addTicket(const Ticket& t) override {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ofstream out(dbPath_, std::ios::app);
        if (!out.is_open()) throw std::runtime_error("Cannot open tickets db file for writing");
        out << t.id << " " << t.price << " " << t.name << "\n";
    }

    bool deleteById(long long id) override {
        std::lock_guard<std::mutex> lock(mtx_);

        std::ifstream in(dbPath_);
        if (!in.is_open()) return false;

        std::vector<std::string> kept;
        bool removed = false;

        std::string line;
        while (std::getline(in, line)) {
            Ticket t;
            if (parseLine(line, t) && t.id == id) {
                removed = true;
                continue;
            }
            kept.push_back(line);
        }
        in.close();

        if (!removed) return false;

        std::ofstream out(dbPath_, std::ios::trunc);
        if (!out.is_open()) throw std::runtime_error("Cannot open tickets db file for rewriting");
        for (const auto& l : kept) out << l << "\n";
        return true;
    }

    std::vector<Ticket> listAll() const override {
        std::lock_guard<std::mutex> lock(mtx_);
        std::vector<Ticket> tickets;

        std::ifstream in(dbPath_);
        if (!in.is_open()) return tickets;

        std::string line;
        while (std::getline(in, line)) {
            Ticket t;
            if (parseLine(line, t)) tickets.push_back(t);
        }

        // Wymóg: STL Algorithm (np. sort)
        std::sort(tickets.begin(), tickets.end(), [](const Ticket& a, const Ticket& b) {
            return a.id < b.id;
        });

        return tickets;
    }

    bool existsById(long long id) const override {
        return static_cast<bool>(getById(id));
    }

    std::optional<Ticket> getById(long long id) const override {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ifstream in(dbPath_);
        if (!in.is_open()) return std::nullopt;

        std::string line;
        while (std::getline(in, line)) {
            Ticket t;
            if (parseLine(line, t) && t.id == id) return t;
        }
        return std::nullopt;
    }

private:
    static bool parseLine(const std::string& line, Ticket& t) {
        std::istringstream iss(line);
        if (!(iss >> t.id >> t.price)) return false;

        std::string rest;
        std::getline(iss, rest);
        if (!rest.empty() && rest[0] == ' ') rest.erase(0, 1);
        t.name = rest;
        return !t.name.empty();
    }

    std::string dbPath_;
    mutable std::mutex mtx_;
};

class FilePurchaseRepository final : public IPurchaseRepository {
public:
    explicit FilePurchaseRepository(std::string dbPath)
        : dbPath_(std::move(dbPath)) {}

    long long generateNextPurchaseId() const override {
        std::lock_guard<std::mutex> lock(mtx_);
        long long maxId = 0;

        std::ifstream in(dbPath_);
        if (!in.is_open()) return 1;

        std::string line;
        while (std::getline(in, line)) {
            Purchase p;
            if (parseLine(line, p)) maxId = std::max(maxId, p.purchaseId);
        }
        return maxId + 1;
    }

    void addPurchase(const Purchase& p) override {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ofstream out(dbPath_, std::ios::app);
        if (!out.is_open()) throw std::runtime_error("Cannot open purchases db file for writing");
        out << p.purchaseId << " " << p.ticketId << " " << p.login << "\n";
    }

    std::vector<std::pair<long long, long long>> getTicketCountsForLogin(const std::string& login) const override {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ifstream in(dbPath_);
        if (!in.is_open()) return {};

        std::map<long long, long long> countsMap; // ticketId -> qty

        std::string line;
        while (std::getline(in, line)) {
            Purchase p;
            if (!parseLine(line, p)) continue;
            if (p.login != login) continue;
            countsMap[p.ticketId]++;
        }

        std::vector<std::pair<long long, long long>> out;
        out.reserve(countsMap.size());
        for (const auto& kv : countsMap) out.push_back(kv);

        // Wymóg: STL Algorithm - np. count_if (tu demonstracyjnie, bez wpływu na logikę)
        (void)std::count_if(out.begin(), out.end(), [](const auto& kv) { return kv.second > 0; });

        return out;
    }

private:
    static bool parseLine(const std::string& line, Purchase& p) {
        std::istringstream iss(line);
        return static_cast<bool>(iss >> p.purchaseId >> p.ticketId >> p.login);
    }

    std::string dbPath_;
    mutable std::mutex mtx_;
};

// ======================================================
// Serwisy (logika biznesowa)
// + enum class
// ======================================================
class AuthService {
public:
    explicit AuthService(IUserRepository& repo)
        : repo_(repo) {}

    enum class RegisterResult { Ok, LoginTaken, InvalidInput, StorageError };
    enum class LoginResult { Ok, InvalidInput, InvalidCredentials, StorageError };

    RegisterResult registerUser(const std::string& login, const std::string& password) {
        if (!isValid(login) || !isValid(password)) return RegisterResult::InvalidInput;

        try {
            if (repo_.exists(login)) return RegisterResult::LoginTaken;
            bool added = repo_.addUser(login, password);
            return added ? RegisterResult::Ok : RegisterResult::LoginTaken;
        } catch (...) {
            return RegisterResult::StorageError;
        }
    }

    LoginResult loginUser(const std::string& login, const std::string& password) {
        if (!isValid(login) || !isValid(password)) return LoginResult::InvalidInput;

        try {
            bool ok = repo_.verifyCredentials(login, password);
            return ok ? LoginResult::Ok : LoginResult::InvalidCredentials;
        } catch (...) {
            return LoginResult::StorageError;
        }
    }

    std::optional<User> getUserIfAuthorized(const std::string& login, const std::string& password) {
        try {
            if (!repo_.verifyCredentials(login, password)) return std::nullopt;
            return repo_.getUserByLogin(login);
        } catch (...) {
            return std::nullopt;
        }
    }

private:
    static bool isValid(const std::string& s) {
        if (s.size() < 3 || s.size() > 32) return false;
        for (unsigned char c : s) {
            if (std::isspace(c) || std::iscntrl(c)) return false;
        }
        return true;
    }

    IUserRepository& repo_;
};

class TicketService {
public:
    explicit TicketService(ITicketRepository& repo)
        : repo_(repo) {}

    enum class CreateResult { Ok, InvalidInput, Forbidden, StorageError };
    enum class DeleteResult { Ok, NotFound, Forbidden, InvalidInput, StorageError };

    CreateResult createTicket(const std::string& adminPassword, double price, const std::string& name, long long& outId) {
        if (adminPassword != kAdminPassword) return CreateResult::Forbidden;
        if (name.empty() || name.size() > 100) return CreateResult::InvalidInput;
        if (!(price > 0.0) || price > 1'000'000.0) return CreateResult::InvalidInput;

        try {
            outId = repo_.generateNextId();
            repo_.addTicket(Ticket{outId, price, name});
            return CreateResult::Ok;
        } catch (...) {
            return CreateResult::StorageError;
        }
    }

    DeleteResult deleteTicket(const std::string& adminPassword, long long id) {
        if (adminPassword != kAdminPassword) return DeleteResult::Forbidden;
        if (id <= 0) return DeleteResult::InvalidInput;

        try {
            bool ok = repo_.deleteById(id);
            return ok ? DeleteResult::Ok : DeleteResult::NotFound;
        } catch (...) {
            return DeleteResult::StorageError;
        }
    }

    std::vector<Ticket> getAllTickets() {
        try { return repo_.listAll(); }
        catch (...) { return {}; }
    }

    std::optional<Ticket> getTicketById(long long id) {
        try { return repo_.getById(id); }
        catch (...) { return std::nullopt; }
    }

private:
    static constexpr const char* kAdminPassword = "12345";
    ITicketRepository& repo_;
};

class PurchaseService {
public:
    PurchaseService(IUserRepository& userRepo, ITicketRepository& ticketRepo, IPurchaseRepository& purchaseRepo)
        : userRepo_(userRepo), ticketRepo_(ticketRepo), purchaseRepo_(purchaseRepo) {}

    enum class PurchaseResult { Ok, InvalidInput, Unauthorized, TicketNotFound, StorageError };
    enum class ListResult { Ok, InvalidInput, Unauthorized, StorageError };

    PurchaseResult purchase(long long ticketId,
                            const std::string& login,
                            const std::string& password,
                            long long quantity) {
        if (ticketId <= 0) return PurchaseResult::InvalidInput;
        if (login.empty() || password.empty()) return PurchaseResult::InvalidInput;
        if (quantity <= 0 || quantity > 1000) return PurchaseResult::InvalidInput;

        try {
            if (!userRepo_.verifyCredentials(login, password)) return PurchaseResult::Unauthorized;
            if (!ticketRepo_.existsById(ticketId)) return PurchaseResult::TicketNotFound;

            for (long long i = 0; i < quantity; i++) {
                long long newPurchaseId = purchaseRepo_.generateNextPurchaseId();
                purchaseRepo_.addPurchase(Purchase{newPurchaseId, ticketId, login});
            }

            return PurchaseResult::Ok;
        } catch (...) {
            return PurchaseResult::StorageError;
        }
    }

    ListResult listUserTickets(const std::string& login,
                              const std::string& password,
                              std::vector<std::pair<long long, long long>>& outCounts) {
        if (login.empty() || password.empty()) return ListResult::InvalidInput;

        try {
            if (!userRepo_.verifyCredentials(login, password)) return ListResult::Unauthorized;
            outCounts = purchaseRepo_.getTicketCountsForLogin(login);
            return ListResult::Ok;
        } catch (...) {
            return ListResult::StorageError;
        }
    }

private:
    IUserRepository& userRepo_;
    ITicketRepository& ticketRepo_;
    IPurchaseRepository& purchaseRepo_;
};

// ======================================================
// Kontrolery HTTP
// ======================================================
class AuthController {
public:
    explicit AuthController(AuthService& service)
        : service_(service) {}

    void registerRoutes(httplib::Server& server) {
        server.Post("/register", [this](const httplib::Request& req, httplib::Response& res) {
            auto loginOpt = extractJsonStringField(req.body, "login");
            auto passOpt  = extractJsonStringField(req.body, "password");
            if (!loginOpt || !passOpt) {
                res.status = 400;
                res.set_content("Invalid JSON. Expected {\"login\":\"...\",\"password\":\"...\"}",
                                "text/plain; charset=utf-8");
                return;
            }

            auto result = service_.registerUser(*loginOpt, *passOpt);
            switch (result) {
                case AuthService::RegisterResult::Ok:
                    res.status = 201; res.set_content("User registered", "text/plain; charset=utf-8"); break;
                case AuthService::RegisterResult::LoginTaken:
                    res.status = 409; res.set_content("Login already taken", "text/plain; charset=utf-8"); break;
                case AuthService::RegisterResult::InvalidInput:
                    res.status = 400; res.set_content("Invalid login or password (3-32 chars, no spaces)", "text/plain; charset=utf-8"); break;
                default:
                    res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
            }
        });

        server.Post("/login", [this](const httplib::Request& req, httplib::Response& res) {
            auto loginOpt = extractJsonStringField(req.body, "login");
            auto passOpt  = extractJsonStringField(req.body, "password");
            if (!loginOpt || !passOpt) {
                res.status = 400;
                res.set_content("Invalid JSON. Expected {\"login\":\"...\",\"password\":\"...\"}",
                                "text/plain; charset=utf-8");
                return;
            }

            auto result = service_.loginUser(*loginOpt, *passOpt);
            switch (result) {
                case AuthService::LoginResult::Ok:
                    res.status = 200; res.set_content("Login OK", "text/plain; charset=utf-8"); break;
                case AuthService::LoginResult::InvalidInput:
                    res.status = 400; res.set_content("Invalid login or password (3-32 chars, no spaces)", "text/plain; charset=utf-8"); break;
                case AuthService::LoginResult::InvalidCredentials:
                    res.status = 401; res.set_content("Invalid login or password", "text/plain; charset=utf-8"); break;
                default:
                    res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
            }
        });

        // POST /user/get  (autoryzowane login+password)
        server.Post("/user/get", [this](const httplib::Request& req, httplib::Response& res) {
            auto loginOpt = extractJsonStringField(req.body, "login");
            auto passOpt  = extractJsonStringField(req.body, "password");
            if (!loginOpt || !passOpt) {
                res.status = 400;
                res.set_content("Invalid JSON. Expected {\"login\":\"...\",\"password\":\"...\"}",
                                "text/plain; charset=utf-8");
                return;
            }

            auto userOpt = service_.getUserIfAuthorized(*loginOpt, *passOpt);
            if (!userOpt) {
                res.status = 401;
                res.set_content("Invalid login or password", "text/plain; charset=utf-8");
                return;
            }

            std::string json = "{";
            json += "\"login\":\"" + escapeJsonString(userOpt->login) + "\",";
            json += "\"password\":\"" + escapeJsonString(userOpt->password) + "\"";
            json += "}";

            res.status = 200;
            res.set_content(json, "application/json; charset=utf-8");
        });
    }

private:
    AuthService& service_;
};

class TicketController {
public:
    explicit TicketController(TicketService& service)
        : service_(service) {}

    void registerRoutes(httplib::Server& server) {
        server.Post("/tickets/create", [this](const httplib::Request& req, httplib::Response& res) {
            auto adminOpt = extractJsonStringField(req.body, "adminPassword");
            auto nameOpt  = extractJsonStringField(req.body, "name");
            auto priceOpt = extractJsonNumberField(req.body, "price");

            if (!adminOpt || !nameOpt || !priceOpt) {
                res.status = 400;
                res.set_content("Invalid JSON. Expected {\"adminPassword\":\"12345\",\"price\":...,\"name\":\"...\"}",
                                "text/plain; charset=utf-8");
                return;
            }

            long long newId = 0;
            auto result = service_.createTicket(*adminOpt, *priceOpt, *nameOpt, newId);

            switch (result) {
                case TicketService::CreateResult::Ok:
                    res.status = 201;
                    res.set_content("Ticket created: " + std::to_string(newId), "text/plain; charset=utf-8");
                    break;
                case TicketService::CreateResult::Forbidden:
                    res.status = 403; res.set_content("Forbidden", "text/plain; charset=utf-8"); break;
                case TicketService::CreateResult::InvalidInput:
                    res.status = 400; res.set_content("Invalid price or name", "text/plain; charset=utf-8"); break;
                default:
                    res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
            }
        });

        server.Post("/tickets/delete", [this](const httplib::Request& req, httplib::Response& res) {
            auto adminOpt = extractJsonStringField(req.body, "adminPassword");
            auto idOpt    = extractJsonIntField(req.body, "id");
            if (!adminOpt || !idOpt) {
                res.status = 400;
                res.set_content("Invalid JSON. Expected {\"adminPassword\":\"12345\",\"id\":...}",
                                "text/plain; charset=utf-8");
                return;
            }

            auto result = service_.deleteTicket(*adminOpt, *idOpt);
            switch (result) {
                case TicketService::DeleteResult::Ok:
                    res.status = 200; res.set_content("Ticket deleted", "text/plain; charset=utf-8"); break;
                case TicketService::DeleteResult::NotFound:
                    res.status = 404; res.set_content("Ticket not found", "text/plain; charset=utf-8"); break;
                case TicketService::DeleteResult::Forbidden:
                    res.status = 403; res.set_content("Forbidden", "text/plain; charset=utf-8"); break;
                case TicketService::DeleteResult::InvalidInput:
                    res.status = 400; res.set_content("Invalid id", "text/plain; charset=utf-8"); break;
                default:
                    res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
            }
        });

        // GET /tickets
        server.Get("/tickets", [this](const httplib::Request&, httplib::Response& res) {
            auto tickets = service_.getAllTickets();

            std::string json = "[";
            for (size_t i = 0; i < tickets.size(); i++) {
                const auto& t = tickets[i];
                json += "{";
                json += "\"id\":" + std::to_string(t.id) + ",";
                json += "\"price\":" + std::to_string(t.price) + ",";
                json += "\"name\":\"" + escapeJsonString(t.name) + "\"";
                json += "}";
                if (i + 1 < tickets.size()) json += ",";
            }
            json += "]";
            res.status = 200;
            res.set_content(json, "application/json; charset=utf-8");
        });

        // GET /tickets/{id}
        server.Get(R"(/tickets/(\d+))", [this](const httplib::Request& req, httplib::Response& res) {
            long long id = 0;
            try { id = std::stoll(req.matches[1].str()); }
            catch (...) {
                res.status = 400; res.set_content("Invalid ticket id", "text/plain; charset=utf-8"); return;
            }

            auto tOpt = service_.getTicketById(id);
            if (!tOpt) {
                res.status = 404; res.set_content("Ticket not found", "text/plain; charset=utf-8"); return;
            }

            std::string json = "{";
            json += "\"id\":" + std::to_string(tOpt->id) + ",";
            json += "\"price\":" + std::to_string(tOpt->price) + ",";
            json += "\"name\":\"" + escapeJsonString(tOpt->name) + "\"";
            json += "}";

            res.status = 200;
            res.set_content(json, "application/json; charset=utf-8");
        });
    }

private:
    TicketService& service_;
};

class PurchaseController {
public:
    explicit PurchaseController(PurchaseService& service)
        : service_(service) {}

    void registerRoutes(httplib::Server& server) {
        // POST /purchase
        // {"idBiletu":2,"quantity":3,"login":"jan","password":"haslo123"}
        server.Post("/purchase", [this](const httplib::Request& req, httplib::Response& res) {
            auto ticketIdOpt = extractJsonIntField(req.body, "idBiletu");
            auto qtyOpt      = extractJsonIntField(req.body, "quantity");
            auto loginOpt    = extractJsonStringField(req.body, "login");
            auto passOpt     = extractJsonStringField(req.body, "password");

            if (!ticketIdOpt || !qtyOpt || !loginOpt || !passOpt) {
                res.status = 400;
                res.set_content("Invalid JSON. Expected {\"idBiletu\":...,\"quantity\":...,\"login\":\"...\",\"password\":\"...\"}",
                                "text/plain; charset=utf-8");
                return;
            }

            auto result = service_.purchase(*ticketIdOpt, *loginOpt, *passOpt, *qtyOpt);
            switch (result) {
                case PurchaseService::PurchaseResult::Ok:
                    res.status = 201; res.set_content("Purchases saved", "text/plain; charset=utf-8"); break;
                case PurchaseService::PurchaseResult::Unauthorized:
                    res.status = 401; res.set_content("Invalid login or password", "text/plain; charset=utf-8"); break;
                case PurchaseService::PurchaseResult::TicketNotFound:
                    res.status = 404; res.set_content("Ticket not found", "text/plain; charset=utf-8"); break;
                case PurchaseService::PurchaseResult::InvalidInput:
                    res.status = 400; res.set_content("Invalid input", "text/plain; charset=utf-8"); break;
                default:
                    res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
            }
        });

        // POST /purchases/by-user
        // {"login":"jan","password":"haslo123"} -> [{"idBiletu":2,"quantity":3}, ...]
        server.Post("/purchases/by-user", [this](const httplib::Request& req, httplib::Response& res) {
            auto loginOpt = extractJsonStringField(req.body, "login");
            auto passOpt  = extractJsonStringField(req.body, "password");
            if (!loginOpt || !passOpt) {
                res.status = 400;
                res.set_content("Invalid JSON. Expected {\"login\":\"...\",\"password\":\"...\"}",
                                "text/plain; charset=utf-8");
                return;
            }

            std::vector<std::pair<long long, long long>> counts;
            auto result = service_.listUserTickets(*loginOpt, *passOpt, counts);
            switch (result) {
                case PurchaseService::ListResult::Ok: {
                    std::string json = "[";
                    for (size_t i = 0; i < counts.size(); i++) {
                        json += "{";
                        json += "\"idBiletu\":" + std::to_string(counts[i].first) + ",";
                        json += "\"quantity\":" + std::to_string(counts[i].second);
                        json += "}";
                        if (i + 1 < counts.size()) json += ",";
                    }
                    json += "]";
                    res.status = 200;
                    res.set_content(json, "application/json; charset=utf-8");
                    break;
                }
                case PurchaseService::ListResult::Unauthorized:
                    res.status = 401; res.set_content("Invalid login or password", "text/plain; charset=utf-8"); break;
                case PurchaseService::ListResult::InvalidInput:
                    res.status = 400; res.set_content("Invalid input", "text/plain; charset=utf-8"); break;
                default:
                    res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
            }
        });
    }

private:
    PurchaseService& service_;
};

// ======================================================
// Dodatkowy serwis/endpoint: /random (bez zmian logiki)
// ======================================================
class RandomNumberService {
public:
    RandomNumberService()
        : rng_(std::random_device{}()), dist_(1, 20) {}

    int generate() { return dist_(rng_); }

private:
    std::mt19937 rng_;
    std::uniform_int_distribution<int> dist_;
};

class RandomController {
public:
    explicit RandomController(RandomNumberService& service)
        : service_(service) {}

    void registerRoutes(httplib::Server& server) {
        server.Get("/random", [this](const httplib::Request&, httplib::Response& res) {
            res.set_content(std::to_string(service_.generate()), "text/plain; charset=utf-8");
        });
    }

private:
    RandomNumberService& service_;
};

// ======================================================
// Wymóg: Parallel programming (std::thread)
// - wątek diagnostyczny aplikacji, działający w tle
// ======================================================
class BackgroundWorker {
public:
    BackgroundWorker()
        : running_(false) {}

    void start() {
        running_.store(true);
        worker_ = std::thread([this]() {
            while (running_.load()) {
                std::this_thread::sleep_for(std::chrono::seconds(5));
                // minimalny “heartbeat” — możesz rozbudować o statystyki
                std::cerr << "[diag] server alive\n";
            }
        });
    }

    void stop() {
        running_.store(false);
        if (worker_.joinable()) worker_.join();
    }

    ~BackgroundWorker() { // dba o zamknięcie wątku
        stop();
    }

private:
    std::atomic_bool running_;
    std::thread worker_;
};

// ======================================================
// Wymóg: One main program class (Application/AppServer)
// + Polymorphism poprzez trzymanie obiektów przez interfejsy
// + Dynamic allocation poprzez smart pointers
// ======================================================
class AppServer {
public:
    AppServer(std::string host, int port)
        : host_(std::move(host)),
          port_(port),
          scratch_(1024)  // RawBuffer -> dynamic allocation
    {}

    void start() {
        // start background worker (parallel programming)
        bg_.start();

        // Repozytoria przez interfejsy (polymorphism) + smart pointers (dynamic allocation)
        userRepo_     = std::make_unique<FileUserRepository>("dbUsers.txt");
        ticketRepo_   = std::make_unique<FileTicketRepository>("ticketsDb.txt");
        purchaseRepo_ = std::make_unique<FilePurchaseRepository>("purchasesDb.txt");

        // Serwisy
        authService_    = std::make_unique<AuthService>(*userRepo_);
        ticketService_  = std::make_unique<TicketService>(*ticketRepo_);
        purchaseService_= std::make_unique<PurchaseService>(*userRepo_, *ticketRepo_, *purchaseRepo_);

        // Kontrolery
        authController_    = std::make_unique<AuthController>(*authService_);
        ticketController_  = std::make_unique<TicketController>(*ticketService_);
        purchaseController_= std::make_unique<PurchaseController>(*purchaseService_);

        // Random
        randomService_   = std::make_unique<RandomNumberService>();
        randomController_= std::make_unique<RandomController>(*randomService_);

        // Rejestracja tras
        randomController_->registerRoutes(server_);
        authController_->registerRoutes(server_);
        ticketController_->registerRoutes(server_);
        purchaseController_->registerRoutes(server_);

        std::cout << "Listening on http://" << host_ << ":" << port_ << "\n";
        std::cout << "GET  /random\n";
        std::cout << "POST /register\n";
        std::cout << "POST /login\n";
        std::cout << "POST /user/get\n";
        std::cout << "POST /tickets/create\n";
        std::cout << "POST /tickets/delete\n";
        std::cout << "GET  /tickets\n";
        std::cout << "GET  /tickets/{id}\n";
        std::cout << "POST /purchase\n";
        std::cout << "POST /purchases/by-user\n";

        server_.listen(host_.c_str(), port_);

        // po zakończeniu listen() — stop worker
        bg_.stop();
    }

private:
    std::string host_;
    int port_;
    httplib::Server server_;

    RawBuffer scratch_;         // wymóg: new + destructor
    BackgroundWorker bg_;       // wymóg: std::thread

    // Repozytoria (polymorphism przez interfejs)
    std::unique_ptr<IUserRepository> userRepo_;
    std::unique_ptr<ITicketRepository> ticketRepo_;
    std::unique_ptr<IPurchaseRepository> purchaseRepo_;

    // Serwisy
    std::unique_ptr<AuthService> authService_;
    std::unique_ptr<TicketService> ticketService_;
    std::unique_ptr<PurchaseService> purchaseService_;

    // Kontrolery
    std::unique_ptr<AuthController> authController_;
    std::unique_ptr<TicketController> ticketController_;
    std::unique_ptr<PurchaseController> purchaseController_;

    // Random
    std::unique_ptr<RandomNumberService> randomService_;
    std::unique_ptr<RandomController> randomController_;
};

// ======================================================
// Unit tests (wymóg 3 pt) — minimalny zestaw
// Uruchamiasz: g++ ... -DUNIT_TESTS
// ======================================================
#ifdef UNIT_TESTS

static void run_tests() {
    // repozytoria na plikach testowych
    {
        // cleanup test files
        std::ofstream("users_test.txt", std::ios::trunc).close();
        std::ofstream("tickets_test.txt", std::ios::trunc).close();
        std::ofstream("purchases_test.txt", std::ios::trunc).close();
    }

    FileUserRepository users("users_test.txt");
    AuthService auth(users);

    // register + login
    assert(auth.registerUser("jan", "haslo123") == AuthService::RegisterResult::Ok);
    assert(auth.loginUser("jan", "haslo123") == AuthService::LoginResult::Ok);
    assert(auth.loginUser("jan", "zle") == AuthService::LoginResult::InvalidCredentials);

    FileTicketRepository tickets("tickets_test.txt");
    TicketService ticketSvc(tickets);

    long long id1 = 0;
    assert(ticketSvc.createTicket("12345", 99.5, "Koncert", id1) == TicketService::CreateResult::Ok);
    assert(id1 == 1);

    long long id2 = 0;
    assert(ticketSvc.createTicket("12345", 50.0, "Mecz", id2) == TicketService::CreateResult::Ok);
    assert(id2 == 2);

    FilePurchaseRepository purchases("purchases_test.txt");
    PurchaseService purchaseSvc(users, tickets, purchases);

    // purchase
    assert(purchaseSvc.purchase(2, "jan", "haslo123", 3) == PurchaseService::PurchaseResult::Ok);

    std::vector<std::pair<long long, long long>> counts;
    assert(purchaseSvc.listUserTickets("jan", "haslo123", counts) == PurchaseService::ListResult::Ok);

    // powinno być ticketId=2 qty=3
    assert(counts.size() == 1);
    assert(counts[0].first == 2);
    assert(counts[0].second == 3);

    std::cout << "All tests passed.\n";
}

int main() {
    run_tests();
    return 0;
}

#else

int main() {
    AppServer app("127.0.0.1", 8080);
    app.start();
    return 0;
}

#endif

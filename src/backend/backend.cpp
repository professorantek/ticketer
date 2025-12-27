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

// =====================
// Minimalny parser JSON (string/number/int)
// =====================
static std::optional<std::string> extractJsonStringField(const std::string &body, const std::string &key)
{
    const std::string pattern = "\"" + key + "\"";
    size_t pos = body.find(pattern);
    if (pos == std::string::npos)
        return std::nullopt;

    pos = body.find(':', pos);
    if (pos == std::string::npos)
        return std::nullopt;

    pos = body.find('"', pos);
    if (pos == std::string::npos)
        return std::nullopt;

    size_t start = pos + 1;
    size_t end = body.find('"', start);
    if (end == std::string::npos)
        return std::nullopt;

    return body.substr(start, end - start);
}

static std::optional<double> extractJsonNumberField(const std::string &body, const std::string &key)
{
    const std::string pattern = "\"" + key + "\"";
    size_t pos = body.find(pattern);
    if (pos == std::string::npos)
        return std::nullopt;

    pos = body.find(':', pos);
    if (pos == std::string::npos)
        return std::nullopt;

    pos++;
    while (pos < body.size() && std::isspace(static_cast<unsigned char>(body[pos])))
        pos++;

    size_t end = pos;
    while (end < body.size())
    {
        char c = body[end];
        if (!(std::isdigit(static_cast<unsigned char>(c)) || c == '.' || c == '-'))
            break;
        end++;
    }
    if (end == pos)
        return std::nullopt;

    try
    {
        return std::stod(body.substr(pos, end - pos));
    }
    catch (...)
    {
        return std::nullopt;
    }
}

static std::optional<long long> extractJsonIntField(const std::string &body, const std::string &key)
{
    auto numOpt = extractJsonNumberField(body, key);
    if (!numOpt)
        return std::nullopt;
    return static_cast<long long>(*numOpt);
}

// =====================
// Repozytorium użytkowników (dbUsers.txt)
// =====================
struct User
{
    std::string login;
    std::string password;
};

class FileUserRepository
{
public:
    explicit FileUserRepository(std::string dbPath)
        : dbPath_(std::move(dbPath)) {}

    bool exists(const std::string &login) const
    {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ifstream in(dbPath_);
        if (!in.is_open())
        {
            return false;
        }

        std::string line;
        while (std::getline(in, line))
        {
            std::string fileLogin, filePass;
            if (parseLine(line, fileLogin, filePass) && fileLogin == login)
            {
                return true;
            }
        }
        return false;
    }

    bool verifyCredentials(const std::string &login, const std::string &password) const
    {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ifstream in(dbPath_);
        if (!in.is_open())
            return false;

        std::string line;
        while (std::getline(in, line))
        {
            std::string fileLogin, filePass;
            if (parseLine(line, fileLogin, filePass) && fileLogin == login)
            {
                return filePass == password;
            }
        }
        return false;
    }

    bool addUser(const std::string &login, const std::string &password)
    {
        std::lock_guard<std::mutex> lock(mtx_);

        if (existsUnlocked(login))
        {
            return false;
        }

        std::ofstream out(dbPath_, std::ios::app);
        if (!out.is_open())
        {
            throw std::runtime_error("Cannot open users db file for writing");
        }

        out << login << " " << password << "\n";
        return true;
    }

    // zwróć dane usera (login+hasło) dla loginu
    // UWAGA: nie udostępniaj tego bez autoryzacji w kontrolerze
    std::optional<User> getUserByLogin(const std::string &login) const
    {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ifstream in(dbPath_);
        if (!in.is_open())
            return std::nullopt;

        std::string line;
        while (std::getline(in, line))
        {
            std::string fileLogin, filePass;
            if (parseLine(line, fileLogin, filePass) && fileLogin == login)
            {
                return User{fileLogin, filePass};
            }
        }
        return std::nullopt;
    }

private:
    bool existsUnlocked(const std::string &login) const
    {
        std::ifstream in(dbPath_);
        if (!in.is_open())
            return false;

        std::string line;
        while (std::getline(in, line))
        {
            std::string fileLogin, filePass;
            if (parseLine(line, fileLogin, filePass) && fileLogin == login)
            {
                return true;
            }
        }
        return false;
    }

    static bool parseLine(const std::string &line, std::string &login, std::string &pass)
    {
        std::istringstream iss(line);
        iss >> login >> pass;
        return !iss.fail();
    }

    std::string dbPath_;
    mutable std::mutex mtx_;
};

// =====================
// Serwis autoryzacji
// =====================
class AuthService
{
public:
    explicit AuthService(FileUserRepository &repo)
        : repo_(repo) {}

    enum class RegisterResult
    {
        Ok,
        LoginTaken,
        InvalidInput,
        StorageError
    };
    enum class LoginResult
    {
        Ok,
        InvalidInput,
        InvalidCredentials,
        StorageError
    };

    LoginResult loginUser(const std::string &login, const std::string &password)
    {
        if (!isValid(login) || !isValid(password))
        {
            return LoginResult::InvalidInput;
        }

        try
        {
            bool ok = repo_.verifyCredentials(login, password);
            return ok ? LoginResult::Ok : LoginResult::InvalidCredentials;
        }
        catch (...)
        {
            return LoginResult::StorageError;
        }
    }

    RegisterResult registerUser(const std::string &login, const std::string &password)
    {
        if (!isValid(login) || !isValid(password))
        {
            return RegisterResult::InvalidInput;
        }

        try
        {
            if (repo_.exists(login))
            {
                return RegisterResult::LoginTaken;
            }
            bool added = repo_.addUser(login, password);
            return added ? RegisterResult::Ok : RegisterResult::LoginTaken;
        }
        catch (...)
        {
            return RegisterResult::StorageError;
        }
    }

    // zwróć dane usera, ale tylko jeśli login+password są poprawne
    std::optional<User> getUserIfAuthorized(const std::string &login, const std::string &password)
    {
        try
        {
            if (!repo_.verifyCredentials(login, password))
                return std::nullopt;
            return repo_.getUserByLogin(login);
        }
        catch (...)
        {
            return std::nullopt;
        }
    }

private:
    static bool isValid(const std::string &s)
    {
        if (s.size() < 3 || s.size() > 32)
            return false;
        for (unsigned char c : s)
        {
            if (std::isspace(c) || std::iscntrl(c))
                return false;
        }
        return true;
    }

    FileUserRepository &repo_;
};

// =====================
// Kontroler auth
// =====================
class AuthController
{
public:
    explicit AuthController(AuthService &service)
        : service_(service) {}

    void registerRoutes(httplib::Server &server)
    {
        server.Post("/register", [this](const httplib::Request &req, httplib::Response &res)
                    {
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
                    res.status = 201;
                    res.set_content("User registered", "text/plain; charset=utf-8");
                    break;
                case AuthService::RegisterResult::LoginTaken:
                    res.status = 409;
                    res.set_content("Login already taken", "text/plain; charset=utf-8");
                    break;
                case AuthService::RegisterResult::InvalidInput:
                    res.status = 400;
                    res.set_content("Invalid login or password (3-32 chars, no spaces)",
                                    "text/plain; charset=utf-8");
                    break;
                default:
                    res.status = 500;
                    res.set_content("Storage error", "text/plain; charset=utf-8");
                    break;
            } });

        server.Post("/login", [this](const httplib::Request &req, httplib::Response &res)
                    {
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
                    res.status = 200;
                    res.set_content("Login OK", "text/plain; charset=utf-8");
                    break;
                case AuthService::LoginResult::InvalidInput:
                    res.status = 400;
                    res.set_content("Invalid login or password (3-32 chars, no spaces)",
                                    "text/plain; charset=utf-8");
                    break;
                case AuthService::LoginResult::InvalidCredentials:
                    res.status = 401;
                    res.set_content("Invalid login or password", "text/plain; charset=utf-8");
                    break;
                default:
                    res.status = 500;
                    res.set_content("Storage error", "text/plain; charset=utf-8");
                    break;
            } });

        // POST /user/get
        // {"login":"jan","password":"haslo123"}
        // -> {"login":"jan","password":"haslo123"}
        // (celowo wymaga hasła; inaczej to byłby wyciek)
        server.Post("/user/get", [this](const httplib::Request &req, httplib::Response &res)
                    {
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

            // JSON
            std::string json = "{";
            json += "\"login\":\"" + userOpt->login + "\",";
            // Uwaga: zwracanie hasła jest NIEBEZPIECZNE, ale robimy zgodnie z Twoim wymaganiem.
            // W realnym projekcie hasła są hashowane i nigdy nie są zwracane.
            std::string safePass = userOpt->password;
            size_t pos = 0;
            while ((pos = safePass.find('"', pos)) != std::string::npos) {
                safePass.insert(pos, "\\");
                pos += 2;
            }
            json += "\"password\":\"" + safePass + "\"";
            json += "}";

            res.status = 200;
            res.set_content(json, "application/json; charset=utf-8"); });
    }

private:
    AuthService &service_;
};

// =====================
// Tickets
// =====================
struct Ticket
{
    long long id;
    double price;
    std::string name;
};

class TicketRepository
{
public:
    explicit TicketRepository(std::string dbPath)
        : dbPath_(std::move(dbPath)) {}

    long long generateNextId() const
    {
        std::lock_guard<std::mutex> lock(mtx_);
        long long maxId = 0;

        std::ifstream in(dbPath_);
        if (!in.is_open())
            return 1;

        std::string line;
        while (std::getline(in, line))
        {
            Ticket t;
            if (parseLine(line, t))
            {
                if (t.id > maxId)
                    maxId = t.id;
            }
        }
        return maxId + 1;
    }

    void addTicket(const Ticket &t)
    {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ofstream out(dbPath_, std::ios::app);
        if (!out.is_open())
        {
            throw std::runtime_error("Cannot open tickets db file for writing");
        }
        out << t.id << " " << t.price << " " << t.name << "\n";
    }

    bool deleteById(long long id)
    {
        std::lock_guard<std::mutex> lock(mtx_);

        std::ifstream in(dbPath_);
        if (!in.is_open())
            return false;

        std::vector<std::string> kept;
        kept.reserve(256);

        bool removed = false;
        std::string line;
        while (std::getline(in, line))
        {
            Ticket t;
            if (parseLine(line, t) && t.id == id)
            {
                removed = true;
                continue;
            }
            kept.push_back(line);
        }
        in.close();

        if (!removed)
            return false;

        std::ofstream out(dbPath_, std::ios::trunc);
        if (!out.is_open())
        {
            throw std::runtime_error("Cannot open tickets db file for rewriting");
        }
        for (const auto &l : kept)
            out << l << "\n";
        return true;
    }

    std::vector<Ticket> listAll() const
    {
        std::lock_guard<std::mutex> lock(mtx_);
        std::vector<Ticket> tickets;

        std::ifstream in(dbPath_);
        if (!in.is_open())
            return tickets;

        std::string line;
        while (std::getline(in, line))
        {
            Ticket t;
            if (parseLine(line, t))
                tickets.push_back(t);
        }
        return tickets;
    }

    bool existsById(long long id) const
    {
        std::lock_guard<std::mutex> lock(mtx_);

        std::ifstream in(dbPath_);
        if (!in.is_open())
            return false;

        std::string line;
        while (std::getline(in, line))
        {
            Ticket t;
            if (parseLine(line, t) && t.id == id)
                return true;
        }
        return false;
    }

    std::optional<Ticket> getById(long long id) const
    {
        std::lock_guard<std::mutex> lock(mtx_);
        std::ifstream in(dbPath_);
        if (!in.is_open())
            return std::nullopt;

        std::string line;
        while (std::getline(in, line))
        {
            Ticket t;
            if (parseLine(line, t) && t.id == id)
                return t;
        }
        return std::nullopt;
    }

private:
    static bool parseLine(const std::string &line, Ticket &t)
    {
        std::istringstream iss(line);
        if (!(iss >> t.id >> t.price))
            return false;

        std::string rest;
        std::getline(iss, rest);
        if (!rest.empty() && rest[0] == ' ')
            rest.erase(0, 1);
        t.name = rest;
        return !t.name.empty();
    }

    std::string dbPath_;
    mutable std::mutex mtx_;
};

class TicketService
{
public:
    explicit TicketService(TicketRepository &repo)
        : repo_(repo) {}

    enum class CreateResult
    {
        Ok,
        InvalidInput,
        Forbidden,
        StorageError
    };
    enum class DeleteResult
    {
        Ok,
        NotFound,
        Forbidden,
        InvalidInput,
        StorageError
    };

    CreateResult createTicket(const std::string &adminPassword, double price, const std::string &name, long long &outId)
    {
        if (adminPassword != kAdminPassword)
            return CreateResult::Forbidden;
        if (name.empty() || name.size() > 100)
            return CreateResult::InvalidInput;
        if (!(price > 0.0) || price > 1'000'000.0)
            return CreateResult::InvalidInput;

        try
        {
            outId = repo_.generateNextId();
            repo_.addTicket(Ticket{outId, price, name});
            return CreateResult::Ok;
        }
        catch (...)
        {
            return CreateResult::StorageError;
        }
    }

    DeleteResult deleteTicket(const std::string &adminPassword, long long id)
    {
        if (adminPassword != kAdminPassword)
            return DeleteResult::Forbidden;
        if (id <= 0)
            return DeleteResult::InvalidInput;

        try
        {
            bool ok = repo_.deleteById(id);
            return ok ? DeleteResult::Ok : DeleteResult::NotFound;
        }
        catch (...)
        {
            return DeleteResult::StorageError;
        }
    }

    std::vector<Ticket> getAllTickets()
    {
        try
        {
            return repo_.listAll();
        }
        catch (...)
        {
            return {};
        }
    }

    std::optional<Ticket> getTicketById(long long id)
    {
        try
        {
            return repo_.getById(id);
        }
        catch (...)
        {
            return std::nullopt;
        }
    }

private:
    static constexpr const char *kAdminPassword = "12345";
    TicketRepository &repo_;
};

class TicketController
{
public:
    explicit TicketController(TicketService &service)
        : service_(service) {}

    void registerRoutes(httplib::Server &server)
    {
        server.Post("/tickets/create", [this](const httplib::Request &req, httplib::Response &res)
                    {
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
                    res.set_content("Ticket created: " + std::to_string(newId),
                                    "text/plain; charset=utf-8");
                    break;
                case TicketService::CreateResult::Forbidden:
                    res.status = 403;
                    res.set_content("Forbidden", "text/plain; charset=utf-8");
                    break;
                case TicketService::CreateResult::InvalidInput:
                    res.status = 400;
                    res.set_content("Invalid price or name", "text/plain; charset=utf-8");
                    break;
                default:
                    res.status = 500;
                    res.set_content("Storage error", "text/plain; charset=utf-8");
                    break;
            } });

        server.Post("/tickets/delete", [this](const httplib::Request &req, httplib::Response &res)
                    {
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
                    res.status = 200;
                    res.set_content("Ticket deleted", "text/plain; charset=utf-8");
                    break;
                case TicketService::DeleteResult::NotFound:
                    res.status = 404;
                    res.set_content("Ticket not found", "text/plain; charset=utf-8");
                    break;
                case TicketService::DeleteResult::Forbidden:
                    res.status = 403;
                    res.set_content("Forbidden", "text/plain; charset=utf-8");
                    break;
                case TicketService::DeleteResult::InvalidInput:
                    res.status = 400;
                    res.set_content("Invalid id", "text/plain; charset=utf-8");
                    break;
                default:
                    res.status = 500;
                    res.set_content("Storage error", "text/plain; charset=utf-8");
                    break;
            } });

        // GET /tickets  -> lista
        server.Get("/tickets", [this](const httplib::Request &, httplib::Response &res)
                   {
            auto tickets = service_.getAllTickets();

            std::string json = "[";
            for (size_t i = 0; i < tickets.size(); i++) {
                const auto& t = tickets[i];

                std::string safeName = t.name;
                size_t pos = 0;
                while ((pos = safeName.find('"', pos)) != std::string::npos) {
                    safeName.insert(pos, "\\");
                    pos += 2;
                }

                json += "{";
                json += "\"id\":" + std::to_string(t.id) + ",";
                json += "\"price\":" + std::to_string(t.price) + ",";
                json += "\"name\":\"" + safeName + "\"";
                json += "}";

                if (i + 1 < tickets.size()) json += ",";
            }
            json += "]";

            res.status = 200;
            res.set_content(json, "application/json; charset=utf-8"); });

        // GET /tickets/{id} -> pełne dane jednego biletu
        server.Get(R"(/tickets/(\d+))", [this](const httplib::Request &req, httplib::Response &res)
                   {
            long long id = 0;
            try {
                id = std::stoll(req.matches[1].str());
            } catch (...) {
                res.status = 400;
                res.set_content("Invalid ticket id", "text/plain; charset=utf-8");
                return;
            }

            auto tOpt = service_.getTicketById(id);
            if (!tOpt) {
                res.status = 404;
                res.set_content("Ticket not found", "text/plain; charset=utf-8");
                return;
            }

            std::string safeName = tOpt->name;
            size_t pos = 0;
            while ((pos = safeName.find('"', pos)) != std::string::npos) {
                safeName.insert(pos, "\\");
                pos += 2;
            }

            std::string json = "{";
            json += "\"id\":" + std::to_string(tOpt->id) + ",";
            json += "\"price\":" + std::to_string(tOpt->price) + ",";
            json += "\"name\":\"" + safeName + "\"";
            json += "}";

            res.status = 200;
            res.set_content(json, "application/json; charset=utf-8"); });
    }

private:
    TicketService &service_;
};

// =====================
// Purchases
// Format linii w purchasesDb.txt:
// purchaseId ticketId login
// =====================
struct Purchase
{
    long long purchaseId;
    long long ticketId;
    std::string login;
};

class PurchaseRepository
{
public:
    explicit PurchaseRepository(std::string dbPath)
        : dbPath_(std::move(dbPath)) {}

    long long generateNextPurchaseId() const
    {
        std::lock_guard<std::mutex> lock(mtx_);
        long long maxId = 0;

        std::ifstream in(dbPath_);
        if (!in.is_open())
            return 1;

        std::string line;
        while (std::getline(in, line))
        {
            Purchase p;
            if (parseLine(line, p))
            {
                if (p.purchaseId > maxId)
                    maxId = p.purchaseId;
            }
        }
        return maxId + 1;
    }

    void addPurchase(const Purchase &p)
    {
        std::lock_guard<std::mutex> lock(mtx_);

        std::ofstream out(dbPath_, std::ios::app);
        if (!out.is_open())
        {
            throw std::runtime_error("Cannot open purchases db file for writing");
        }

        out << p.purchaseId << " " << p.ticketId << " " << p.login << "\n";
    }

    // Agregacja: ticketId -> quantity dla danego login
    std::vector<std::pair<long long, long long>> getTicketCountsForLogin(const std::string &login) const
    {
        std::lock_guard<std::mutex> lock(mtx_);

        std::ifstream in(dbPath_);
        if (!in.is_open())
            return {};

        std::vector<std::pair<long long, long long>> counts;

        std::string line;
        while (std::getline(in, line))
        {
            Purchase p;
            if (!parseLine(line, p))
                continue;

            if (p.login != login)
                continue;

            bool found = false;
            for (auto &kv : counts)
            {
                if (kv.first == p.ticketId)
                {
                    kv.second++;
                    found = true;
                    break;
                }
            }
            if (!found)
                counts.push_back({p.ticketId, 1});
        }

        return counts;
    }

private:
    static bool parseLine(const std::string &line, Purchase &p)
    {
        std::istringstream iss(line);
        return static_cast<bool>(iss >> p.purchaseId >> p.ticketId >> p.login);
    }

    std::string dbPath_;
    mutable std::mutex mtx_;
};

class PurchaseService
{
public:
    PurchaseService(FileUserRepository &userRepo,
                    TicketRepository &ticketRepo,
                    PurchaseRepository &purchaseRepo)
        : userRepo_(userRepo), ticketRepo_(ticketRepo), purchaseRepo_(purchaseRepo) {}

    enum class PurchaseResult
    {
        Ok,
        InvalidInput,
        Unauthorized,
        TicketNotFound,
        StorageError
    };

    enum class ListResult
    {
        Ok,
        InvalidInput,
        Unauthorized,
        StorageError
    };

    PurchaseResult purchase(long long ticketId,
                            const std::string &login,
                            const std::string &password,
                            long long quantity)
    {
        if (ticketId <= 0)
            return PurchaseResult::InvalidInput;
        if (login.empty() || password.empty())
            return PurchaseResult::InvalidInput;
        if (quantity <= 0 || quantity > 1000)
            return PurchaseResult::InvalidInput;

        try
        {
            if (!userRepo_.verifyCredentials(login, password))
            {
                return PurchaseResult::Unauthorized;
            }

            if (!ticketRepo_.existsById(ticketId))
            {
                return PurchaseResult::TicketNotFound;
            }

            for (long long i = 0; i < quantity; i++)
            {
                long long newPurchaseId = purchaseRepo_.generateNextPurchaseId();
                purchaseRepo_.addPurchase(Purchase{newPurchaseId, ticketId, login});
            }

            return PurchaseResult::Ok;
        }
        catch (...)
        {
            return PurchaseResult::StorageError;
        }
    }

    ListResult listUserTickets(const std::string &login,
                              const std::string &password,
                              std::vector<std::pair<long long, long long>> &outCounts)
    {
        if (login.empty() || password.empty())
            return ListResult::InvalidInput;

        try
        {
            if (!userRepo_.verifyCredentials(login, password))
                return ListResult::Unauthorized;

            outCounts = purchaseRepo_.getTicketCountsForLogin(login);
            return ListResult::Ok;
        }
        catch (...)
        {
            return ListResult::StorageError;
        }
    }

private:
    FileUserRepository &userRepo_;
    TicketRepository &ticketRepo_;
    PurchaseRepository &purchaseRepo_;
};

class PurchaseController
{
public:
    explicit PurchaseController(PurchaseService &service)
        : service_(service) {}

    void registerRoutes(httplib::Server &server)
    {
        // POST /purchase
        // {"idBiletu":2,"quantity":3,"login":"jan","password":"haslo123"}
        server.Post("/purchase", [this](const httplib::Request &req, httplib::Response &res)
                    {
            auto ticketIdOpt = extractJsonIntField(req.body, "idBiletu");
            auto qtyOpt      = extractJsonIntField(req.body, "quantity");
            auto loginOpt    = extractJsonStringField(req.body, "login");
            auto passOpt     = extractJsonStringField(req.body, "password");

            if (!ticketIdOpt || !qtyOpt || !loginOpt || !passOpt) {
                res.status = 400;
                res.set_content(
                    "Invalid JSON. Expected {\"idBiletu\":...,\"quantity\":...,\"login\":\"...\",\"password\":\"...\"}",
                    "text/plain; charset=utf-8"
                );
                return;
            }

            auto result = service_.purchase(*ticketIdOpt, *loginOpt, *passOpt, *qtyOpt);

            switch (result) {
                case PurchaseService::PurchaseResult::Ok:
                    res.status = 201;
                    res.set_content("Purchases saved", "text/plain; charset=utf-8");
                    break;
                case PurchaseService::PurchaseResult::Unauthorized:
                    res.status = 401;
                    res.set_content("Invalid login or password", "text/plain; charset=utf-8");
                    break;
                case PurchaseService::PurchaseResult::TicketNotFound:
                    res.status = 404;
                    res.set_content("Ticket not found", "text/plain; charset=utf-8");
                    break;
                case PurchaseService::PurchaseResult::InvalidInput:
                    res.status = 400;
                    res.set_content("Invalid input", "text/plain; charset=utf-8");
                    break;
                default:
                    res.status = 500;
                    res.set_content("Storage error", "text/plain; charset=utf-8");
                    break;
            } });

        // POST /purchases/by-user
        // {"login":"jan","password":"haslo123"}
        // -> [{"idBiletu":2,"quantity":3}, ...]
        server.Post("/purchases/by-user", [this](const httplib::Request &req, httplib::Response &res)
                    {
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
                    res.status = 401;
                    res.set_content("Invalid login or password", "text/plain; charset=utf-8");
                    break;
                case PurchaseService::ListResult::InvalidInput:
                    res.status = 400;
                    res.set_content("Invalid input", "text/plain; charset=utf-8");
                    break;
                default:
                    res.status = 500;
                    res.set_content("Storage error", "text/plain; charset=utf-8");
                    break;
            } });
    }

private:
    PurchaseService &service_;
};

// =====================
// Random
// =====================
class RandomNumberService
{
public:
    RandomNumberService()
        : rng_(std::random_device{}()), dist_(1, 20) {}

    int generate() { return dist_(rng_); }

private:
    std::mt19937 rng_;
    std::uniform_int_distribution<int> dist_;
};

class RandomController
{
public:
    explicit RandomController(RandomNumberService &service)
        : service_(service) {}

    void registerRoutes(httplib::Server &server)
    {
        server.Get("/random", [this](const httplib::Request &, httplib::Response &res)
                   { res.set_content(std::to_string(service_.generate()), "text/plain; charset=utf-8"); });
    }

private:
    RandomNumberService &service_;
};

// =====================
// AppServer
// =====================
class AppServer
{
public:
    AppServer(std::string host, int port)
        : host_(std::move(host)), port_(port) {}

    void start()
    {
        RandomNumberService randomService;
        RandomController randomController(randomService);

        FileUserRepository userRepo("dbUsers.txt");
        AuthService authService(userRepo);
        AuthController authController(authService);

        TicketRepository ticketRepo("ticketsDb.txt");
        TicketService ticketService(ticketRepo);
        TicketController ticketController(ticketService);

        PurchaseRepository purchaseRepo("purchasesDb.txt");
        PurchaseService purchaseService(userRepo, ticketRepo, purchaseRepo);
        PurchaseController purchaseController(purchaseService);

        randomController.registerRoutes(server_);
        authController.registerRoutes(server_);
        ticketController.registerRoutes(server_);
        purchaseController.registerRoutes(server_);

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
    }

private:
    std::string host_;
    int port_;
    httplib::Server server_;
};

int main()
{
    AppServer app("127.0.0.1", 8080);
    app.start();
    return 0;
}

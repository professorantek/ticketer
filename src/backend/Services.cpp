#include "Services.h"
#include <cctype>

// ======================================================
// AuthService
// ======================================================
AuthService::AuthService(IUserRepository& repo)
    : repo_(repo) {}

AuthService::RegisterResult AuthService::registerUser(const std::string& login, const std::string& password) {
    if (!isValid(login) || !isValid(password)) return RegisterResult::InvalidInput;

    try {
        if (repo_.exists(login)) return RegisterResult::LoginTaken;
        bool added = repo_.addUser(login, password);
        return added ? RegisterResult::Ok : RegisterResult::LoginTaken;
    } catch (...) {
        return RegisterResult::StorageError;
    }
}

AuthService::LoginResult AuthService::loginUser(const std::string& login, const std::string& password) {
    if (!isValid(login) || !isValid(password)) return LoginResult::InvalidInput;

    try {
        bool ok = repo_.verifyCredentials(login, password);
        return ok ? LoginResult::Ok : LoginResult::InvalidCredentials;
    } catch (...) {
        return LoginResult::StorageError;
    }
}

std::optional<User> AuthService::getUserIfAuthorized(const std::string& login, const std::string& password) {
    try {
        if (!repo_.verifyCredentials(login, password)) return std::nullopt;
        return repo_.getUserByLogin(login);
    } catch (...) {
        return std::nullopt;
    }
}

bool AuthService::isValid(const std::string& s) {
    if (s.size() < 3 || s.size() > 32) return false;
    for (unsigned char c : s) {
        if (std::isspace(c) || std::iscntrl(c)) return false;
    }
    return true;
}

// ======================================================
// TicketService
// ======================================================
TicketService::TicketService(ITicketRepository& repo)
    : repo_(repo) {}

TicketService::CreateResult TicketService::createTicket(const std::string& adminPassword, double price, const std::string& name, long long& outId) {
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

TicketService::DeleteResult TicketService::deleteTicket(const std::string& adminPassword, long long id) {
    if (adminPassword != kAdminPassword) return DeleteResult::Forbidden;
    if (id <= 0) return DeleteResult::InvalidInput;

    try {
        bool ok = repo_.deleteById(id);
        return ok ? DeleteResult::Ok : DeleteResult::NotFound;
    } catch (...) {
        return DeleteResult::StorageError;
    }
}

TicketService::UpdateResult TicketService::updateTicket(const std::string& adminPassword, long long id, double newPrice, const std::string& newName) {
    if (adminPassword != kAdminPassword) return UpdateResult::Forbidden;
    if (id <= 0) return UpdateResult::InvalidInput;
    if (newName.empty() || newName.size() > 100) return UpdateResult::InvalidInput;
    if (!(newPrice > 0.0) || newPrice > 1'000'000.0) return UpdateResult::InvalidInput;

    try {
        bool ok = repo_.updateTicket(id, newPrice, newName);
        return ok ? UpdateResult::Ok : UpdateResult::NotFound;
    } catch (...) {
        return UpdateResult::StorageError;
    }
}

std::vector<Ticket> TicketService::getAllTickets() {
    try { return repo_.listAll(); }
    catch (...) { return {}; }
}

std::optional<Ticket> TicketService::getTicketById(long long id) {
    try { return repo_.getById(id); }
    catch (...) { return std::nullopt; }
}

// ======================================================
// PurchaseService
// ======================================================
PurchaseService::PurchaseService(IUserRepository& userRepo, ITicketRepository& ticketRepo, IPurchaseRepository& purchaseRepo)
    : userRepo_(userRepo), ticketRepo_(ticketRepo), purchaseRepo_(purchaseRepo) {}

PurchaseService::PurchaseResult PurchaseService::purchase(long long ticketId,
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

PurchaseService::ListResult PurchaseService::listUserTickets(const std::string& login,
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

// ======================================================
// RandomNumberService
// ======================================================
RandomNumberService::RandomNumberService()
    : rng_(std::random_device{}()), dist_(1, 20) {}

int RandomNumberService::generate() { return dist_(rng_); }

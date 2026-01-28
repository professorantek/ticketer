#pragma once

#include "Repositories.h"
#include <random>

// ======================================================
// Serwisy (logika biznesowa)
// + enum class
// ======================================================
class AuthService {
public:
    explicit AuthService(IUserRepository& repo);

    enum class RegisterResult { Ok, LoginTaken, InvalidInput, StorageError };
    enum class LoginResult { Ok, InvalidInput, InvalidCredentials, StorageError };

    RegisterResult registerUser(const std::string& login, const std::string& password);
    LoginResult loginUser(const std::string& login, const std::string& password);
    std::optional<User> getUserIfAuthorized(const std::string& login, const std::string& password);

private:
    static bool isValid(const std::string& s);
    IUserRepository& repo_;
};

class TicketService {
public:
    explicit TicketService(ITicketRepository& repo);

    enum class CreateResult { Ok, InvalidInput, Forbidden, StorageError };
    enum class DeleteResult { Ok, NotFound, Forbidden, InvalidInput, StorageError };
    enum class UpdateResult { Ok, NotFound, Forbidden, InvalidInput, StorageError };

    CreateResult createTicket(const std::string& adminPassword, double price, const std::string& name, long long& outId);
    DeleteResult deleteTicket(const std::string& adminPassword, long long id);
    UpdateResult updateTicket(const std::string& adminPassword, long long id, double newPrice, const std::string& newName);
    std::vector<Ticket> getAllTickets();
    std::optional<Ticket> getTicketById(long long id);

private:
    static constexpr const char* kAdminPassword = "12345";
    ITicketRepository& repo_;
};

class PurchaseService {
public:
    PurchaseService(IUserRepository& userRepo, ITicketRepository& ticketRepo, IPurchaseRepository& purchaseRepo);

    enum class PurchaseResult { Ok, InvalidInput, Unauthorized, TicketNotFound, StorageError };
    enum class ListResult { Ok, InvalidInput, Unauthorized, StorageError };

    PurchaseResult purchase(long long ticketId, const std::string& login, const std::string& password, long long quantity);
    ListResult listUserTickets(const std::string& login, const std::string& password, std::vector<std::pair<long long, long long>>& outCounts);

private:
    IUserRepository& userRepo_;
    ITicketRepository& ticketRepo_;
    IPurchaseRepository& purchaseRepo_;
};

class RandomNumberService {
public:
    RandomNumberService();
    int generate();

private:
    std::mt19937 rng_;
    std::uniform_int_distribution<int> dist_;
};

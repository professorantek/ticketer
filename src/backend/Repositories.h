#pragma once

#include "Models.h"
#include <string>
#include <optional>
#include <vector>

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
    virtual bool updateTicket(long long id, double newPrice, const std::string& newName) = 0;
};

class IPurchaseRepository {
public:
    virtual ~IPurchaseRepository() = default;
    virtual long long generateNextPurchaseId() const = 0;
    virtual void addPurchase(const Purchase& p) = 0;
    virtual std::vector<std::pair<long long, long long>> getTicketCountsForLogin(const std::string& login) const = 0;
};

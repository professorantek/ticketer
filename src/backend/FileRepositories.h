#pragma once

#include "Repositories.h"
#include <mutex>

// ======================================================
// Implementacje plikowe repozytoriów (dziedziczenie)
// + File I/O
// + Error handling (exceptions)
// ======================================================
class FileUserRepository final : public IUserRepository
{
public:
    explicit FileUserRepository(std::string dbPath);

    bool exists(const std::string &login) const override;
    bool verifyCredentials(const std::string &login, const std::string &password) const override;
    bool addUser(const std::string &login, const std::string &password) override;
    std::optional<User> getUserByLogin(const std::string &login) const override;

private:
    bool existsUnlocked(const std::string &login) const;
    static bool parseLine(const std::string &line, std::string &login, std::string &pass);

    std::string dbPath_;
    mutable std::mutex mtx_;
};

class FileTicketRepository final : public ITicketRepository
{
public:
    explicit FileTicketRepository(std::string dbPath);

    long long generateNextId() const override;
    void addTicket(const Ticket &t) override;
    bool deleteById(long long id) override;
    std::vector<Ticket> listAll() const override;
    bool existsById(long long id) const override;
    std::optional<Ticket> getById(long long id) const override;
    bool updateTicket(long long id, double newPrice, const std::string &newName) override;

private:
    static bool parseLine(const std::string &line, Ticket &t);

    std::string dbPath_;
    mutable std::mutex mtx_;
};

class FilePurchaseRepository final : public IPurchaseRepository
{
public:
    explicit FilePurchaseRepository(std::string dbPath);

    long long generateNextPurchaseId() const override;
    void addPurchase(const Purchase &p) override;
    std::vector<std::pair<long long, long long>> getTicketCountsForLogin(const std::string &login) const override;

private:
    static bool parseLine(const std::string &line, Purchase &p);

    std::string dbPath_;
    mutable std::mutex mtx_;
};

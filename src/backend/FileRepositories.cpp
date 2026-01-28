#include "FileRepositories.h"
#include <fstream>
#include <sstream>
#include <algorithm>
#include <map>
#include <stdexcept>

// ======================================================
// FileUserRepository
// ======================================================
FileUserRepository::FileUserRepository(std::string dbPath)
    : dbPath_(std::move(dbPath)) {}

bool FileUserRepository::exists(const std::string& login) const {
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

bool FileUserRepository::verifyCredentials(const std::string& login, const std::string& password) const {
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

bool FileUserRepository::addUser(const std::string& login, const std::string& password) {
    std::lock_guard<std::mutex> lock(mtx_);

    if (existsUnlocked(login)) return false;

    std::ofstream out(dbPath_, std::ios::app);
    if (!out.is_open()) throw std::runtime_error("Cannot open users db file for writing");

    out << login << " " << password << "\n";
    return true;
}

std::optional<User> FileUserRepository::getUserByLogin(const std::string& login) const {
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

bool FileUserRepository::existsUnlocked(const std::string& login) const {
    std::ifstream in(dbPath_);
    if (!in.is_open()) return false;

    std::string line;
    while (std::getline(in, line)) {
        std::string fileLogin, filePass;
        if (parseLine(line, fileLogin, filePass) && fileLogin == login) return true;
    }
    return false;
}

bool FileUserRepository::parseLine(const std::string& line, std::string& login, std::string& pass) {
    std::istringstream iss(line);
    iss >> login >> pass;
    return !iss.fail();
}

// ======================================================
// FileTicketRepository
// ======================================================
FileTicketRepository::FileTicketRepository(std::string dbPath)
    : dbPath_(std::move(dbPath)) {}

long long FileTicketRepository::generateNextId() const {
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

void FileTicketRepository::addTicket(const Ticket& t) {
    std::lock_guard<std::mutex> lock(mtx_);
    std::ofstream out(dbPath_, std::ios::app);
    if (!out.is_open()) throw std::runtime_error("Cannot open tickets db file for writing");
    out << t.id << " " << t.price << " " << t.name << "\n";
}

bool FileTicketRepository::deleteById(long long id) {
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

std::vector<Ticket> FileTicketRepository::listAll() const {
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
    std::sort(tickets.begin(), tickets.end(),
              [](const Ticket& a, const Ticket& b) { return a.id < b.id; });

    return tickets;
}

bool FileTicketRepository::existsById(long long id) const {
    return static_cast<bool>(getById(id));
}

std::optional<Ticket> FileTicketRepository::getById(long long id) const {
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

bool FileTicketRepository::updateTicket(long long id, double newPrice, const std::string& newName) {
    std::lock_guard<std::mutex> lock(mtx_);

    std::ifstream in(dbPath_);
    if (!in.is_open()) return false;

    std::vector<Ticket> all;
    all.reserve(256);

    bool updated = false;
    std::string line;
    while (std::getline(in, line)) {
        Ticket t;
        if (!parseLine(line, t)) continue;

        if (t.id == id) {
            t.price = newPrice;
            t.name = newName;
            updated = true;
        }
        all.push_back(t);
    }
    in.close();

    if (!updated) return false;

    std::ofstream out(dbPath_, std::ios::trunc);
    if (!out.is_open()) throw std::runtime_error("Cannot open tickets db file for rewriting");

    for (const auto& t : all) {
        out << t.id << " " << t.price << " " << t.name << "\n";
    }
    return true;
}

bool FileTicketRepository::parseLine(const std::string& line, Ticket& t) {
    std::istringstream iss(line);
    if (!(iss >> t.id >> t.price)) return false;

    std::string rest;
    std::getline(iss, rest);
    if (!rest.empty() && rest[0] == ' ') rest.erase(0, 1);
    t.name = rest;
    return !t.name.empty();
}

// ======================================================
// FilePurchaseRepository
// ======================================================
FilePurchaseRepository::FilePurchaseRepository(std::string dbPath)
    : dbPath_(std::move(dbPath)) {}

long long FilePurchaseRepository::generateNextPurchaseId() const {
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

void FilePurchaseRepository::addPurchase(const Purchase& p) {
    std::lock_guard<std::mutex> lock(mtx_);
    std::ofstream out(dbPath_, std::ios::app);
    if (!out.is_open()) throw std::runtime_error("Cannot open purchases db file for writing");
    out << p.purchaseId << " " << p.ticketId << " " << p.login << "\n";
}

std::vector<std::pair<long long, long long>> FilePurchaseRepository::getTicketCountsForLogin(const std::string& login) const {
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

    // Wymóg: STL Algorithm - np. count_if (tu demonstracyjnie)
    (void)std::count_if(out.begin(), out.end(), [](const auto& kv) { return kv.second > 0; });

    return out;
}

bool FilePurchaseRepository::parseLine(const std::string& line, Purchase& p) {
    std::istringstream iss(line);
    return static_cast<bool>(iss >> p.purchaseId >> p.ticketId >> p.login);
}

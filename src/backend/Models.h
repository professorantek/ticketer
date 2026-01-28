#pragma once

#include <string>
#include <ostream>

// ======================================================
// Modele + operator overloading
// ======================================================
struct User
{
    std::string login;
    std::string password;
};

struct Ticket
{
    long long id;
    double price;
    std::string name;
};

struct Purchase
{
    long long purchaseId;
    long long ticketId;
    std::string login;
};

// Wymóg: Operator overloading
std::ostream &operator<<(std::ostream &os, const Ticket &t);
std::ostream &operator<<(std::ostream &os, const User &u);

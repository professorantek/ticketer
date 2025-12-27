#include "Admin.h"

Admin::Admin(std::string name) : User(name) {}

bool Admin::isAdmin() const {
    return true; // To jest admin
}
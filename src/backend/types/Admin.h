#ifndef ADMIN_H
#define ADMIN_H

#include "User.h"

class Admin : public User {
public:
    Admin(std::string name);
    // Admin nadpisuje metodę, zwracając prawdę
    bool isAdmin() const override;
};

#endif
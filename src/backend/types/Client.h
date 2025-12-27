#ifndef CLIENT_H
#define CLIENT_H

#include "User.h"

class Client : public User {
public:
    Client(std::string name);
    bool isAdmin() const override;
};

#endif
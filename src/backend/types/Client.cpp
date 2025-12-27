#include "Client.h"

Client::Client(std::string name) : User(name) {}

bool Client::isAdmin() const {
    return false;
}
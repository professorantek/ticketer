#include "Models.h"

std::ostream& operator<<(std::ostream& os, const Ticket& t) {
    os << "Ticket{id=" << t.id << ", price=" << t.price << ", name=" << t.name << "}";
    return os;
}

std::ostream& operator<<(std::ostream& os, const User& u) {
    os << "User{login=" << u.login << ", password=" << u.password << "}";
    return os;
}

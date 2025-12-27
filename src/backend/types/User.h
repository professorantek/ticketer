#ifndef USER_H
#define USER_H

#include <string>

class User {
protected:
    std::string username;

public:
    User(std::string name);
    virtual ~User();

    std::string getUsername() const;

    // Metoda sprawdzająca, czy użytkownik jest adminem
    virtual bool isAdmin() const = 0;
};

#endif
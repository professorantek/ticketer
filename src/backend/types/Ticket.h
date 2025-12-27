#ifndef TICKET_H
#define TICKET_H

#include <string>

class Ticket {
private:
    int id;
    std::string eventName;
    double price;
    int quantity;

public:
    Ticket(int id, std::string name, double price, int qty);

    int getId() const;
    std::string getEventName() const;
    double getPrice() const;
    int getQuantity() const;

    void setQuantity(int qty);
    std::string toFileFormat() const;
};

#endif
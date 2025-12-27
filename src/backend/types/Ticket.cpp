#include "Ticket.h"

using namespace std;

Ticket::Ticket(int id, string name, double price, int qty)
    : id(id), eventName(name), price(price), quantity(qty) {}

int Ticket::getId() const { return id; }
string Ticket::getEventName() const { return eventName; }
double Ticket::getPrice() const { return price; }
int Ticket::getQuantity() const { return quantity; }

void Ticket::setQuantity(int qty) {
    quantity = qty;
}

string Ticket::toFileFormat() const {
    return to_string(id) + ";" + eventName + ";" + to_string(price) + ";" + to_string(quantity);
}
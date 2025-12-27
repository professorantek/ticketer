#include "User.h"

using namespace std;

User::User(string name) : username(name) {}
User::~User() {}
string User::getUsername() const { return username; }
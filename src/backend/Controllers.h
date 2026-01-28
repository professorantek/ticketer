#pragma once

#include "Services.h"
#include "httplib.h"

// ======================================================
// Kontrolery HTTP
// ======================================================
class AuthController {
public:
    explicit AuthController(AuthService& service);
    void registerRoutes(httplib::Server& server);

private:
    AuthService& service_;
};

class TicketController {
public:
    explicit TicketController(TicketService& service);
    void registerRoutes(httplib::Server& server);

private:
    TicketService& service_;
};

class PurchaseController {
public:
    explicit PurchaseController(PurchaseService& service);
    void registerRoutes(httplib::Server& server);

private:
    PurchaseService& service_;
};

class RandomController {
public:
    explicit RandomController(RandomNumberService& service);
    void registerRoutes(httplib::Server& server);

private:
    RandomNumberService& service_;
};

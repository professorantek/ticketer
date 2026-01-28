#pragma once

#include "RawBuffer.h"
#include "BackgroundWorker.h"
#include "Repositories.h"
#include "Services.h"
#include "Controllers.h"
#include "httplib.h"
#include <memory>
#include <string>

// ======================================================
// Wymóg: One main program class (Application/AppServer)
// + Polymorphism poprzez trzymanie obiektów przez interfejsy
// + Dynamic allocation poprzez smart pointers
// ======================================================
class AppServer
{
public:
    AppServer(std::string host, int port);
    void start();

private:
    std::string host_;
    int port_;
    httplib::Server server_;

    RawBuffer scratch_;
    BackgroundWorker bg_;

    std::unique_ptr<IUserRepository> userRepo_;
    std::unique_ptr<ITicketRepository> ticketRepo_;
    std::unique_ptr<IPurchaseRepository> purchaseRepo_;

    std::unique_ptr<AuthService> authService_;
    std::unique_ptr<TicketService> ticketService_;
    std::unique_ptr<PurchaseService> purchaseService_;

    std::unique_ptr<AuthController> authController_;
    std::unique_ptr<TicketController> ticketController_;
    std::unique_ptr<PurchaseController> purchaseController_;

    std::unique_ptr<RandomNumberService> randomService_;
    std::unique_ptr<RandomController> randomController_;
};

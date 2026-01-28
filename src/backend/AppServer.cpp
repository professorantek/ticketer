#include "AppServer.h"
#include "FileRepositories.h"
#include <iostream>

AppServer::AppServer(std::string host, int port)
    : host_(std::move(host)),
      port_(port),
      scratch_(1024) // RawBuffer -> dynamic allocation
{
}

void AppServer::start()
{
    bg_.start();

    userRepo_ = std::make_unique<FileUserRepository>("dbUsers.txt");
    ticketRepo_ = std::make_unique<FileTicketRepository>("ticketsDb.txt");
    purchaseRepo_ = std::make_unique<FilePurchaseRepository>("purchasesDb.txt");

    authService_ = std::make_unique<AuthService>(*userRepo_);
    ticketService_ = std::make_unique<TicketService>(*ticketRepo_);
    purchaseService_ = std::make_unique<PurchaseService>(*userRepo_, *ticketRepo_, *purchaseRepo_);

    authController_ = std::make_unique<AuthController>(*authService_);
    ticketController_ = std::make_unique<TicketController>(*ticketService_);
    purchaseController_ = std::make_unique<PurchaseController>(*purchaseService_);

    randomService_ = std::make_unique<RandomNumberService>();
    randomController_ = std::make_unique<RandomController>(*randomService_);

    randomController_->registerRoutes(server_);
    authController_->registerRoutes(server_);
    ticketController_->registerRoutes(server_);
    purchaseController_->registerRoutes(server_);

    std::cout << "Listening on http://" << host_ << ":" << port_ << "\n";
    std::cout << "GET  /random\n";
    std::cout << "POST /register\n";
    std::cout << "POST /login\n";
    std::cout << "POST /user/get\n";
    std::cout << "POST /tickets/create\n";
    std::cout << "POST /tickets/delete\n";
    std::cout << "POST /tickets/update\n";
    std::cout << "GET  /tickets\n";
    std::cout << "GET  /tickets/{id}\n";
    std::cout << "POST /purchase\n";
    std::cout << "POST /purchases/by-user\n";

    server_.listen(host_.c_str(), port_);

    bg_.stop();
}

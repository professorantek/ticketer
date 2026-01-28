// tests.cpp
// Unit tests for the backend
// Build: g++ -std=c++20 -O2 -pthread -DUNIT_TESTS tests.cpp AppServer.cpp BackgroundWorker.cpp Controllers.cpp Services.cpp FileRepositories.cpp Models.cpp JsonUtils.cpp RawBuffer.cpp -o tests

#include "FileRepositories.h"
#include "Services.h"
#include <fstream>
#include <cassert>
#include <iostream>

static void run_tests() {
    {
        std::ofstream("users_test.txt", std::ios::trunc).close();
        std::ofstream("tickets_test.txt", std::ios::trunc).close();
        std::ofstream("purchases_test.txt", std::ios::trunc).close();
    }

    FileUserRepository users("users_test.txt");
    AuthService auth(users);

    assert(auth.registerUser("jan", "haslo123") == AuthService::RegisterResult::Ok);
    assert(auth.loginUser("jan", "haslo123") == AuthService::LoginResult::Ok);
    assert(auth.loginUser("jan", "zle") == AuthService::LoginResult::InvalidCredentials);

    FileTicketRepository tickets("tickets_test.txt");
    TicketService ticketSvc(tickets);

    long long id1 = 0;
    assert(ticketSvc.createTicket("12345", 99.5, "Koncert", id1) == TicketService::CreateResult::Ok);
    assert(id1 == 1);

    long long id2 = 0;
    assert(ticketSvc.createTicket("12345", 50.0, "Mecz", id2) == TicketService::CreateResult::Ok);
    assert(id2 == 2);

    // update ticket
    assert(ticketSvc.updateTicket("12345", 2, 60.0, "Mecz Updated") == TicketService::UpdateResult::Ok);
    auto t2 = ticketSvc.getTicketById(2);
    assert(t2.has_value());
    assert(t2->price == 60.0);
    assert(t2->name == "Mecz Updated");

    FilePurchaseRepository purchases("purchases_test.txt");
    PurchaseService purchaseSvc(users, tickets, purchases);

    assert(purchaseSvc.purchase(2, "jan", "haslo123", 3) == PurchaseService::PurchaseResult::Ok);

    std::vector<std::pair<long long, long long>> counts;
    assert(purchaseSvc.listUserTickets("jan", "haslo123", counts) == PurchaseService::ListResult::Ok);

    assert(counts.size() == 1);
    assert(counts[0].first == 2);
    assert(counts[0].second == 3);

    std::cout << "All tests passed.\n";
}

int main() {
    run_tests();
    return 0;
}

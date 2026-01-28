#include "Controllers.h"
#include "JsonUtils.h"

// ======================================================
// AuthController
// ======================================================
AuthController::AuthController(AuthService& service)
    : service_(service) {}

void AuthController::registerRoutes(httplib::Server& server) {
    server.Post("/register", [this](const httplib::Request& req, httplib::Response& res) {
        auto loginOpt = extractJsonStringField(req.body, "login");
        auto passOpt  = extractJsonStringField(req.body, "password");
        if (!loginOpt || !passOpt) {
            res.status = 400;
            res.set_content("Invalid JSON. Expected {\"login\":\"...\",\"password\":\"...\"}",
                            "text/plain; charset=utf-8");
            return;
        }

        auto result = service_.registerUser(*loginOpt, *passOpt);
        switch (result) {
            case AuthService::RegisterResult::Ok:
                res.status = 201; res.set_content("User registered", "text/plain; charset=utf-8"); break;
            case AuthService::RegisterResult::LoginTaken:
                res.status = 409; res.set_content("Login already taken", "text/plain; charset=utf-8"); break;
            case AuthService::RegisterResult::InvalidInput:
                res.status = 400; res.set_content("Invalid login or password (3-32 chars, no spaces)", "text/plain; charset=utf-8"); break;
            default:
                res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
        }
    });

    server.Post("/login", [this](const httplib::Request& req, httplib::Response& res) {
        auto loginOpt = extractJsonStringField(req.body, "login");
        auto passOpt  = extractJsonStringField(req.body, "password");
        if (!loginOpt || !passOpt) {
            res.status = 400;
            res.set_content("Invalid JSON. Expected {\"login\":\"...\",\"password\":\"...\"}",
                            "text/plain; charset=utf-8");
            return;
        }

        auto result = service_.loginUser(*loginOpt, *passOpt);
        switch (result) {
            case AuthService::LoginResult::Ok:
                res.status = 200; res.set_content("Login OK", "text/plain; charset=utf-8"); break;
            case AuthService::LoginResult::InvalidInput:
                res.status = 400; res.set_content("Invalid login or password (3-32 chars, no spaces)", "text/plain; charset=utf-8"); break;
            case AuthService::LoginResult::InvalidCredentials:
                res.status = 401; res.set_content("Invalid login or password", "text/plain; charset=utf-8"); break;
            default:
                res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
        }
    });

    server.Post("/user/get", [this](const httplib::Request& req, httplib::Response& res) {
        auto loginOpt = extractJsonStringField(req.body, "login");
        auto passOpt  = extractJsonStringField(req.body, "password");
        if (!loginOpt || !passOpt) {
            res.status = 400;
            res.set_content("Invalid JSON. Expected {\"login\":\"...\",\"password\":\"...\"}",
                            "text/plain; charset=utf-8");
            return;
        }

        auto userOpt = service_.getUserIfAuthorized(*loginOpt, *passOpt);
        if (!userOpt) {
            res.status = 401;
            res.set_content("Invalid login or password", "text/plain; charset=utf-8");
            return;
        }

        std::string json = "{";
        json += "\"login\":\"" + escapeJsonString(userOpt->login) + "\",";
        json += "\"password\":\"" + escapeJsonString(userOpt->password) + "\"";
        json += "}";

        res.status = 200;
        res.set_content(json, "application/json; charset=utf-8");
    });
}

// ======================================================
// TicketController
// ======================================================
TicketController::TicketController(TicketService& service)
    : service_(service) {}

void TicketController::registerRoutes(httplib::Server& server) {
    server.Post("/tickets/create", [this](const httplib::Request& req, httplib::Response& res) {
        auto adminOpt = extractJsonStringField(req.body, "adminPassword");
        auto nameOpt  = extractJsonStringField(req.body, "name");
        auto priceOpt = extractJsonNumberField(req.body, "price");

        if (!adminOpt || !nameOpt || !priceOpt) {
            res.status = 400;
            res.set_content("Invalid JSON. Expected {\"adminPassword\":\"12345\",\"price\":...,\"name\":\"...\"}",
                            "text/plain; charset=utf-8");
            return;
        }

        long long newId = 0;
        auto result = service_.createTicket(*adminOpt, *priceOpt, *nameOpt, newId);

        switch (result) {
            case TicketService::CreateResult::Ok:
                res.status = 201;
                res.set_content("Ticket created: " + std::to_string(newId), "text/plain; charset=utf-8");
                break;
            case TicketService::CreateResult::Forbidden:
                res.status = 403; res.set_content("Forbidden", "text/plain; charset=utf-8"); break;
            case TicketService::CreateResult::InvalidInput:
                res.status = 400; res.set_content("Invalid price or name", "text/plain; charset=utf-8"); break;
            default:
                res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
        }
    });

    server.Post("/tickets/delete", [this](const httplib::Request& req, httplib::Response& res) {
        auto adminOpt = extractJsonStringField(req.body, "adminPassword");
        auto idOpt    = extractJsonIntField(req.body, "id");
        if (!adminOpt || !idOpt) {
            res.status = 400;
            res.set_content("Invalid JSON. Expected {\"adminPassword\":\"12345\",\"id\":...}",
                            "text/plain; charset=utf-8");
            return;
        }

        auto result = service_.deleteTicket(*adminOpt, *idOpt);
        switch (result) {
            case TicketService::DeleteResult::Ok:
                res.status = 200; res.set_content("Ticket deleted", "text/plain; charset=utf-8"); break;
            case TicketService::DeleteResult::NotFound:
                res.status = 404; res.set_content("Ticket not found", "text/plain; charset=utf-8"); break;
            case TicketService::DeleteResult::Forbidden:
                res.status = 403; res.set_content("Forbidden", "text/plain; charset=utf-8"); break;
            case TicketService::DeleteResult::InvalidInput:
                res.status = 400; res.set_content("Invalid id", "text/plain; charset=utf-8"); break;
            default:
                res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
        }
    });

    server.Post("/tickets/update", [this](const httplib::Request& req, httplib::Response& res) {
        auto adminOpt = extractJsonStringField(req.body, "adminPassword");
        auto idOpt    = extractJsonIntField(req.body, "id");
        auto nameOpt  = extractJsonStringField(req.body, "name");
        auto priceOpt = extractJsonNumberField(req.body, "price");

        if (!adminOpt || !idOpt || !nameOpt || !priceOpt) {
            res.status = 400;
            res.set_content(
                "Invalid JSON. Expected {\"adminPassword\":\"12345\",\"id\":...,\"price\":...,\"name\":\"...\"}",
                "text/plain; charset=utf-8"
            );
            return;
        }

        auto result = service_.updateTicket(*adminOpt, *idOpt, *priceOpt, *nameOpt);
        switch (result) {
            case TicketService::UpdateResult::Ok:
                res.status = 200; res.set_content("Ticket updated", "text/plain; charset=utf-8"); break;
            case TicketService::UpdateResult::NotFound:
                res.status = 404; res.set_content("Ticket not found", "text/plain; charset=utf-8"); break;
            case TicketService::UpdateResult::Forbidden:
                res.status = 403; res.set_content("Forbidden", "text/plain; charset=utf-8"); break;
            case TicketService::UpdateResult::InvalidInput:
                res.status = 400; res.set_content("Invalid id/price/name", "text/plain; charset=utf-8"); break;
            default:
                res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
        }
    });

    server.Get("/tickets", [this](const httplib::Request&, httplib::Response& res) {
        auto tickets = service_.getAllTickets();

        std::string json = "[";
        for (size_t i = 0; i < tickets.size(); i++) {
            const auto& t = tickets[i];
            json += "{";
            json += "\"id\":" + std::to_string(t.id) + ",";
            json += "\"price\":" + std::to_string(t.price) + ",";
            json += "\"name\":\"" + escapeJsonString(t.name) + "\"";
            json += "}";
            if (i + 1 < tickets.size()) json += ",";
        }
        json += "]";
        res.status = 200;
        res.set_content(json, "application/json; charset=utf-8");
    });

    server.Get(R"(/tickets/(\d+))", [this](const httplib::Request& req, httplib::Response& res) {
        long long id = 0;
        try { id = std::stoll(req.matches[1].str()); }
        catch (...) {
            res.status = 400;
            res.set_content("Invalid ticket id", "text/plain; charset=utf-8");
            return;
        }

        auto tOpt = service_.getTicketById(id);
        if (!tOpt) {
            res.status = 404;
            res.set_content("Ticket not found", "text/plain; charset=utf-8");
            return;
        }

        std::string json = "{";
        json += "\"id\":" + std::to_string(tOpt->id) + ",";
        json += "\"price\":" + std::to_string(tOpt->price) + ",";
        json += "\"name\":\"" + escapeJsonString(tOpt->name) + "\"";
        json += "}";

        res.status = 200;
        res.set_content(json, "application/json; charset=utf-8");
    });
}

// ======================================================
// PurchaseController
// ======================================================
PurchaseController::PurchaseController(PurchaseService& service)
    : service_(service) {}

void PurchaseController::registerRoutes(httplib::Server& server) {
    server.Post("/purchase", [this](const httplib::Request& req, httplib::Response& res) {
        auto ticketIdOpt = extractJsonIntField(req.body, "idBiletu");
        auto qtyOpt      = extractJsonIntField(req.body, "quantity");
        auto loginOpt    = extractJsonStringField(req.body, "login");
        auto passOpt     = extractJsonStringField(req.body, "password");

        if (!ticketIdOpt || !qtyOpt || !loginOpt || !passOpt) {
            res.status = 400;
            res.set_content("Invalid JSON. Expected {\"idBiletu\":...,\"quantity\":...,\"login\":\"...\",\"password\":\"...\"}",
                            "text/plain; charset=utf-8");
            return;
        }

        auto result = service_.purchase(*ticketIdOpt, *loginOpt, *passOpt, *qtyOpt);
        switch (result) {
            case PurchaseService::PurchaseResult::Ok:
                res.status = 201; res.set_content("Purchases saved", "text/plain; charset=utf-8"); break;
            case PurchaseService::PurchaseResult::Unauthorized:
                res.status = 401; res.set_content("Invalid login or password", "text/plain; charset=utf-8"); break;
            case PurchaseService::PurchaseResult::TicketNotFound:
                res.status = 404; res.set_content("Ticket not found", "text/plain; charset=utf-8"); break;
            case PurchaseService::PurchaseResult::InvalidInput:
                res.status = 400; res.set_content("Invalid input", "text/plain; charset=utf-8"); break;
            default:
                res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
        }
    });

    server.Post("/purchases/by-user", [this](const httplib::Request& req, httplib::Response& res) {
        auto loginOpt = extractJsonStringField(req.body, "login");
        auto passOpt  = extractJsonStringField(req.body, "password");
        if (!loginOpt || !passOpt) {
            res.status = 400;
            res.set_content("Invalid JSON. Expected {\"login\":\"...\",\"password\":\"...\"}",
                            "text/plain; charset=utf-8");
            return;
        }

        std::vector<std::pair<long long, long long>> counts;
        auto result = service_.listUserTickets(*loginOpt, *passOpt, counts);
        switch (result) {
            case PurchaseService::ListResult::Ok: {
                std::string json = "[";
                for (size_t i = 0; i < counts.size(); i++) {
                    json += "{";
                    json += "\"idBiletu\":" + std::to_string(counts[i].first) + ",";
                    json += "\"quantity\":" + std::to_string(counts[i].second);
                    json += "}";
                    if (i + 1 < counts.size()) json += ",";
                }
                json += "]";
                res.status = 200;
                res.set_content(json, "application/json; charset=utf-8");
                break;
            }
            case PurchaseService::ListResult::Unauthorized:
                res.status = 401; res.set_content("Invalid login or password", "text/plain; charset=utf-8"); break;
            case PurchaseService::ListResult::InvalidInput:
                res.status = 400; res.set_content("Invalid input", "text/plain; charset=utf-8"); break;
            default:
                res.status = 500; res.set_content("Storage error", "text/plain; charset=utf-8"); break;
        }
    });
}

// ======================================================
// RandomController
// ======================================================
RandomController::RandomController(RandomNumberService& service)
    : service_(service) {}

void RandomController::registerRoutes(httplib::Server& server) {
    server.Get("/random", [this](const httplib::Request&, httplib::Response& res) {
        res.set_content(std::to_string(service_.generate()), "text/plain; charset=utf-8");
    });
}

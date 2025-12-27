import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class Main extends Application {

    private Stage primaryStage;
    private final HttpClient client = HttpClient.newHttpClient();
    private final LocalizationManager loc = new LocalizationManager();

    // --- KONFIGURACJA BACKENDU ---
    private static final String API_BASE = "http://127.0.0.1:8080";
    // Hasło admina zdefiniowane w C++ (TicketService::kAdminPassword)
    private static final String HARDCODED_ADMIN_PASS = "12345"; 

    // --- STAN APLIKACJI ---
    private enum ViewType { LOGIN, REGISTER, USER_DASHBOARD, ADMIN_DASHBOARD }
    private ViewType currentViewType = ViewType.LOGIN;
    
    // Musimy pamiętać hasło, bo backend wymaga go przy każdej operacji (kupno/historia)
    private String currentLogin = "";
    private String currentPassword = ""; 

    // Cache biletów: ID -> Nazwa (potrzebne do wyświetlania historii zakupów)
    private final Map<Integer, String> ticketNameCache = new HashMap<>();

    // ==========================================
    // HELPER: PROSTY PARSER JSON
    // ==========================================
    public static class SimpleJsonParser {
        public static String getValue(String json, String key) {
            Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\"[^\"]*\"|[\\d\\.]+|true|false)");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                String val = matcher.group(1);
                if (val.startsWith("\"") && val.endsWith("\"")) {
                    return val.substring(1, val.length() - 1);
                }
                return val;
            }
            return "";
        }

        public static List<String> parseArray(String jsonArray) {
            List<String> items = new ArrayList<>();
            if (jsonArray == null || !jsonArray.trim().startsWith("[")) return items;
            
            String content = jsonArray.trim();
            if (content.length() < 2) return items;
            content = content.substring(1, content.length() - 1);

            int braceCount = 0;
            StringBuilder current = new StringBuilder();
            for (char c : content.toCharArray()) {
                if (c == '{') braceCount++;
                if (c == '}') braceCount--;
                
                if (c == ',' && braceCount == 0) {
                    items.add(current.toString());
                    current = new StringBuilder();
                } else {
                    current.append(c);
                }
            }
            if (current.length() > 0) items.add(current.toString());
            return items;
        }
    }

    // ==========================================
    // KLASA DO OBSŁUGI LOKALIZACJI
    // ==========================================
    public static class LocalizationManager {
        private final Map<String, String> translations = new HashMap<>();

        public void loadFromFile(String filename) {
            translations.clear();
            File file = new File(filename);
            if (!file.exists()) return;
            try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) translations.put(parts[0].trim(), parts[1].trim());
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        public String get(String key) {
            return translations.getOrDefault(key, "[" + key + "]");
        }
    }

    // ==========================================
    // START
    // ==========================================
    private VBox userDashboardContent;

    @Override
    public void start(Stage stage) {
        loc.loadFromFile("localization-pl.txt");
        this.primaryStage = stage;
        this.primaryStage.setTitle(loc.get("app.title"));
        showLoginView();
        this.primaryStage.show();
    }

    private void switchLanguage(String langCode) {
        if ("EN".equals(langCode)) loc.loadFromFile("localization-en.txt");
        else loc.loadFromFile("localization-pl.txt");
        
        primaryStage.setTitle(loc.get("app.title"));
        switch (currentViewType) {
            case LOGIN -> showLoginView();
            case REGISTER -> showRegisterView();
            case USER_DASHBOARD -> showUserDashboard(currentLogin);
            case ADMIN_DASHBOARD -> showAdminDashboard(currentLogin);
        }
    }

    // ==========================================
    // 1. EKRAN LOGOWANIA
    // ==========================================
    private void showLoginView() {
        currentViewType = ViewType.LOGIN;

        Label title = createStyledLabel(loc.get("login.title"), 24);
        TextField loginField = createStyledTextField(loc.get("prompt.login"));
        PasswordField passField = createStyledPasswordField(loc.get("prompt.password"));
        Label statusLabel = createStyledLabel("", 12);

        Button loginBtn = createStyledButton(loc.get("login.btn"));
        Button goToRegisterBtn = new Button(loc.get("login.register_link"));
        styleLinkButton(goToRegisterBtn);

        // Debug buttons (symulacja)
        Button debugUserBtn = new Button(loc.get("debug.user"));
        debugUserBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8;");
        debugUserBtn.setMaxWidth(Double.MAX_VALUE);
        debugUserBtn.setOnAction(e -> {
            currentLogin = "developer"; currentPassword = "dev"; 
            showUserDashboard("developer");
        });

        VBox debugBox = new VBox(10, debugUserBtn);
        debugBox.setAlignment(Pos.CENTER);
        debugBox.setPadding(new Insets(10, 0, 0, 0));

        // --- LOGIN ACTION ---
        loginBtn.setOnAction(e -> {
            String login = loginField.getText();
            String pass = passField.getText();

            if (login.isEmpty() || pass.isEmpty()) {
                statusLabel.setTextFill(Color.RED);
                statusLabel.setText(loc.get("login.status.empty"));
                return;
            }
            statusLabel.setText(loc.get("login.status.process"));
            statusLabel.setTextFill(Color.BLACK);
            loginBtn.setDisable(true);

            // C++ oczekuje kluczy: "login", "password"
            String json = String.format("{\"login\":\"%s\", \"password\":\"%s\"}", login, pass);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> Platform.runLater(() -> {
                        loginBtn.setDisable(false);
                        if (resp.statusCode() == 200) {
                            // ZAPISUJEMY DANE DO SESJI
                            currentLogin = login;
                            currentPassword = pass;

                            if ("admin".equalsIgnoreCase(login)) {
                                showAdminDashboard(login);
                            } else {
                                showUserDashboard(login);
                            }
                        } else {
                            statusLabel.setTextFill(Color.RED);
                            statusLabel.setText("Błąd: " + resp.statusCode() + " (" + resp.body() + ")");
                        }
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            loginBtn.setDisable(false);
                            statusLabel.setTextFill(Color.RED);
                            statusLabel.setText("Błąd połączenia!");
                        });
                        return null;
                    });
        });

        goToRegisterBtn.setOnAction(e -> showRegisterView());
        VBox layout = createBaseLayout(title, loginField, passField, loginBtn, statusLabel, goToRegisterBtn, debugBox);
        primaryStage.setScene(new Scene(layout, 420, 600));
    }

    // ==========================================
    // 2. EKRAN REJESTRACJI
    // ==========================================
    private void showRegisterView() {
        currentViewType = ViewType.REGISTER;
        Label title = createStyledLabel(loc.get("register.title"), 24);
        TextField loginField = createStyledTextField(loc.get("prompt.login"));
        PasswordField passField = createStyledPasswordField(loc.get("prompt.password"));
        PasswordField passConfirmField = createStyledPasswordField(loc.get("prompt.password_confirm"));
        Label statusLabel = createStyledLabel("", 12);

        Button registerBtn = createStyledButton(loc.get("register.btn"));
        Button backBtn = new Button(loc.get("register.back_btn"));
        styleLinkButton(backBtn);

        registerBtn.setOnAction(e -> {
            String login = loginField.getText();
            String pass = passField.getText();
            String passConf = passConfirmField.getText();

            if (login.isEmpty() || pass.isEmpty()) {
                statusLabel.setTextFill(Color.RED);
                statusLabel.setText(loc.get("login.status.empty"));
                return;
            }
            if (!pass.equals(passConf)) {
                statusLabel.setTextFill(Color.RED);
                statusLabel.setText(loc.get("register.status.pass_mismatch"));
                return;
            }

            statusLabel.setText(loc.get("register.status.process"));
            statusLabel.setTextFill(Color.BLACK);
            registerBtn.setDisable(true);

            // C++ POST /register {"login": "...", "password": "..."}
            String json = String.format("{\"login\":\"%s\", \"password\":\"%s\"}", login, pass);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> Platform.runLater(() -> {
                        registerBtn.setDisable(false);
                        if (resp.statusCode() == 201) {
                            statusLabel.setTextFill(Color.GREEN);
                            statusLabel.setText(loc.get("register.success"));
                        } else if (resp.statusCode() == 409) {
                            statusLabel.setTextFill(Color.RED);
                            statusLabel.setText("Login zajęty!");
                        } else {
                            statusLabel.setTextFill(Color.RED);
                            statusLabel.setText("Błąd: " + resp.statusCode());
                        }
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            registerBtn.setDisable(false);
                            statusLabel.setTextFill(Color.RED);
                            statusLabel.setText("Brak połączenia.");
                        });
                        return null;
                    });
        });

        backBtn.setOnAction(e -> showLoginView());
        VBox layout = createBaseLayout(title, loginField, passField, passConfirmField, registerBtn, statusLabel, backBtn);
        primaryStage.setScene(new Scene(layout, 420, 550));
    }

    // ==========================================
    // 3. PANEL UŻYTKOWNIKA
    // ==========================================
    private void showUserDashboard(String username) {
        currentViewType = ViewType.USER_DASHBOARD;
        
        Label welcomeLabel = createStyledLabel(loc.get("dashboard.welcome") + " " + username + "!", 18);
        Button btnAvailable = new Button(loc.get("menu.offer"));
        Button btnMyTickets = new Button(loc.get("menu.my_tickets"));
        styleMenuButton(btnAvailable);
        styleMenuButton(btnMyTickets);

        HBox menu = new HBox(10, btnAvailable, btnMyTickets);
        menu.setAlignment(Pos.CENTER);

        userDashboardContent = new VBox(10);
        userDashboardContent.setPadding(new Insets(10));
        userDashboardContent.setAlignment(Pos.TOP_CENTER);
        
        ScrollPane scrollPane = new ScrollPane(userDashboardContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        scrollPane.setPrefHeight(350);

        Button logoutBtn = new Button(loc.get("menu.logout"));
        styleLinkButton(logoutBtn);
        logoutBtn.setOnAction(e -> {
            currentLogin = "";
            currentPassword = "";
            showLoginView();
        });

        btnAvailable.setOnAction(e -> loadAvailableTickets());
        btnMyTickets.setOnAction(e -> loadMyTickets());

        // Najpierw ładujemy dostępne bilety, aby zapełnić cache nazw
        loadAvailableTickets(); 

        VBox layout = createBaseLayout(welcomeLabel, menu, scrollPane, logoutBtn);
        primaryStage.setScene(new Scene(layout, 420, 600));
    }

    // --- POBIERANIE DOSTĘPNYCH BILETÓW (GET /tickets) ---
    private void loadAvailableTickets() {
        userDashboardContent.getChildren().clear();
        Label title = new Label(loc.get("dashboard.available_title"));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        userDashboardContent.getChildren().add(title);
        Label statusLbl = new Label("Ładowanie...");
        userDashboardContent.getChildren().add(statusLbl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/tickets"))
                .GET()
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> Platform.runLater(() -> {
                    userDashboardContent.getChildren().remove(statusLbl);
                    if (resp.statusCode() == 200) {
                        List<String> items = SimpleJsonParser.parseArray(resp.body());
                        if (items.isEmpty()) userDashboardContent.getChildren().add(new Label("Brak biletów."));

                        // Czyścimy cache przy odświeżaniu
                        ticketNameCache.clear();

                        for (String itemJson : items) {
                            try {
                                // C++ zwraca: {"id":1, "price":100.0, "name":"..."}
                                int id = Integer.parseInt(SimpleJsonParser.getValue(itemJson, "id"));
                                String name = SimpleJsonParser.getValue(itemJson, "name");
                                double price = Double.parseDouble(SimpleJsonParser.getValue(itemJson, "price"));
                                
                                // Zapisujemy do cache, żeby "Moje bilety" mogły tego użyć
                                ticketNameCache.put(id, name);

                                String priceStr = String.format("%.2f PLN", price);
                                // Dostępność nie jest zwracana przez ten endpoint, więc ukrywamy lub dajemy "-"
                                userDashboardContent.getChildren().add(
                                    createTicketCard(id, name, priceStr, -1, loc.get("ticket.buy_btn"), false)
                                );
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    } else {
                        userDashboardContent.getChildren().add(new Label("Błąd serwera: " + resp.statusCode()));
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        userDashboardContent.getChildren().remove(statusLbl);
                        userDashboardContent.getChildren().add(new Label("Brak połączenia."));
                    });
                    return null;
                });
    }

    // --- POBIERANIE MOICH BILETÓW (POST /purchases/by-user) ---
    private void loadMyTickets() {
        userDashboardContent.getChildren().clear();
        Label title = new Label(loc.get("dashboard.my_tickets_title"));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        userDashboardContent.getChildren().add(title);
        Label statusLbl = new Label("Ładowanie...");
        userDashboardContent.getChildren().add(statusLbl);

        // Backend wymaga POST z loginem i hasłem
        String json = String.format("{\"login\":\"%s\", \"password\":\"%s\"}", currentLogin, currentPassword);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/purchases/by-user"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> Platform.runLater(() -> {
                    userDashboardContent.getChildren().remove(statusLbl);
                    if (resp.statusCode() == 200) {
                        List<String> items = SimpleJsonParser.parseArray(resp.body());
                        if (items.isEmpty()) userDashboardContent.getChildren().add(new Label("Nie masz jeszcze biletów."));

                        for (String itemJson : items) {
                            // C++ zwraca: [{"idBiletu": 2, "quantity": 3}, ...]
                            String idStr = SimpleJsonParser.getValue(itemJson, "idBiletu");
                            String qtyStr = SimpleJsonParser.getValue(itemJson, "quantity");
                            
                            if (!idStr.isEmpty() && !qtyStr.isEmpty()) {
                                int id = Integer.parseInt(idStr);
                                int qty = Integer.parseInt(qtyStr);
                                
                                // Pobieramy nazwę z cache (z załadowanych wcześniej /tickets)
                                String name = ticketNameCache.getOrDefault(id, "Bilet ID: " + id);

                                userDashboardContent.getChildren().add(
                                    createTicketCard(id, name, "Zapłacono", qty, loc.get("ticket.qr_btn"), true)
                                );
                            }
                        }
                    } else if (resp.statusCode() == 401) {
                         userDashboardContent.getChildren().add(new Label("Sesja wygasła. Zaloguj się ponownie."));
                    } else {
                        userDashboardContent.getChildren().add(new Label("Błąd: " + resp.statusCode()));
                    }
                }));
    }

    // ==========================================
    // 4. PANEL ADMINA
    // ==========================================
    private void showAdminDashboard(String username) {
        currentViewType = ViewType.ADMIN_DASHBOARD;

        Label welcomeLabel = createStyledLabel(loc.get("admin.title") + " " + username, 18);
        welcomeLabel.setTextFill(Color.DARKRED);
        Label subTitle = new Label(loc.get("admin.subtitle"));
        
        TextField eventNameField = createStyledTextField(loc.get("prompt.event_name"));
        TextField priceField = createStyledTextField(loc.get("prompt.price"));
        // Ilość nie jest już wymagana przez endpoint /tickets/create w C++, ale jest pole na froncie
        // Możemy je ukryć lub zostawić jako atrapę, backend C++ bierze tylko name i price
        // (W Twoim kodzie C++ createTicket nie przyjmuje quantity, generuje tylko ID, name, price)
        
        Label statusLabel = createStyledLabel("", 12);

        Button addBtn = createStyledButton(loc.get("admin.add_btn"));
        addBtn.setStyle("-fx-background-color: #be123c; -fx-text-fill: white; -fx-background-radius: 12; -fx-font-weight: bold; -fx-cursor: hand;");

        Button logoutBtn = new Button(loc.get("menu.logout"));
        styleLinkButton(logoutBtn);
        logoutBtn.setOnAction(e -> {
            currentLogin = ""; currentPassword = "";
            showLoginView();
        });

        // --- AKCJA DODAWANIA BILETU (POST /tickets/create) ---
        addBtn.setOnAction(e -> {
            String name = eventNameField.getText();
            String priceStr = priceField.getText();

            if (name.isEmpty() || priceStr.isEmpty()) {
                statusLabel.setTextFill(Color.RED);
                statusLabel.setText("Wypełnij nazwę i cenę!");
                return;
            }

            addBtn.setDisable(true);
            statusLabel.setText("Wysyłanie...");
            statusLabel.setTextFill(Color.BLACK);

            String safePrice = priceStr.replace(",", ".");
            
            // C++: {"adminPassword":"...", "name":"...", "price":...}
            String json = String.format(Locale.US, 
                "{\"adminPassword\":\"%s\", \"name\":\"%s\", \"price\":%s}", 
                HARDCODED_ADMIN_PASS, name, safePrice
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/tickets/create"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> Platform.runLater(() -> {
                        addBtn.setDisable(false);
                        if (resp.statusCode() == 201) {
                            statusLabel.setTextFill(Color.GREEN);
                            statusLabel.setText(loc.get("admin.status.success") + " " + name);
                            eventNameField.clear();
                            priceField.clear();
                        } else if (resp.statusCode() == 403) {
                            statusLabel.setTextFill(Color.RED);
                            statusLabel.setText("Brak uprawnień (złe hasło admina).");
                        } else {
                            statusLabel.setTextFill(Color.RED);
                            statusLabel.setText("Błąd: " + resp.statusCode());
                        }
                    }));
        });

        VBox formBox = new VBox(10, subTitle, eventNameField, priceField, addBtn, statusLabel);
        formBox.setAlignment(Pos.CENTER);
        formBox.setPadding(new Insets(20));
        formBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);");

        VBox layout = createBaseLayout(welcomeLabel, formBox, logoutBtn);
        primaryStage.setScene(new Scene(layout, 420, 600));
    }

    // ==========================================
    // 5. OKNO ZAKUPU
    // ==========================================
    private void showBuyConfirmationWindow(int ticketId, String ticketName, String priceString) {
        Stage buyStage = new Stage();
        buyStage.initModality(Modality.APPLICATION_MODAL);
        buyStage.setTitle(loc.get("buy.title"));

        Label header = new Label(loc.get("buy.header"));
        Label nameLabel = new Label(ticketName);
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        Label singlePriceLabel = new Label(loc.get("buy.price_single") + " " + priceString);

        HBox quantityBox = new HBox(10);
        quantityBox.setAlignment(Pos.CENTER);
        Label qtyLabel = new Label(loc.get("buy.qty_label"));
        Spinner<Integer> quantitySpinner = new Spinner<>(1, 10, 1);
        quantitySpinner.setPrefWidth(80);
        quantityBox.getChildren().addAll(qtyLabel, quantitySpinner);

        Label totalPriceLabel = new Label(loc.get("buy.total") + " " + priceString);
        totalPriceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2563eb;");

        double singlePrice = 0.0;
        try {
            String cleanPrice = priceString.replace(" PLN", "").replace(",", ".").trim();
            singlePrice = Double.parseDouble(cleanPrice);
        } catch (Exception e) { singlePrice = 0.0; }
        final double finalSinglePrice = singlePrice;

        quantitySpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            double total = finalSinglePrice * newVal;
            totalPriceLabel.setText(String.format(Locale.US, "%s %.2f PLN", loc.get("buy.total"), total));
        });

        CheckBox oathCheckbox = new CheckBox(loc.get("buy.checkbox"));
        Button confirmBtn = new Button(loc.get("buy.confirm_btn"));
        confirmBtn.setDisable(true); 
        confirmBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");

        oathCheckbox.selectedProperty().addListener((observable, oldValue, newValue) -> confirmBtn.setDisable(!newValue));

        Label statusLabel = new Label("");

        // --- AKCJA KUPNA (POST /purchase) ---
        confirmBtn.setOnAction(e -> {
            int quantityToBuy = quantitySpinner.getValue();
            confirmBtn.setDisable(true);

            // C++ oczekuje: {"idBiletu":..., "quantity":..., "login":"...", "password":"..."}
            String json = String.format("{\"idBiletu\":%d, \"quantity\":%d, \"login\":\"%s\", \"password\":\"%s\"}", 
                ticketId, quantityToBuy, currentLogin, currentPassword);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/purchase"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> Platform.runLater(() -> {
                        if (resp.statusCode() == 201) {
                            statusLabel.setStyle("-fx-text-fill: green;");
                            statusLabel.setText(loc.get("buy.success"));
                            // Zamykamy okno po chwili
                            new java.util.Timer().schedule(new java.util.TimerTask() {
                                @Override public void run() { Platform.runLater(() -> buyStage.close()); }
                            }, 1000);
                        } else {
                            statusLabel.setStyle("-fx-text-fill: red;");
                            statusLabel.setText("Błąd: " + resp.statusCode());
                            confirmBtn.setDisable(false);
                        }
                    }));
        });

        VBox layout = new VBox(10, header, nameLabel, singlePriceLabel, quantityBox, totalPriceLabel, oathCheckbox, confirmBtn, statusLabel);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30));
        layout.setStyle("-fx-background-color: white;");
        buyStage.setScene(new Scene(layout, 350, 400));
        buyStage.show();
    }

    private void showQRWindow(String ticketName, String codeData) {
        Stage qrStage = new Stage();
        qrStage.initModality(Modality.APPLICATION_MODAL); 
        qrStage.setTitle("Bilet: " + ticketName);

        Label header = new Label(ticketName);
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        String apiUrl = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=" + codeData;
        ImageView qrImage = new ImageView();
        try {
            Image image = new Image(apiUrl, true);
            qrImage.setImage(image);
        } catch (Exception e) { header.setText(loc.get("qr.error")); }

        Button closeBtn = new Button(loc.get("qr.close_btn"));
        closeBtn.setOnAction(e -> qrStage.close());
        closeBtn.setStyle("-fx-background-color: #333; -fx-text-fill: white; -fx-cursor: hand;");

        VBox layout = new VBox(15, header, qrImage, closeBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: white;");
        qrStage.setScene(new Scene(layout, 300, 400));
        qrStage.show();
    }

    // ==========================================
    // STYLE I HELPERY
    // ==========================================
    private VBox createBaseLayout(javafx.scene.Node... children) {
        Button btnPL = new Button("PL");
        Button btnEN = new Button("EN");
        String style = "-fx-background-color: transparent; -fx-font-weight: bold; -fx-cursor: hand; -fx-text-fill: #555; -fx-border-color: #ccc; -fx-border-radius: 4;";
        btnPL.setStyle(style);
        btnEN.setStyle(style);
        
        btnPL.setOnAction(e -> switchLanguage("PL"));
        btnEN.setOnAction(e -> switchLanguage("EN"));
        
        HBox langBox = new HBox(5, btnPL, btnEN);
        langBox.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox();
        root.setSpacing(14);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.getChildren().add(langBox);
        root.getChildren().addAll(children);
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #f8fafc, #eef2ff); -fx-font-family: 'Segoe UI', 'Inter', 'Arial';");
        return root;
    }

    private HBox createTicketCard(int ticketId, String eventName, String priceInfo, int quantity, String btnText, boolean isOwned) {
        HBox card = new HBox();
        card.setPadding(new Insets(15));
        card.setSpacing(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);");

        VBox infoBox = new VBox(5);
        Label nameLbl = new Label(eventName);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label priceLbl = new Label(priceInfo);
        priceLbl.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");
        
        // Jeśli quantity < 0 to znaczy że nie wyświetlamy ilości (np. przy zakupie nie znamy stanu magazynowego z tego endpointu)
        if (quantity >= 0) {
            String qtyText = (isOwned ? loc.get("ticket.owned") : loc.get("ticket.available")) + " " + quantity + " " + loc.get("ticket.unit");
            Label qtyLabel = new Label(qtyText);
            qtyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888; -fx-font-weight: bold;");
            infoBox.getChildren().add(qtyLabel);
        }
        infoBox.getChildren().add(0, nameLbl);
        infoBox.getChildren().add(1, priceLbl);

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button actionBtn = new Button(btnText);
        String btnColor = isOwned ? "#10b981" : "#3b82f6"; 
        actionBtn.setStyle("-fx-background-color: " + btnColor + "; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");

        actionBtn.setOnAction(e -> {
            if (isOwned) {
                // Generujemy przykładowy kod QR
                String randomCode = "ID:" + ticketId + "-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase();
                showQRWindow(eventName, randomCode);
            } else {
                showBuyConfirmationWindow(ticketId, eventName, priceInfo);
            }
        });

        card.getChildren().addAll(infoBox, spacer, actionBtn);
        return card;
    }

    private TextField createStyledTextField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setMaxWidth(300);
        field.setPrefHeight(40);
        field.setStyle("-fx-background-radius: 8; -fx-border-color: #ccc; -fx-padding: 5;");
        return field;
    }

    private PasswordField createStyledPasswordField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.setMaxWidth(300);
        field.setPrefHeight(40);
        field.setStyle("-fx-background-radius: 8; -fx-border-color: #ccc; -fx-padding: 5;");
        return field;
    }

    private Button createStyledButton(String text) {
        Button button = new Button(text);
        button.setPrefWidth(220);
        button.setPrefHeight(44);
        button.setStyle("-fx-background-radius: 12; -fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: white; -fx-background-color: linear-gradient(to right, #2563eb, #7c3aed); -fx-cursor: hand;");
        button.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.25)));
        return button;
    }

    private void styleLinkButton(Button btn) {
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #2563eb; -fx-underline: true; -fx-cursor: hand;");
    }

    private void styleMenuButton(Button btn) {
        btn.setPrefWidth(140);
        btn.setStyle("-fx-background-radius: 8; -fx-background-color: #ffffff; -fx-text-fill: #333; -fx-font-weight: bold; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);");
    }

    private Label createStyledLabel(String text, int fontSize) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: " + fontSize + "px; -fx-font-weight: 700;");
        return label;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
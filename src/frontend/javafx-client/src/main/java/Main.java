import java.net.http.HttpClient;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class Main extends Application {

    private Stage primaryStage;
    private final HttpClient client = HttpClient.newHttpClient();

    // --- KONFIGURACJA URLI (Tutaj wpisz swoje adresy backendu) ---
    private static final String API_BASE = "http://127.0.0.1:8080";
    private static final String LOGIN_ENDPOINT = API_BASE + "/login";
    private static final String REGISTER_ENDPOINT = API_BASE + "/register";
    private static final String RANDOM_ENDPOINT = API_BASE + "/random";

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.primaryStage.setTitle("System Biletowy");
        
        // Na start pokazujemy ekran logowania
        showLoginView();
        
        this.primaryStage.show();
    }

    // ==========================================
    // 1. WIDOK LOGOWANIA
    // ==========================================
    private void showLoginView() {
        Label title = createStyledLabel("Logowanie", 24);
        TextField loginField = createStyledTextField("Login");
        PasswordField passField = createStyledPasswordField("Hasło");
        Label statusLabel = createStyledLabel("", 12);
        statusLabel.setTextFill(Color.RED);

        Button loginBtn = createStyledButton("Zaloguj się");
        Button goToRegisterBtn = new Button("Nie masz konta? Zarejestruj się");
        styleLinkButton(goToRegisterBtn);

        // --- PRZYCISK DEBUGOWANIA (Szybkie przejście) ---
        Button debugSkipBtn = new Button("🛠 [DEBUG] Pomiń logowanie");
        debugSkipBtn.setStyle(
            "-fx-background-color: #ef4444;" + // Czerwony kolor ostrzegawczy
            "-fx-text-fill: white;" +
            "-fx-font-weight: bold;" +
            "-fx-background-radius: 8;" +
            "-fx-padding: 8 16;" +
            "-fx-cursor: hand;"
        );
        
        debugSkipBtn.setOnAction(e -> {
            System.out.println("DEBUG: Pominięto logowanie.");
            showDashboardView("Developer"); // Wchodzimy jako użytkownik "Developer"
        });

        // --- AKCJA: LOGOWANIE ---
        loginBtn.setOnAction(e -> {
            String login = loginField.getText();
            String pass = passField.getText();

            if (login.isEmpty() || pass.isEmpty()) {
                statusLabel.setText("Wypełnij wszystkie pola!");
                return;
            }

            loginBtn.setDisable(true);
            statusLabel.setText("Logowanie...");
            statusLabel.setTextFill(Color.BLACK);

            // >>> TU ENDPOINT LOGOWANIA (zakomentowany) <<<
            /*
            // ... kod http ...
            */

            // TYMCZASOWA SYMULACJA:
            Platform.runLater(() -> {
                showDashboardView(login);
            });
        });

        goToRegisterBtn.setOnAction(e -> showRegisterView());

        // Dodałem debugSkipBtn na końcu listy elementów
        VBox layout = createBaseLayout(title, loginField, passField, loginBtn, statusLabel, goToRegisterBtn, debugSkipBtn);
        primaryStage.setScene(new Scene(layout, 420, 500)); // Zwiększyłem nieco wysokość okna
    }

    // ==========================================
    // 2. WIDOK REJESTRACJI
    // ==========================================
    private void showRegisterView() {
        Label title = createStyledLabel("Rejestracja", 24);
        TextField loginField = createStyledTextField("Login");
        PasswordField passField = createStyledPasswordField("Hasło");
        PasswordField passConfirmField = createStyledPasswordField("Powtórz hasło");
        Label statusLabel = createStyledLabel("", 12);
        statusLabel.setTextFill(Color.RED);

        Button registerBtn = createStyledButton("Utwórz konto");
        Button backBtn = new Button("Wróć do logowania");
        styleLinkButton(backBtn);

        // --- AKCJA: REJESTRACJA ---
        registerBtn.setOnAction(e -> {
            String login = loginField.getText();
            String pass = passField.getText();
            String passConf = passConfirmField.getText();

            if (login.isEmpty() || pass.isEmpty()) {
                statusLabel.setText("Wypełnij wszystkie pola!");
                return;
            }
            if (!pass.equals(passConf)) {
                statusLabel.setText("Hasła nie są identyczne!");
                return;
            }

            registerBtn.setDisable(true);
            statusLabel.setText("Tworzenie konta...");
            statusLabel.setTextFill(Color.BLACK);

            // >>> TU WSTAW ZAPYTANIE DO ENDPOINTU REJESTRACJI <<<
            /*
            String jsonBody = String.format("{\"username\":\"%s\", \"password\":\"%s\"}", login, pass);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REGISTER_ENDPOINT))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> Platform.runLater(() -> {
                        if (resp.statusCode() == 200 || resp.statusCode() == 201) {
                             statusLabel.setTextFill(Color.GREEN);
                             statusLabel.setText("Konto utworzone! Możesz się zalogować.");
                             // Opcjonalnie: showLoginView(); po chwili
                        } else {
                             statusLabel.setTextFill(Color.RED);
                             statusLabel.setText("Błąd rejestracji: " + resp.body());
                        }
                        registerBtn.setDisable(false);
                    }));
            */

            // TYMCZASOWA SYMULACJA (Do usunięcia):
            Platform.runLater(() -> {
                System.out.println("DEBUG: Symulacja rejestracji dla: " + login);
                statusLabel.setTextFill(Color.GREEN);
                statusLabel.setText("Sukces! (Symulacja)");
                registerBtn.setDisable(false);
            });
        });

        backBtn.setOnAction(e -> showLoginView());

        VBox layout = createBaseLayout(title, loginField, passField, passConfirmField, registerBtn, statusLabel, backBtn);
        primaryStage.setScene(new Scene(layout, 420, 500));
    }

    // ==========================================
    // 3. WIDOK GŁÓWNY (DASHBOARD) - Twój oryginalny kod
    // ==========================================
    // ==========================================
    // 3. WIDOK GŁÓWNY (DASHBOARD - BILETY)
    // ==========================================
    // --- ŁADOWANIE DOSTĘPNYCH BILETÓW (SKLEP) ---
    // --- POMOCNICZA: TWORZENIE KARTY BILETU ---
    private javafx.scene.layout.HBox createTicketCard(String eventName, String priceInfo, String btnText, boolean isOwned) {
        javafx.scene.layout.HBox card = new javafx.scene.layout.HBox();
        card.setPadding(new Insets(15));
        card.setSpacing(10);
        card.setAlignment(Pos.CENTER_LEFT);
        
        // Styl karty (białe tło, cień, zaokrąglenie)
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12;" +
            "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);"
        );

        // Lewa strona: Nazwa i Cena
        VBox infoBox = new VBox(5);
        Label nameLbl = new Label(eventName);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label priceLbl = new Label(priceInfo);
        priceLbl.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");
        infoBox.getChildren().addAll(nameLbl, priceLbl);
        
        // Rozpychanie (żeby przycisk był po prawej)
        javafx.scene.layout.HBox spacer = new javafx.scene.layout.HBox();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        // Przycisk akcji (Kup / Zobacz)
        Button actionBtn = new Button(btnText);
        if (isOwned) {
            // Styl dla posiadanego biletu (szary/zielony)
            actionBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");
        } else {
            // Styl dla przycisku kupna (niebieski)
            actionBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");
        }

        // --- OBSŁUGA KLIKNIĘCIA "KUP" ---
        actionBtn.setOnAction(e -> {
            if (!isOwned) {
                System.out.println("DEBUG: Kliknięto kupno: " + eventName);
                // >>> TU WSTAW POST DO /buy-ticket <<<
            } else {
                System.out.println("DEBUG: Wyświetlanie szczegółów biletu: " + eventName);
            }
        });

        card.getChildren().addAll(infoBox, spacer, actionBtn);
        return card;
    }
    private void loadAvailableTickets() {
        dashboardContent.getChildren().clear(); // Wyczyść poprzedni widok
        Label title = new Label("Oferta wydarzeń:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        dashboardContent.getChildren().add(title);

        // >>> TU WSTAW ZAPYTANIE GET DO /tickets/available <<<
        /*
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(API_BASE + "/tickets")).GET().build();
        client.sendAsync(...)
            .thenAccept(resp -> {
                 // Parsowanie JSON -> pętla po biletach -> addTicketCard(...)
            });
        */

        // TYMCZASOWA SYMULACJA DANYCH Z BAZY
        dashboardContent.getChildren().add(createTicketCard("Koncert Rockowy", "150.00 PLN", "Kup", false));
        dashboardContent.getChildren().add(createTicketCard("Mecz Polska-Niemcy", "220.00 PLN", "Kup", false));
        dashboardContent.getChildren().add(createTicketCard("Teatr: Hamlet", "80.00 PLN", "Kup", false));
    }

    // --- ŁADOWANIE MOICH BILETÓW (PORTFEL) ---
    private void loadMyTickets() {
        dashboardContent.getChildren().clear();
        Label title = new Label("Twoje zakupione bilety:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        dashboardContent.getChildren().add(title);

        // >>> TU WSTAW ZAPYTANIE GET DO /my-tickets <<<
        
        // TYMCZASOWA SYMULACJA
        dashboardContent.getChildren().add(createTicketCard("Kino: Avatar 3", "Zapłacono", "Zobacz QR", true));
    }
    private VBox dashboardContent; // Kontener na listę biletów

    private void showDashboardView(String username) {
        // --- NAGŁÓWEK ---
        Label welcomeLabel = createStyledLabel("Witaj, " + username + "!", 18);
        
        // --- MENU NAWIGACYJNE (GÓRA) ---
        Button btnAvailable = new Button("🛒 Dostępne bilety");
        Button btnMyTickets = new Button("🎟 Moje bilety");
        
        // Stylizacja przycisków menu (trochę mniejsze niż główne)
        String menuBtnStyle = "-fx-background-radius: 8; -fx-background-color: #ffffff; -fx-text-fill: #333; -fx-font-weight: bold; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);";
        btnAvailable.setStyle(menuBtnStyle);
        btnMyTickets.setStyle(menuBtnStyle);
        btnAvailable.setPrefWidth(140);
        btnMyTickets.setPrefWidth(140);

        javafx.scene.layout.HBox menu = new javafx.scene.layout.HBox(10, btnAvailable, btnMyTickets);
        menu.setAlignment(Pos.CENTER);

        // --- OBSZAR TREŚCI (TUTAJ BĘDĄ SIĘ POJAWIAĆ BILETY) ---
        dashboardContent = new VBox(10);
        dashboardContent.setPadding(new Insets(10));
        dashboardContent.setAlignment(Pos.TOP_CENTER);
        
        // ScrollPane, żeby można było przewijać, jak będzie dużo biletów
        ScrollPane scrollPane = new ScrollPane(dashboardContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPrefHeight(300);

        // --- PRZYCISK WYLOGUJ (DÓŁ) ---
        Button logoutBtn = new Button("Wyloguj");
        styleLinkButton(logoutBtn);
        logoutBtn.setOnAction(e -> showLoginView());

        // --- LOGIKA PRZYCISKÓW MENU ---
        btnAvailable.setOnAction(e -> loadAvailableTickets());
        btnMyTickets.setOnAction(e -> loadMyTickets());

        // Domyślnie ładujemy dostępne bilety
        loadAvailableTickets();

        // --- UKŁAD CAŁOŚCI ---
        VBox layout = createBaseLayout(welcomeLabel, menu, scrollPane, logoutBtn);
        primaryStage.setScene(new Scene(layout, 420, 600)); // Wyższe okno dla listy
    }

    // ==========================================
    // METODY POMOCNICZE (STYLE I UKŁAD)
    // ==========================================

    private VBox createBaseLayout(javafx.scene.Node... children) {
        VBox root = new VBox(14, children);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #f8fafc, #eef2ff);" +
                "-fx-font-family: 'Segoe UI', 'Inter', 'Arial';"
        );
        return root;
    }

    private TextField createStyledTextField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setMaxWidth(300);
        field.setPrefHeight(40);
        field.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #ccc; -fx-padding: 5 10 5 10;");
        return field;
    }

    private PasswordField createStyledPasswordField(String prompt) {
        PasswordField field = new PasswordField();
        field.setPromptText(prompt);
        field.setMaxWidth(300);
        field.setPrefHeight(40);
        field.setStyle("-fx-background-radius: 8; -fx-border-radius: 8; -fx-border-color: #ccc; -fx-padding: 5 10 5 10;");
        return field;
    }

    private Button createStyledButton(String text) {
        Button button = new Button(text);
        button.setPrefWidth(220);
        button.setPrefHeight(44);
        button.setStyle(
                "-fx-background-radius: 12;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: 700;" +
                "-fx-text-fill: white;" +
                "-fx-background-color: linear-gradient(to right, #2563eb, #7c3aed);" +
                "-fx-cursor: hand;"
        );
        button.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.25)));
        return button;
    }

    private void styleLinkButton(Button btn) {
        btn.setStyle(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #2563eb;" +
            "-fx-underline: true;" +
            "-fx-cursor: hand;"
        );
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
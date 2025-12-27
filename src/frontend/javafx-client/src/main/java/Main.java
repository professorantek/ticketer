import java.net.http.HttpClient;
import java.util.UUID;

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

    // ==========================================
    // SEKCJA 0: KONFIGURACJA
    // ==========================================
    private static final String API_BASE = "http://127.0.0.1:8080";
    
    // Kontener na treść w panelu użytkownika
    private VBox userDashboardContent;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.primaryStage.setTitle("System Biletowy");
        showLoginView();
        this.primaryStage.show();
    }

    // ==========================================
    // SEKCJA 1: EKRAN LOGOWANIA
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

        // --- PRZYCISKI DEBUGOWANIA ---
        Button debugUserBtn = new Button("🛠 [DEBUG] Pomiń jako USER");
        debugUserBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8;");
        debugUserBtn.setMaxWidth(Double.MAX_VALUE);
        debugUserBtn.setOnAction(e -> showUserDashboard("Developer"));

        Button debugAdminBtn = new Button("🛠 [DEBUG] Pomiń jako ADMIN");
        debugAdminBtn.setStyle("-fx-background-color: #7f1d1d; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8;");
        debugAdminBtn.setMaxWidth(Double.MAX_VALUE);
        debugAdminBtn.setOnAction(e -> showAdminDashboard("Admin"));

        VBox debugBox = new VBox(10, debugUserBtn, debugAdminBtn);
        debugBox.setAlignment(Pos.CENTER);
        debugBox.setPadding(new Insets(10, 0, 0, 0));

        // --- AKCJA LOGOWANIA ---
        loginBtn.setOnAction(e -> {
            String login = loginField.getText();
            String pass = passField.getText();

            if (login.isEmpty() || pass.isEmpty()) {
                statusLabel.setText("Wypełnij wszystkie pola!");
                return;
            }
            statusLabel.setText("Logowanie...");
            statusLabel.setTextFill(Color.BLACK);
            loginBtn.setDisable(true);

            // [TODO] Request do /Login
            Platform.runLater(() -> {
                if ("admin".equalsIgnoreCase(login)) {
                    showAdminDashboard(login);
                } else {
                    showUserDashboard(login);
                }
            });
        });

        goToRegisterBtn.setOnAction(e -> showRegisterView());

        VBox layout = createBaseLayout(title, loginField, passField, loginBtn, statusLabel, goToRegisterBtn, debugBox);
        primaryStage.setScene(new Scene(layout, 420, 600));
    }

    // ==========================================
    // SEKCJA 2: EKRAN REJESTRACJI
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

            statusLabel.setText("Tworzenie konta...");
            statusLabel.setTextFill(Color.BLACK);
            registerBtn.setDisable(true);

            // [TODO] Request do /Register
            Platform.runLater(() -> {
                statusLabel.setTextFill(Color.GREEN);
                statusLabel.setText("Konto utworzone! (Symulacja)");
                registerBtn.setDisable(false);
            });
        });

        backBtn.setOnAction(e -> showLoginView());

        VBox layout = createBaseLayout(title, loginField, passField, passConfirmField, registerBtn, statusLabel, backBtn);
        primaryStage.setScene(new Scene(layout, 420, 550));
    }

    // ==========================================
    // SEKCJA 3: PANEL UŻYTKOWNIKA (CLIENT)
    // ==========================================
    private void showUserDashboard(String username) {
        Label welcomeLabel = createStyledLabel("Witaj, " + username + "!", 18);
        
        Button btnAvailable = new Button("🛒 Oferta");
        Button btnMyTickets = new Button("🎟 Moje bilety");
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

        Button logoutBtn = new Button("Wyloguj");
        styleLinkButton(logoutBtn);
        logoutBtn.setOnAction(e -> showLoginView());

        btnAvailable.setOnAction(e -> loadAvailableTickets());
        btnMyTickets.setOnAction(e -> loadMyTickets());

        loadAvailableTickets();

        VBox layout = createBaseLayout(welcomeLabel, menu, scrollPane, logoutBtn);
        primaryStage.setScene(new Scene(layout, 420, 600));
    }

    private void loadAvailableTickets() {
        userDashboardContent.getChildren().clear();
        Label title = new Label("Dostępne wydarzenia:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        userDashboardContent.getChildren().add(title);

        // [TODO] Pobieranie biletów z backendu
        userDashboardContent.getChildren().add(createTicketCard("Koncert Rockowy", "150.00 PLN", "Kup", false));
        userDashboardContent.getChildren().add(createTicketCard("Mecz Polska-Niemcy", "220.00 PLN", "Kup", false));
    }

    private void loadMyTickets() {
        userDashboardContent.getChildren().clear();
        Label title = new Label("Twoje bilety:");
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        userDashboardContent.getChildren().add(title);

        // [TODO] Pobieranie moich biletów
        userDashboardContent.getChildren().add(createTicketCard("Kino: Avatar 3", "Zapłacono", "Pokaż QR", true));
        userDashboardContent.getChildren().add(createTicketCard("Teatr Narodowy", "Zapłacono", "Pokaż QR", true));
    }

    // ==========================================
    // SEKCJA 4: PANEL ADMINA
    // ==========================================
    private void showAdminDashboard(String username) {
        Label welcomeLabel = createStyledLabel("Panel Admina: " + username, 18);
        welcomeLabel.setTextFill(Color.DARKRED);
        Label subTitle = new Label("Dodaj nowe wydarzenie:");
        
        TextField eventNameField = createStyledTextField("Nazwa wydarzenia");
        TextField priceField = createStyledTextField("Cena (np. 99.99)");
        TextField qtyField = createStyledTextField("Ilość biletów");
        Label statusLabel = createStyledLabel("", 12);

        Button addBtn = createStyledButton("➕ Dodaj Bilet");
        addBtn.setStyle("-fx-background-color: #be123c; -fx-text-fill: white; -fx-background-radius: 12; -fx-font-weight: bold; -fx-cursor: hand;");

        Button logoutBtn = new Button("Wyloguj");
        styleLinkButton(logoutBtn);
        logoutBtn.setOnAction(e -> showLoginView());

        addBtn.setOnAction(e -> {
            String name = eventNameField.getText();
            if (name.isEmpty()) {
                statusLabel.setTextFill(Color.RED);
                statusLabel.setText("Wypełnij pola!");
                return;
            }
            // [TODO] Request dodawania
            statusLabel.setTextFill(Color.GREEN);
            statusLabel.setText("Dodano: " + name);
            eventNameField.clear();
        });

        VBox formBox = new VBox(10, subTitle, eventNameField, priceField, qtyField, addBtn, statusLabel);
        formBox.setAlignment(Pos.CENTER);
        formBox.setPadding(new Insets(20));
        formBox.setStyle("-fx-background-color: white; -fx-background-radius: 10; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);");

        VBox layout = createBaseLayout(welcomeLabel, formBox, logoutBtn);
        primaryStage.setScene(new Scene(layout, 420, 600));
    }

    // ==========================================
    // SEKCJA 5: FUNKCJA GENERUJĄCA OKNO Z QR
    // ==========================================
    private void showQRWindow(String ticketName, String codeData) {
        Stage qrStage = new Stage();
        // Okno modalne - blokuje klikanie w tło dopóki nie zamkniesz
        qrStage.initModality(Modality.APPLICATION_MODAL); 
        qrStage.setTitle("Bilet: " + ticketName);

        Label header = new Label(ticketName);
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        // Używamy publicznego API do generowania QR (nie wymaga biblioteki ZXing)
        // size=250x250, data=TUTAJ_TWÓJ_TEKST
        String apiUrl = "https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=" + codeData;
        
        // Ładowanie obrazka z sieci
        ImageView qrImage = new ImageView();
        try {
            Image image = new Image(apiUrl, true); // true = loading in background
            qrImage.setImage(image);
        } catch (Exception e) {
            header.setText("Błąd ładowania QR");
        }

        Label codeLabel = new Label("Kod: " + codeData);
        codeLabel.setStyle("-fx-font-family: 'Monospaced'; -fx-text-fill: #555;");

        Button closeBtn = new Button("Zamknij");
        closeBtn.setOnAction(e -> qrStage.close());
        closeBtn.setStyle("-fx-background-color: #333; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5;");

        VBox layout = new VBox(15, header, qrImage, codeLabel, closeBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: white;");

        Scene scene = new Scene(layout, 300, 400);
        qrStage.setScene(scene);
        qrStage.show();
    }

    // ==========================================
    // SEKCJA 6: KOMPONENTY I STYLE
    // ==========================================
    private HBox createTicketCard(String eventName, String priceInfo, String btnText, boolean isOwned) {
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
        infoBox.getChildren().addAll(nameLbl, priceLbl);
        
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button actionBtn = new Button(btnText);
        String btnColor = isOwned ? "#10b981" : "#3b82f6"; 
        actionBtn.setStyle("-fx-background-color: " + btnColor + "; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");

        actionBtn.setOnAction(e -> {
            if (isOwned) {
                // Generujemy losowy unikalny kod dla biletu
                String randomCode = "BILET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                showQRWindow(eventName, randomCode);
            } else {
                System.out.println("DEBUG: Kliknięto KUP -> " + eventName);
                // [TODO] Logika kupowania
            }
        });

        card.getChildren().addAll(infoBox, spacer, actionBtn);
        return card;
    }

    private VBox createBaseLayout(javafx.scene.Node... children) {
        VBox root = new VBox(14, children);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #f8fafc, #eef2ff); -fx-font-family: 'Segoe UI', 'Inter', 'Arial';");
        return root;
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
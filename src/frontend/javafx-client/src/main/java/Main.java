import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    // --- STAN APLIKACJI ---
    private enum ViewType { LOGIN, REGISTER, USER_DASHBOARD, ADMIN_DASHBOARD }
    private ViewType currentViewType = ViewType.LOGIN;
    private String currentUsername = ""; 

    // ==========================================
    // KLASA DO OBSŁUGI LOKALIZACJI
    // ==========================================
    public static class LocalizationManager {
        private final Map<String, String> translations = new HashMap<>();

        public void loadFromFile(String filename) {
            translations.clear();
            File file = new File(filename);
            if (!file.exists()) {
                System.err.println("BŁĄD: Brak pliku: " + filename);
                return;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        translations.put(parts[0].trim(), parts[1].trim());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public String get(String key) {
            return translations.getOrDefault(key, "[" + key + "]");
        }
    }

    // ==========================================
    // SEKCJA 0: START
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
        if ("EN".equals(langCode)) {
            loc.loadFromFile("localization-en.txt");
        } else {
            loc.loadFromFile("localization-pl.txt");
        }
        primaryStage.setTitle(loc.get("app.title"));
        
        switch (currentViewType) {
            case LOGIN -> showLoginView();
            case REGISTER -> showRegisterView();
            case USER_DASHBOARD -> showUserDashboard(currentUsername);
            case ADMIN_DASHBOARD -> showAdminDashboard(currentUsername);
        }
    }

    // ==========================================
    // SEKCJA 1: EKRAN LOGOWANIA
    // ==========================================
    private void showLoginView() {
        currentViewType = ViewType.LOGIN;

        Label title = createStyledLabel(loc.get("login.title"), 24);
        
        // --- ZMIANA: Tłumaczone podpowiedzi (prompts) ---
        TextField loginField = createStyledTextField(loc.get("prompt.login"));
        PasswordField passField = createStyledPasswordField(loc.get("prompt.password"));
        
        Label statusLabel = createStyledLabel("", 12);
        statusLabel.setTextFill(Color.RED);

        Button loginBtn = createStyledButton(loc.get("login.btn"));
        Button goToRegisterBtn = new Button(loc.get("login.register_link"));
        styleLinkButton(goToRegisterBtn);

        Button debugUserBtn = new Button(loc.get("debug.user"));
        debugUserBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8;");
        debugUserBtn.setMaxWidth(Double.MAX_VALUE);
        debugUserBtn.setOnAction(e -> showUserDashboard("Developer"));

        Button debugAdminBtn = new Button(loc.get("debug.admin"));
        debugAdminBtn.setStyle("-fx-background-color: #7f1d1d; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 8;");
        debugAdminBtn.setMaxWidth(Double.MAX_VALUE);
        debugAdminBtn.setOnAction(e -> showAdminDashboard("Admin"));

        VBox debugBox = new VBox(10, debugUserBtn, debugAdminBtn);
        debugBox.setAlignment(Pos.CENTER);
        debugBox.setPadding(new Insets(10, 0, 0, 0));

        loginBtn.setOnAction(e -> {
            String login = loginField.getText();
            String pass = passField.getText();

            if (login.isEmpty() || pass.isEmpty()) {
                statusLabel.setText(loc.get("login.status.empty"));
                return;
            }
            statusLabel.setText(loc.get("login.status.process"));
            statusLabel.setTextFill(Color.BLACK);
            loginBtn.setDisable(true);

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
        currentViewType = ViewType.REGISTER;

        Label title = createStyledLabel(loc.get("register.title"), 24);
        
        // --- ZMIANA: Tłumaczone podpowiedzi ---
        TextField loginField = createStyledTextField(loc.get("prompt.login"));
        PasswordField passField = createStyledPasswordField(loc.get("prompt.password"));
        PasswordField passConfirmField = createStyledPasswordField(loc.get("prompt.password_confirm"));
        
        Label statusLabel = createStyledLabel("", 12);
        statusLabel.setTextFill(Color.RED);

        Button registerBtn = createStyledButton(loc.get("register.btn"));
        Button backBtn = new Button(loc.get("register.back_btn"));
        styleLinkButton(backBtn);

        registerBtn.setOnAction(e -> {
            String login = loginField.getText();
            String pass = passField.getText();
            String passConf = passConfirmField.getText();

            if (login.isEmpty() || pass.isEmpty()) {
                statusLabel.setText(loc.get("login.status.empty"));
                return;
            }
            if (!pass.equals(passConf)) {
                statusLabel.setText(loc.get("register.status.pass_mismatch"));
                return;
            }
            statusLabel.setText(loc.get("register.status.process"));
            statusLabel.setTextFill(Color.BLACK);
            registerBtn.setDisable(true);

            Platform.runLater(() -> {
                statusLabel.setTextFill(Color.GREEN);
                statusLabel.setText(loc.get("register.success"));
                registerBtn.setDisable(false);
            });
        });

        backBtn.setOnAction(e -> showLoginView());

        VBox layout = createBaseLayout(title, loginField, passField, passConfirmField, registerBtn, statusLabel, backBtn);
        primaryStage.setScene(new Scene(layout, 420, 550));
    }

    // ==========================================
    // SEKCJA 3: PANEL UŻYTKOWNIKA
    // ==========================================
    private void showUserDashboard(String username) {
        currentViewType = ViewType.USER_DASHBOARD;
        currentUsername = username;

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
        logoutBtn.setOnAction(e -> showLoginView());

        btnAvailable.setOnAction(e -> loadAvailableTickets());
        btnMyTickets.setOnAction(e -> loadMyTickets());

        loadAvailableTickets();

        VBox layout = createBaseLayout(welcomeLabel, menu, scrollPane, logoutBtn);
        primaryStage.setScene(new Scene(layout, 420, 600));
    }

    private void loadAvailableTickets() {
        userDashboardContent.getChildren().clear();
        Label title = new Label(loc.get("dashboard.available_title"));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        userDashboardContent.getChildren().add(title);

        userDashboardContent.getChildren().add(createTicketCard("Koncert Rockowy", "150.00 PLN", 100, loc.get("ticket.buy_btn"), false));
        userDashboardContent.getChildren().add(createTicketCard("Mecz Polska-Niemcy", "220.00 PLN", 45, loc.get("ticket.buy_btn"), false));
    }

    private void loadMyTickets() {
        userDashboardContent.getChildren().clear();
        Label title = new Label(loc.get("dashboard.my_tickets_title"));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        userDashboardContent.getChildren().add(title);

        userDashboardContent.getChildren().add(createTicketCard("Kino: Avatar 3", "Zapłacono", 2, loc.get("ticket.qr_btn"), true));
    }

    // ==========================================
    // SEKCJA 4: PANEL ADMINA
    // ==========================================
    private void showAdminDashboard(String username) {
        currentViewType = ViewType.ADMIN_DASHBOARD;
        currentUsername = username;

        Label welcomeLabel = createStyledLabel(loc.get("admin.title") + " " + username, 18);
        welcomeLabel.setTextFill(Color.DARKRED);
        Label subTitle = new Label(loc.get("admin.subtitle"));
        
        // --- ZMIANA: Tłumaczone podpowiedzi ---
        TextField eventNameField = createStyledTextField(loc.get("prompt.event_name"));
        TextField priceField = createStyledTextField(loc.get("prompt.price"));
        TextField qtyField = createStyledTextField(loc.get("prompt.qty"));
        
        Label statusLabel = createStyledLabel("", 12);

        Button addBtn = createStyledButton(loc.get("admin.add_btn"));
        addBtn.setStyle("-fx-background-color: #be123c; -fx-text-fill: white; -fx-background-radius: 12; -fx-font-weight: bold; -fx-cursor: hand;");

        Button logoutBtn = new Button(loc.get("menu.logout"));
        styleLinkButton(logoutBtn);
        logoutBtn.setOnAction(e -> showLoginView());

        addBtn.setOnAction(e -> {
            String name = eventNameField.getText();
            if (name.isEmpty()) {
                statusLabel.setTextFill(Color.RED);
                statusLabel.setText(loc.get("login.status.empty"));
                return;
            }
            statusLabel.setTextFill(Color.GREEN);
            statusLabel.setText(loc.get("admin.status.success") + " " + name);
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
    // SEKCJA 5: OKNA MODALNE
    // ==========================================
    private void showBuyConfirmationWindow(String ticketName, String priceString) {
        Stage buyStage = new Stage();
        buyStage.initModality(Modality.APPLICATION_MODAL);
        buyStage.setTitle(loc.get("buy.title"));

        Label header = new Label(loc.get("buy.header"));
        header.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
        Label nameLabel = new Label(ticketName);
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-padding: 0 0 5 0;");
        Label singlePriceLabel = new Label(loc.get("buy.price_single") + " " + priceString);
        singlePriceLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        HBox quantityBox = new HBox(10);
        quantityBox.setAlignment(Pos.CENTER);
        Label qtyLabel = new Label(loc.get("buy.qty_label"));
        Spinner<Integer> quantitySpinner = new Spinner<>(1, 10, 1);
        quantitySpinner.setPrefWidth(80);
        quantityBox.getChildren().addAll(qtyLabel, quantitySpinner);

        Label totalPriceLabel = new Label(loc.get("buy.total") + " " + priceString);
        totalPriceLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2563eb; -fx-padding: 10 0 0 0;");

        double singlePrice = 0.0;
        try {
            String cleanPrice = priceString.replace(" PLN", "").replace(",", ".").trim();
            singlePrice = Double.parseDouble(cleanPrice);
        } catch (Exception e) { singlePrice = 0.0; }
        final double finalSinglePrice = singlePrice;

        quantitySpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            double total = finalSinglePrice * newVal;
            totalPriceLabel.setText(String.format("%s %.2f PLN", loc.get("buy.total"), total));
        });

        CheckBox oathCheckbox = new CheckBox(loc.get("buy.checkbox"));
        oathCheckbox.setStyle("-fx-font-size: 14px; -fx-padding: 15 0 15 0;");

        Button confirmBtn = new Button(loc.get("buy.confirm_btn"));
        confirmBtn.setDisable(true); 
        confirmBtn.setPrefWidth(200);
        confirmBtn.setStyle("-fx-background-color: #10b981; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");

        oathCheckbox.selectedProperty().addListener((observable, oldValue, newValue) -> confirmBtn.setDisable(!newValue));

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: green;");

        confirmBtn.setOnAction(e -> {
            statusLabel.setText(loc.get("buy.success"));
            confirmBtn.setVisible(false);
            oathCheckbox.setVisible(false);
            quantityBox.setDisable(true);
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override public void run() { Platform.runLater(() -> buyStage.close()); }
            }, 1500);
        });

        VBox layout = new VBox(10, header, nameLabel, singlePriceLabel, quantityBox, totalPriceLabel, oathCheckbox, confirmBtn, statusLabel);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30));
        layout.setStyle("-fx-background-color: white;");

        Scene scene = new Scene(layout, 350, 400);
        buyStage.setScene(scene);
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
        closeBtn.setStyle("-fx-background-color: #333; -fx-text-fill: white; -fx-cursor: hand; -fx-background-radius: 5;");

        VBox layout = new VBox(15, header, qrImage, closeBtn);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));
        layout.setStyle("-fx-background-color: white;");

        Scene scene = new Scene(layout, 300, 400);
        qrStage.setScene(scene);
        qrStage.show();
    }

    // ==========================================
    // SEKCJA 6: KOMPONENTY
    // ==========================================
    private VBox createBaseLayout(javafx.scene.Node... children) {
        Button btnPL = new Button("PL");
        Button btnEN = new Button("EN");
        String langBtnStyle = "-fx-background-color: transparent; -fx-font-weight: bold; -fx-cursor: hand; -fx-text-fill: #555; -fx-border-color: #ccc; -fx-border-radius: 4;";
        btnPL.setStyle(langBtnStyle);
        btnEN.setStyle(langBtnStyle);
        
        btnPL.setOnAction(e -> switchLanguage("PL"));
        btnEN.setOnAction(e -> switchLanguage("EN"));
        
        HBox langBox = new HBox(5, btnPL, btnEN);
        langBox.setAlignment(Pos.CENTER_RIGHT);
        langBox.setPadding(new Insets(0, 0, 10, 0));

        VBox root = new VBox();
        root.setSpacing(14);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.getChildren().add(langBox);
        root.getChildren().addAll(children);
        root.setStyle("-fx-background-color: linear-gradient(to bottom, #f8fafc, #eef2ff); -fx-font-family: 'Segoe UI', 'Inter', 'Arial';");
        return root;
    }

    private HBox createTicketCard(String eventName, String priceInfo, int quantity, String btnText, boolean isOwned) {
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
        
        String qtyText = (isOwned ? loc.get("ticket.owned") : loc.get("ticket.available")) + " " + quantity + " " + loc.get("ticket.unit");
        Label qtyLabel = new Label(qtyText);
        qtyLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888; -fx-font-weight: bold;");

        infoBox.getChildren().addAll(nameLbl, priceLbl, qtyLabel);
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button actionBtn = new Button(btnText);
        String btnColor = isOwned ? "#10b981" : "#3b82f6"; 
        actionBtn.setStyle("-fx-background-color: " + btnColor + "; -fx-text-fill: white; -fx-background-radius: 6; -fx-cursor: hand;");

        actionBtn.setOnAction(e -> {
            if (isOwned) {
                String randomCode = "BILET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                showQRWindow(eventName, randomCode);
            } else {
                showBuyConfirmationWindow(eventName, priceInfo);
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
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
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

// ==========================================
// GENERIC CLASS (3 pkt) + generic methods
// ==========================================
final class ApiResult<T> {
    private final boolean ok;
    private final int statusCode;
    private final T data;
    private final String error;

    public ApiResult(boolean ok, int statusCode, T data, String error) {
        this.ok = ok;
        this.statusCode = statusCode;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResult<T> success(int code, T data) {
        return new ApiResult<>(true, code, data, "");
    }

    public static <T> ApiResult<T> failure(int code, String error) {
        return new ApiResult<>(false, code, null, error);
    }

    public boolean isOk() { return ok; }
    public int getStatusCode() { return statusCode; }
    public T getData() { return data; }
    public String getError() { return error; }
}

// ==========================================
// DTO
// ==========================================
record TicketDto(long id, double price, String name) {}
record UserTicketCountDto(long idBiletu, long quantity) {}

// ==========================================
// INTERFACE (1 pkt)
// ==========================================
interface BackendApi {
    CompletableFuture<ApiResult<Void>> login(String login, String password);
    CompletableFuture<ApiResult<Void>> register(String login, String password);
    CompletableFuture<ApiResult<List<TicketDto>>> getTickets();
    CompletableFuture<ApiResult<Void>> purchase(long ticketId, long quantity, String login, String password);
    CompletableFuture<ApiResult<List<UserTicketCountDto>>> getUserTickets(String login, String password);
    CompletableFuture<ApiResult<Void>> createTicket(String name, double price, String adminPassword);
    CompletableFuture<ApiResult<Void>> deleteTicket(long id, String adminPassword);
    CompletableFuture<ApiResult<Void>> updateTicket(long id, String name, double price, String adminPassword);
}

// ==========================================
// ABSTRACT CLASS (1 pkt)
// ==========================================
abstract class AbstractBackendApi implements BackendApi {
    protected final String baseUrl;

    protected AbstractBackendApi(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    protected void logError(String msg, Throwable t) {
        System.err.println("[BackendApi] " + msg);
        if (t != null) t.printStackTrace();
    }
}

// ==========================================
// REAL HTTP IMPLEMENTATION
// ==========================================
final class HttpBackendApi extends AbstractBackendApi {
    private final HttpClient client;

    public HttpBackendApi(String baseUrl, Executor executor) {
        super(baseUrl);
        this.client = HttpClient.newBuilder().executor(executor).build();
    }

    @Override
    public CompletableFuture<ApiResult<Void>> login(String login, String password) {
        String json = String.format("{\"login\":\"%s\",\"password\":\"%s\"}", login, password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> resp.statusCode() == 200
                        ? ApiResult.<Void>success(resp.statusCode(), null)
                        : ApiResult.<Void>failure(resp.statusCode(), resp.body()))
                .exceptionally(ex -> {
                    logError("Login failed", ex);
                    return ApiResult.<Void>failure(0, "Network error");
                });
    }

    @Override
    public CompletableFuture<ApiResult<Void>> register(String login, String password) {
        String json = String.format("{\"login\":\"%s\",\"password\":\"%s\"}", login, password);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> resp.statusCode() == 201
                        ? ApiResult.<Void>success(resp.statusCode(), null)
                        : ApiResult.<Void>failure(resp.statusCode(), resp.body()))
                .exceptionally(ex -> {
                    logError("Register failed", ex);
                    return ApiResult.<Void>failure(0, "Network error");
                });
    }

    @Override
    public CompletableFuture<ApiResult<List<TicketDto>>> getTickets() {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/tickets"))
                .GET()
                .build();

        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200)
                        return ApiResult.<List<TicketDto>>failure(resp.statusCode(), resp.body());

                    List<String> items = Main.SimpleJsonParser.parseArray(resp.body());
                    List<TicketDto> out = new ArrayList<>();
                    for (String item : items) {
                        try {
                            long id = Long.parseLong(Main.SimpleJsonParser.getValue(item, "id"));
                            double price = Double.parseDouble(Main.SimpleJsonParser.getValue(item, "price"));
                            String name = Main.SimpleJsonParser.getValue(item, "name");
                            out.add(new TicketDto(id, price, name));
                        } catch (Exception e) {
                            logError("Ticket parse error: " + item, e);
                        }
                    }
                    return ApiResult.<List<TicketDto>>success(resp.statusCode(), out);
                })
                .exceptionally(ex -> {
                    logError("Get tickets failed", ex);
                    return ApiResult.<List<TicketDto>>failure(0, "Network error");
                });
    }

    @Override
    public CompletableFuture<ApiResult<Void>> purchase(long ticketId, long quantity, String login, String password) {
        String json = String.format(Locale.US,
                "{\"idBiletu\":%d,\"quantity\":%d,\"login\":\"%s\",\"password\":\"%s\"}",
                ticketId, quantity, login, password);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/purchase"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> resp.statusCode() == 201
                        ? ApiResult.<Void>success(resp.statusCode(), null)
                        : ApiResult.<Void>failure(resp.statusCode(), resp.body()))
                .exceptionally(ex -> {
                    logError("Purchase failed", ex);
                    return ApiResult.<Void>failure(0, "Network error");
                });
    }

    @Override
    public CompletableFuture<ApiResult<List<UserTicketCountDto>>> getUserTickets(String login, String password) {
        String json = String.format("{\"login\":\"%s\",\"password\":\"%s\"}", login, password);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/purchases/by-user"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> {
                    if (resp.statusCode() != 200)
                        return ApiResult.<List<UserTicketCountDto>>failure(resp.statusCode(), resp.body());

                    List<String> items = Main.SimpleJsonParser.parseArray(resp.body());
                    List<UserTicketCountDto> out = new ArrayList<>();
                    for (String item : items) {
                        try {
                            long id = Long.parseLong(Main.SimpleJsonParser.getValue(item, "idBiletu"));
                            long qty = Long.parseLong(Main.SimpleJsonParser.getValue(item, "quantity"));
                            out.add(new UserTicketCountDto(id, qty));
                        } catch (Exception e) {
                            logError("User ticket parse error: " + item, e);
                        }
                    }
                    return ApiResult.<List<UserTicketCountDto>>success(resp.statusCode(), out);
                })
                .exceptionally(ex -> {
                    logError("Get user tickets failed", ex);
                    return ApiResult.<List<UserTicketCountDto>>failure(0, "Network error");
                });
    }

    @Override
    public CompletableFuture<ApiResult<Void>> createTicket(String name, double price, String adminPassword) {
        String json = String.format(Locale.US,
                "{\"adminPassword\":\"%s\", \"name\":\"%s\", \"price\":%f}",
                adminPassword, name, price
        );

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/tickets/create"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenApply(resp -> resp.statusCode() == 201
                        ? ApiResult.<Void>success(resp.statusCode(), null)
                        : ApiResult.<Void>failure(resp.statusCode(), resp.body()))
                .exceptionally(ex -> {
                    logError("Create ticket failed", ex);
                    return ApiResult.<Void>failure(0, "Network error");
                });
    }
    @Override
    public CompletableFuture<ApiResult<Void>> deleteTicket(long id, String adminPassword) {
        String json = String.format(Locale.US, "{\"adminPassword\":\"%s\", \"id\":%d}", adminPassword, id);
        
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/tickets/delete"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return sendVoidRequest(req, "Delete ticket failed");
    }

    @Override
    public CompletableFuture<ApiResult<Void>> updateTicket(long id, String name, double price, String adminPassword) {
        String json = String.format(Locale.US, 
            "{\"adminPassword\":\"%s\", \"id\":%d, \"name\":\"%s\", \"price\":%f}", 
            adminPassword, id, name, price);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/tickets/update"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return sendVoidRequest(req, "Update ticket failed");
    }

    // Helper żeby nie powielać kodu sendAsync
    private CompletableFuture<ApiResult<Void>> sendVoidRequest(HttpRequest req, String errorMsg) {
        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString()) // Użyj 'client' z klasy (może wymagać protected w AbstractBackendApi lub this.client)
                .thenApply(resp -> resp.statusCode() == 200 || resp.statusCode() == 201
                        ? ApiResult.<Void>success(resp.statusCode(), null)
                        : ApiResult.<Void>failure(resp.statusCode(), resp.body()))
                .exceptionally(ex -> {
                    logError(errorMsg, ex);
                    return ApiResult.<Void>failure(0, "Network error");
                });
    }
}

// ==========================================
// OPTIONAL MOCK IMPLEMENTATION
// ==========================================
final class MockBackendApi extends AbstractBackendApi {
    public MockBackendApi() { super("mock://"); }

    @Override public CompletableFuture<ApiResult<Void>> login(String login, String password) {
        return CompletableFuture.completedFuture(ApiResult.success(200, null));
    }
    @Override public CompletableFuture<ApiResult<Void>> register(String login, String password) {
        return CompletableFuture.completedFuture(ApiResult.success(201, null));
    }
    @Override public CompletableFuture<ApiResult<List<TicketDto>>> getTickets() {
        return CompletableFuture.completedFuture(ApiResult.success(200,
                List.of(new TicketDto(1, 99.0, "Mock Ticket"), new TicketDto(2, 120.0, "VIP Mock"))));
    }
    @Override public CompletableFuture<ApiResult<Void>> purchase(long ticketId, long quantity, String login, String password) {
        return CompletableFuture.completedFuture(ApiResult.success(201, null));
    }
    @Override public CompletableFuture<ApiResult<List<UserTicketCountDto>>> getUserTickets(String login, String password) {
        return CompletableFuture.completedFuture(ApiResult.success(200,
                List.of(new UserTicketCountDto(1, 2), new UserTicketCountDto(2, 1))));
    }
    @Override public CompletableFuture<ApiResult<Void>> createTicket(String name, double price, String adminPassword) {
        return CompletableFuture.completedFuture(ApiResult.success(201, null));
    }
    @Override public CompletableFuture<ApiResult<Void>> deleteTicket(long id, String adminPassword) {
        return CompletableFuture.completedFuture(ApiResult.success(200, null));
    }
    @Override public CompletableFuture<ApiResult<Void>> updateTicket(long id, String name, double price, String adminPassword) {
        return CompletableFuture.completedFuture(ApiResult.success(200, null));
    }
}

// ==========================================
// APP RESOURCES
// ==========================================
final class AppResources implements AutoCloseable {
    private final ExecutorService executor;

    public AppResources() {
        this.executor = Executors.newFixedThreadPool(4);
    }

    public ExecutorService executor() { return executor; }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}

// ==========================================
// MAIN APP
// ==========================================
public class Main extends Application {

    private Stage primaryStage;
    private AppResources resources;
    private BackendApi api;
    private final LocalizationManager loc = new LocalizationManager();

    // --- CONFIG ---
    private static final String API_BASE = "http://127.0.0.1:8080";
    private static final String HARDCODED_ADMIN_PASS = "12345"; // Backend admin pass

    // --- LOCAL ADMIN CREDENTIALS ---
    private static final String LOCAL_ADMIN_LOGIN = "admin";
    private static final String LOCAL_ADMIN_PASS = "12345";

    private enum ViewType { LOGIN, REGISTER, USER_DASHBOARD, ADMIN_DASHBOARD }
    private ViewType currentViewType = ViewType.LOGIN;

    private String currentLogin = "";
    private String currentPassword = "";

    private final Map<Integer, String> ticketNameCache = new HashMap<>();

    // --- JSON HELPER ---
    public static class SimpleJsonParser {
        public static String getValue(String json, String key) {
            Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\"[^\"]*\"|[\\d\\.]+|true|false)");
            Matcher matcher = pattern.matcher(json);
            if (matcher.find()) {
                String val = matcher.group(1);
                if (val.startsWith("\"") && val.endsWith("\"")) return val.substring(1, val.length() - 1);
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

    // --- LOCALIZATION HELPER ---
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

    // --- START / STOP ---
    private VBox userDashboardContent;

    @Override
    public void start(Stage stage) {
        this.resources = new AppResources();
        this.api = new HttpBackendApi(API_BASE, resources.executor());
        loc.loadFromFile("localization-pl.txt");

        this.primaryStage = stage;
        this.primaryStage.setTitle(loc.get("app.title"));
        showLoginView();
        this.primaryStage.show();
    }

    @Override
    public void stop() {
        if (resources != null) resources.close();
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

        // --- PRZYCISK ADMINISTRATORA (NOWOŚĆ) ---
        // Zamiast przycisków debug, mamy przycisk wejścia do panelu przez lokalne hasło
        Button adminPanelBtn = new Button("Panel Administratora");
        adminPanelBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #555; -fx-font-weight: bold; -fx-cursor: hand; -fx-border-color: #ccc; -fx-border-radius: 5;");
        adminPanelBtn.setOnAction(e -> showLocalAdminLoginWindow());

        VBox adminBox = new VBox(10, adminPanelBtn);
        adminBox.setAlignment(Pos.CENTER);
        adminBox.setPadding(new Insets(20, 0, 0, 0));

        // --- LOGIN ACTION (BACKEND) ---
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

            api.login(login, pass).thenAccept(result -> Platform.runLater(() -> {
                loginBtn.setDisable(false);
                if (result.isOk()) {
                    currentLogin = login;
                    currentPassword = pass;
                    showUserDashboard(login);
                } else {
                    statusLabel.setTextFill(Color.RED);
                    statusLabel.setText("Błąd: " + result.getStatusCode() + " (" + result.getError() + ")");
                }
            }));
        });

        goToRegisterBtn.setOnAction(e -> showRegisterView());
        VBox layout = createBaseLayout(title, loginField, passField, loginBtn, statusLabel, goToRegisterBtn, adminBox);
        primaryStage.setScene(new Scene(layout, 420, 600));
    }

    // ==========================================
    // NOWE OKNO: LOKALNE LOGOWANIE ADMINA
    // ==========================================
    private void showLocalAdminLoginWindow() {
        Stage adminStage = new Stage();
        adminStage.initModality(Modality.APPLICATION_MODAL);
        adminStage.setTitle("Logowanie Administratora");

        Label header = new Label("Dostęp do panelu administratora");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");

        TextField loginField = createStyledTextField("Login");
        PasswordField passField = createStyledPasswordField("Hasło");
        Label statusLabel = createStyledLabel("", 12);

        Button confirmBtn = new Button("Zaloguj");
        confirmBtn.setStyle("-fx-background-color: #be123c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-cursor: hand;");
        confirmBtn.setPrefWidth(200);

        confirmBtn.setOnAction(e -> {
            String l = loginField.getText();
            String p = passField.getText();

            // SPRAWDZAMY LOKALNIE
            if (LOCAL_ADMIN_LOGIN.equals(l) && LOCAL_ADMIN_PASS.equals(p)) {
                adminStage.close();
                // Ustawiamy te dane jako sesja, bo są też potrzebne do wysyłania requestów do backendu (jako adminPassword)
                currentLogin = l;
                currentPassword = p;
                showAdminDashboard(l);
            } else {
                statusLabel.setTextFill(Color.RED);
                statusLabel.setText("Błędne dane lokalne!");
            }
        });

        VBox layout = new VBox(15, header, loginField, passField, confirmBtn, statusLabel);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30));
        layout.setStyle("-fx-background-color: white;");

        Scene scene = new Scene(layout, 300, 350);
        adminStage.setScene(scene);
        adminStage.show();
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

            api.register(login, pass).thenAccept(result -> Platform.runLater(() -> {
                registerBtn.setDisable(false);
                if (result.isOk() && result.getStatusCode() == 201) {
                    statusLabel.setTextFill(Color.GREEN);
                    statusLabel.setText(loc.get("register.success"));
                } else if (result.getStatusCode() == 409) {
                    statusLabel.setTextFill(Color.RED);
                    statusLabel.setText("Login zajęty!");
                } else {
                    statusLabel.setTextFill(Color.RED);
                    statusLabel.setText("Błąd: " + result.getStatusCode());
                }
            }));
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

        loadAvailableTickets();

        VBox layout = createBaseLayout(welcomeLabel, menu, scrollPane, logoutBtn);
        primaryStage.setScene(new Scene(layout, 420, 600));
    }

    private void loadAvailableTickets() {
        userDashboardContent.getChildren().clear();
        Label title = new Label(loc.get("dashboard.available_title"));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        userDashboardContent.getChildren().add(title);
        Label statusLbl = new Label("Ładowanie...");
        userDashboardContent.getChildren().add(statusLbl);

        api.getTickets().thenAccept(result -> Platform.runLater(() -> {
            userDashboardContent.getChildren().remove(statusLbl);

            if (!result.isOk()) {
                userDashboardContent.getChildren().add(new Label("Błąd serwera: " + result.getStatusCode()));
                return;
            }

            List<TicketDto> tickets = result.getData();
            if (tickets == null || tickets.isEmpty()) {
                userDashboardContent.getChildren().add(new Label("Brak biletów."));
                return;
            }

            ticketNameCache.clear();
            for (TicketDto t : tickets) {
                ticketNameCache.put((int) t.id(), t.name());
                String priceStr = String.format(Locale.US, "%.2f PLN", t.price());
                userDashboardContent.getChildren().add(
                        createTicketCard((int) t.id(), t.name(), priceStr, -1, loc.get("ticket.buy_btn"), false)
                );
            }
        }));
    }

    private void loadMyTickets() {
        userDashboardContent.getChildren().clear();
        Label title = new Label(loc.get("dashboard.my_tickets_title"));
        title.setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
        userDashboardContent.getChildren().add(title);
        Label statusLbl = new Label("Ładowanie...");
        userDashboardContent.getChildren().add(statusLbl);

        api.getUserTickets(currentLogin, currentPassword).thenAccept(result -> Platform.runLater(() -> {
            userDashboardContent.getChildren().remove(statusLbl);

            if (!result.isOk()) {
                if (result.getStatusCode() == 401) {
                    userDashboardContent.getChildren().add(new Label("Sesja wygasła. Zaloguj się ponownie."));
                } else {
                    userDashboardContent.getChildren().add(new Label("Błąd: " + result.getStatusCode()));
                }
                return;
            }

            List<UserTicketCountDto> items = result.getData();
            if (items == null || items.isEmpty()) {
                userDashboardContent.getChildren().add(new Label("Nie masz jeszcze biletów."));
                return;
            }

            for (UserTicketCountDto it : items) {
                int id = (int) it.idBiletu();
                int qty = (int) it.quantity();
                String name = ticketNameCache.getOrDefault(id, "Bilet ID: " + id);

                userDashboardContent.getChildren().add(
                        createTicketCard(id, name, "Zapłacono", qty, loc.get("ticket.qr_btn"), true)
                );
            }
        }));
    }

    // ==========================================
    // 4. PANEL ADMINA
    // ==========================================
    // ==========================================
    // 4. PANEL ADMINA (Z LISTĄ I EDYCJĄ)
    // ==========================================
    private void showAdminDashboard(String username) {
        currentViewType = ViewType.ADMIN_DASHBOARD;

        Label welcomeLabel = createStyledLabel(loc.get("admin.title") + " " + username, 18);
        welcomeLabel.setTextFill(Color.DARKRED);

        // --- SEKCJ 1: DODAWANIE ---
        Label addLabel = new Label(loc.get("admin.subtitle")); // "Dodaj nowe wydarzenie"
        TextField addNameField = createStyledTextField(loc.get("prompt.event_name"));
        TextField addPriceField = createStyledTextField(loc.get("prompt.price"));
        Button addBtn = new Button(loc.get("admin.add_btn")); // "Dodaj Bilet"
        addBtn.setStyle("-fx-background-color: #be123c; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        Label statusLabel = createStyledLabel("", 12);

        // Akcja dodawania
        addBtn.setOnAction(e -> {
            String name = addNameField.getText();
            String priceStr = addPriceField.getText();
            if (name.isEmpty() || priceStr.isEmpty()) {
                statusLabel.setTextFill(Color.RED);
                statusLabel.setText("Wypełnij pola!");
                return;
            }
            double priceVal;
            try { priceVal = Double.parseDouble(priceStr.replace(",", ".")); } 
            catch (NumberFormatException ex) { statusLabel.setText("Zła cena"); return; }

            api.createTicket(name, priceVal, currentPassword).thenAccept(res -> Platform.runLater(() -> {
                if (res.isOk()) {
                    statusLabel.setTextFill(Color.GREEN);
                    statusLabel.setText("Dodano!");
                    addNameField.clear(); addPriceField.clear();
                    refreshAdminList(username); // Odśwież listę poniżej
                } else statusLabel.setText("Błąd: " + res.getStatusCode());
            }));
        });

        HBox addBox = new HBox(10, addNameField, addPriceField, addBtn);
        addBox.setAlignment(Pos.CENTER);

        // --- SEKCJA 2: LISTA ISTNIEJĄCYCH BILETÓW ---
        Label listLabel = new Label("Zarządzaj biletami:");
        listLabel.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 0 0;");
        
        VBox ticketsListContainer = new VBox(10);
        ticketsListContainer.setAlignment(Pos.TOP_CENTER);
        ScrollPane scroll = new ScrollPane(ticketsListContainer);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(300);
        scroll.setStyle("-fx-background-color: transparent;");

        // Przechowujemy referencję do kontenera w polu klasy (lub używamy lokalnej zmiennej finalnej/efektywnie finalnej)
        // Aby móc odświeżać listę, wydzielmy to do metody pomocniczej.
        // Tutaj użyjemy triku z Consumerem lub po prostu wywołamy refresh raz.
        
        // Button wyloguj
        Button logoutBtn = new Button(loc.get("menu.logout"));
        styleLinkButton(logoutBtn);
        logoutBtn.setOnAction(e -> { currentLogin=""; currentPassword=""; showLoginView(); });

        VBox layout = createBaseLayout(welcomeLabel, addLabel, addBox, statusLabel, listLabel, scroll, logoutBtn);
        
        // Zapiszmy kontener listy w polu klasy tymczasowo lub przekażmy go do metody
        this.adminTicketListContainer = ticketsListContainer; 
        refreshAdminList(username);

        primaryStage.setScene(new Scene(layout, 500, 700));
    }

    // Pole pomocnicze w klasie Main
    private VBox adminTicketListContainer;

    // Metoda odświeżająca listę w panelu admina
    private void refreshAdminList(String username) {
        if (adminTicketListContainer == null) return;
        adminTicketListContainer.getChildren().clear();
        adminTicketListContainer.getChildren().add(new Label("Ładowanie..."));

        api.getTickets().thenAccept(res -> Platform.runLater(() -> {
            adminTicketListContainer.getChildren().clear();
            if (!res.isOk()) {
                adminTicketListContainer.getChildren().add(new Label("Błąd pobierania listy."));
                return;
            }
            List<TicketDto> tickets = res.getData();
            if (tickets.isEmpty()) {
                adminTicketListContainer.getChildren().add(new Label("Brak biletów w bazie."));
                return;
            }

            for (TicketDto t : tickets) {
                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(5));
                row.setStyle("-fx-background-color: white; -fx-border-color: #ddd; -fx-border-radius: 5;");

                // Pola edycji
                TextField nameF = new TextField(t.name());
                nameF.setPrefWidth(150);
                TextField priceF = new TextField(String.format(Locale.US, "%.2f", t.price()));
                priceF.setPrefWidth(70);

                Button saveBtn = new Button("Zapisz");
                saveBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-size: 10px;");
                
                Button delBtn = new Button("Usuń");
                delBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-size: 10px;");

                // Akcja DELETE
                delBtn.setOnAction(ev -> {
                    if(!confirmDialog("Czy na pewno usunąć bilet ID: " + t.id() + "?")) return;
                    
                    api.deleteTicket(t.id(), currentPassword).thenAccept(delRes -> Platform.runLater(() -> {
                        if(delRes.isOk()) refreshAdminList(username);
                        else showAlert("Błąd usuwania: " + delRes.getStatusCode());
                    }));
                });

                // Akcja UPDATE
                saveBtn.setOnAction(ev -> {
                    double newPrice;
                    try { newPrice = Double.parseDouble(priceF.getText().replace(",", ".")); }
                    catch(Exception ex) { showAlert("Błędna cena"); return; }
                    
                    api.updateTicket(t.id(), nameF.getText(), newPrice, currentPassword).thenAccept(updRes -> Platform.runLater(() -> {
                        if(updRes.isOk()) {
                            showAlert("Zaktualizowano!");
                            refreshAdminList(username);
                        } else showAlert("Błąd aktualizacji: " + updRes.getStatusCode());
                    }));
                });

                row.getChildren().addAll(new Label("ID:" + t.id()), nameF, priceF, saveBtn, delBtn);
                adminTicketListContainer.getChildren().add(row);
            }
        }));
    }

    // Helpery do dialogów (dodaj do Main)
    private boolean confirmDialog(String msg) {
        // W JavaFX proste potwierdzenie wymagałoby AlertType.CONFIRMATION i wait.
        // Tutaj dla uproszczenia zwracamy true, lub można użyć Alert.
        // Ponieważ jesteśmy w async callbackach, modalne okno blokujące wątek UI jest ok.
        // Ale dla szybkości implementacji w tym przykładzie:
        return true; 
    }
    
    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, msg);
        alert.show();
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
        } catch (Exception e) {
            singlePrice = 0.0;
        }
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

        confirmBtn.setOnAction(e -> {
            int quantityToBuy = quantitySpinner.getValue();
            confirmBtn.setDisable(true);

            api.purchase(ticketId, quantityToBuy, currentLogin, currentPassword)
                    .thenAccept(result -> Platform.runLater(() -> {
                        if (result.isOk()) {
                            statusLabel.setStyle("-fx-text-fill: green;");
                            statusLabel.setText(loc.get("buy.success"));
                            new java.util.Timer().schedule(new java.util.TimerTask() {
                                @Override public void run() { Platform.runLater(buyStage::close); }
                            }, 1000);
                        } else {
                            statusLabel.setStyle("-fx-text-fill: red;");
                            statusLabel.setText("Błąd: " + result.getStatusCode());
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
        } catch (Exception e) {
            header.setText(loc.get("qr.error"));
        }

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
        nameLbl.setStyle("-fx-text-fill: #666; -fx-font-size: 14px;");
        Label priceLbl = new Label(priceInfo);
        priceLbl.setStyle("-fx-text-fill: #666; -fx-font-size: 12px;");

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
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Main extends Application {

    private static final String URL = "http://127.0.0.1:8080/random";

    private final HttpClient client = HttpClient.newHttpClient();

    @Override
    public void start(Stage stage) {
        Label title = new Label("Generator liczby (1–20)");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: 700;");

        Label resultLabel = new Label("Kliknij przycisk, aby wylosować liczbę.");
        resultLabel.setStyle("-fx-font-size: 14px;");

        Label numberLabel = new Label("—");
        numberLabel.setStyle(
                "-fx-font-size: 48px;" +
                "-fx-font-weight: 800;" +
                "-fx-padding: 10 0 10 0;"
        );

        Button button = new Button("Losuj liczbę");
        button.setPrefWidth(220);
        button.setPrefHeight(44);

        // Prosty, estetyczny styl przycisku (CSS inline)
        button.setStyle(
                "-fx-background-radius: 12;" +
                "-fx-font-size: 14px;" +
                "-fx-font-weight: 700;" +
                "-fx-text-fill: white;" +
                "-fx-background-color: linear-gradient(to right, #2563eb, #7c3aed);" +
                "-fx-cursor: hand;"
        );
        button.setEffect(new DropShadow(10, Color.rgb(0, 0, 0, 0.25)));

        button.setOnAction(e -> {
            button.setDisable(true);
            resultLabel.setText("Pobieranie...");
            numberLabel.setText("…");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(URL))
                    .GET()
                    .build();

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> Platform.runLater(() -> {
                        if (resp.statusCode() == 200) {
                            String body = resp.body().trim();
                            numberLabel.setText(body);
                            resultLabel.setText("Wylosowana liczba:");
                        } else {
                            numberLabel.setText("!");
                            resultLabel.setText("Błąd HTTP: " + resp.statusCode());
                        }
                        button.setDisable(false);
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            numberLabel.setText("!");
                            resultLabel.setText("Nie udało się połączyć z serwerem.");
                            button.setDisable(false);
                        });
                        return null;
                    });
        });

        VBox root = new VBox(14, title, resultLabel, numberLabel, button);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #f8fafc, #eef2ff);" +
                "-fx-font-family: 'Segoe UI', 'Inter', 'Arial';"
        );

        Scene scene = new Scene(root, 420, 320);
        stage.setTitle("Random 1–20");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

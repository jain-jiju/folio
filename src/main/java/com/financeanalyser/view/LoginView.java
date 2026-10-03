package com.financeanalyser.view;

import com.financeanalyser.db.DatabaseManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.lang.Runnable;

/**
 * Lock screen shown before the main window loads. Challenges the stored
 * SHA-256 password hash via DatabaseManager.verifyPassword() - this is the
 * missing enforcement point that made the Security tab's password change
 * purely cosmetic before. On success, runs onUnlocked.
 *
 * Default password on a brand-new database is "changeme" (set in
 * DatabaseManager.ensureDefaultUser) - the user should change it from the
 * Security tab after first login.
 */
public class LoginView extends StackPane {

    private final DatabaseManager db;
    private final int userId;
    private final Runnable onUnlocked;

    private PasswordField passwordField;
    private Label errorLabel;

    public LoginView(DatabaseManager db, int userId, Runnable onUnlocked) {
        this.db = db;
        this.userId = userId;
        this.onUnlocked = onUnlocked;
        build();
    }

    private void build() {
        setStyle("-fx-background-color: #F4EFE4;");
        setPrefSize(1280, 820);

        VBox card = new VBox(16);
        card.setAlignment(Pos.CENTER);
        card.setMaxWidth(360);
        card.setPadding(new Insets(40, 36, 36, 36));
        card.setStyle(
                "-fx-background-color: white;" +
                "-fx-background-radius: 20px;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 24, 0, 0, 8);"
        );

        javafx.scene.layout.StackPane logoBox = new javafx.scene.layout.StackPane();
        logoBox.getStyleClass().add("sidebar-logo-box");
        logoBox.setPrefSize(48, 48);
        logoBox.setMaxSize(48, 48);
        Label logoLetter = new Label("F");
        logoLetter.getStyleClass().add("sidebar-logo-letter");
        logoBox.getChildren().add(logoLetter);

        Label title = new Label("Folio is locked");
        title.setStyle("-fx-text-fill: #141414; -fx-font-size: 20px; -fx-font-weight: bold;");

        Label subtitle = new Label("Enter your local password to continue.");
        subtitle.setStyle("-fx-text-fill: #7A7A7A; -fx-font-size: 12px;");
        subtitle.setWrapText(true);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.getStyleClass().add("password-field");
        passwordField.setMaxWidth(Double.MAX_VALUE);
        passwordField.setOnAction(e -> attemptUnlock());

        errorLabel = new Label("");
        errorLabel.setStyle("-fx-text-fill: #D64545; -fx-font-size: 11px;");
        errorLabel.setVisible(false);

        Button unlockBtn = new Button("Unlock");
        unlockBtn.getStyleClass().add("btn-orange");
        unlockBtn.setMaxWidth(Double.MAX_VALUE);
        unlockBtn.setOnAction(e -> attemptUnlock());

        Label hint = new Label("First time? The default password is \"changeme\" -\nchange it from the Security tab once you're in.");
        hint.setStyle("-fx-text-fill: #B0B0B0; -fx-font-size: 10px;");
        hint.setWrapText(true);
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        hint.setAlignment(Pos.CENTER);

        card.getChildren().addAll(logoBox, title, subtitle, passwordField, errorLabel, unlockBtn, hint);
        getChildren().add(card);

        // Enter key anywhere on the screen also tries to unlock.
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) attemptUnlock();
        });
    }

    private void attemptUnlock() {
        String pw = passwordField.getText();
        if (pw == null || pw.isEmpty()) {
            showError("Enter your password.");
            return;
        }
        if (db.verifyPassword(userId, pw)) {
            onUnlocked.run();
        } else {
            showError("Incorrect password. Try again.");
            passwordField.clear();
            passwordField.requestFocus();
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
    }

    /** Focuses the password field - call after this view is attached to a showing Scene. */
    public void focusField() {
        passwordField.requestFocus();
    }
}

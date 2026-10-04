package com.financeanalyser.view;

import com.financeanalyser.controller.GeminiClient;
import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.email.EmailSyncConfig;
import com.financeanalyser.email.ImportResult;
import com.financeanalyser.email.StatementPasswordGenerator;
import com.financeanalyser.email.TransactionImportService;
import com.financeanalyser.model.User;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class SettingsView extends VBox {

    private final DatabaseManager db;
    private final int userId;
    private final Runnable onProfileUpdated;
    private TextField apiKeyInput;

    private TextField nameInput;
    private ImageView profilePreview;
    private String pendingPicPath;

    public SettingsView(DatabaseManager db, int userId, Runnable onProfileUpdated) {
        super(16);
        this.db = db;
        this.userId = userId;
        this.onProfileUpdated = onProfileUpdated;
        setPadding(new Insets(4, 28, 28, 28));
        buildUi();
    }

    private void buildUi() {
        getChildren().add(buildProfileCard());

        HBox row2 = new HBox(16);
        VBox dataCard = buildDataStaysYoursCard();
        dataCard.setPrefWidth(300);

        VBox rightCol = new VBox(16);
        HBox smallCardsRow = new HBox(16, buildPasswordCard(), buildBackupCard());
        HBox.setHgrow(smallCardsRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(smallCardsRow.getChildren().get(1), Priority.ALWAYS);
        rightCol.getChildren().addAll(smallCardsRow, buildGeminiCard());
        HBox.setHgrow(rightCol, Priority.ALWAYS);

        row2.getChildren().addAll(dataCard, rightCol);
        getChildren().add(row2);

        getChildren().add(buildEmailSyncCard());
    }

    private VBox buildProfileCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-blue");
        card.setPadding(new Insets(20));

        VBox textHeader = new VBox(2);
        Label eyebrow = new Label("PERSONAL PROFILE");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("Profile details");
        title.getStyleClass().add("block-title");
        Label sub = new Label("Your changes also update the account box in the sidebar.");
        sub.getStyleClass().add("muted");
        textHeader.getChildren().addAll(eyebrow, title, sub);

        User user = db.getUser(userId);

        profilePreview = new ImageView();
        profilePreview.setFitWidth(72);
        profilePreview.setFitHeight(72);
        profilePreview.setPreserveRatio(false);
        profilePreview.setClip(new Circle(36, 36, 36));
        loadImageInto(profilePreview, user.getProfilePicPath());
        StackPane avatarWrap = new StackPane(profilePreview);
        avatarWrap.setStyle("-fx-background-color: white; -fx-background-radius: 36px;");
        avatarWrap.setPrefSize(72, 72);
        avatarWrap.setMaxSize(72, 72);

        Button editPhotoBtn = new Button("Edit photo");
        editPhotoBtn.getStyleClass().add("btn-black");
        editPhotoBtn.setStyle(editPhotoBtn.getStyle() + "-fx-font-size: 10px; -fx-padding: 3 8 3 8;");
        editPhotoBtn.setOnAction(e -> choosePicture());
        StackPane avatarStack = new StackPane(avatarWrap, editPhotoBtn);
        StackPane.setAlignment(editPhotoBtn, Pos.BOTTOM_CENTER);

        VBox nameBox = new VBox(4);
        Label nameLabel = new Label("Username");
        nameLabel.getStyleClass().add("muted");
        nameInput = new TextField(user.getUsername());
        nameInput.setPrefWidth(260);
        nameBox.getChildren().addAll(nameLabel, nameInput);

        Button saveBtn = new Button("Save profile");
        saveBtn.getStyleClass().add("btn-orange");
        saveBtn.setOnAction(e -> saveProfile());

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        HBox row = new HBox(18, avatarStack, nameBox, sp, saveBtn);
        row.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(textHeader, row);
        return card;
    }

    private void choosePicture() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Profile Picture");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) return;
        pendingPicPath = file.getAbsolutePath();
        loadImageInto(profilePreview, pendingPicPath);
    }

    private void saveProfile() {
        String name = nameInput.getText().trim();
        if (name.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Name cannot be empty.").showAndWait();
            return;
        }
        String storedPicPath = null;
        if (pendingPicPath != null) {
            try {
                String ext = pendingPicPath.substring(pendingPicPath.lastIndexOf('.'));
                Path dest = Path.of("profile_pic" + ext);
                Files.copy(Path.of(pendingPicPath), dest, StandardCopyOption.REPLACE_EXISTING);
                storedPicPath = dest.toAbsolutePath().toString();
            } catch (IOException e) {
                new Alert(Alert.AlertType.ERROR, "Could not save picture: " + e.getMessage()).showAndWait();
                return;
            }
        }
        db.updateUserProfile(userId, name, storedPicPath);
        pendingPicPath = null;
        new Alert(Alert.AlertType.INFORMATION, "Profile saved.").showAndWait();
        if (onProfileUpdated != null) onProfileUpdated.run();
    }

    static void loadImageInto(ImageView view, String path) {
        if (path == null || path.isBlank() || !Files.exists(Path.of(path))) {
            view.setImage(null);
            return;
        }
        try {
            view.setImage(new Image(new File(path).toURI().toString(), 72, 72, false, true));
        } catch (Exception e) {
            view.setImage(null);
        }
    }

    private VBox buildDataStaysYoursCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-black");
        card.setPadding(new Insets(22));

        StackPane icon = new StackPane();
        icon.getStyleClass().add("card-blue");
        icon.setPrefSize(48, 48);
        icon.setMaxSize(48, 48);
        Label lock = new Label("\uD83D\uDD12");
        icon.getChildren().add(lock);

        Region sp1 = new Region();
        sp1.setPrefHeight(140);
        VBox.setVgrow(sp1, Priority.ALWAYS);

        Label pill = new Label("100% local");
        pill.getStyleClass().add("pill");

        Label headline = new Label("Your data stays\nyours.");
        headline.setStyle("-fx-text-fill: white; -fx-font-size: 26px; -fx-font-weight: bold;");

        Label desc = new Label("Passwords, transactions and backups live on this device. Nothing is sent to a cloud service.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);

        card.getChildren().addAll(icon, sp1, pill, headline, desc);
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox buildPasswordCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-white");
        card.setPadding(new Insets(18));
        Label eyebrow = new Label("ACCESS \u00B7 01");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("Change local password");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        Label info = new Label("Secured with a local SHA-256 hash.");
        info.getStyleClass().add("muted");

        Label pwLabel = new Label("New password");
        pwLabel.getStyleClass().add("muted");
        PasswordField newPw = new PasswordField();
        newPw.setPromptText("Enter a strong password");
        Button changeBtn = new Button("Update password");
        changeBtn.getStyleClass().add("btn-orange");
        changeBtn.setOnAction(e -> {
            String pw = newPw.getText();
            if (pw == null || pw.length() < 4) {
                new Alert(Alert.AlertType.WARNING, "Password should be at least 4 characters.").showAndWait();
                return;
            }
            db.setPassword(userId, pw);
            newPw.clear();
            new Alert(Alert.AlertType.INFORMATION, "Password updated.").showAndWait();
        });
        card.getChildren().addAll(eyebrow, title, info, pwLabel, newPw, changeBtn);
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox buildBackupCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-white");
        card.setPadding(new Insets(18));
        Label eyebrow = new Label("DATABASE \u00B7 02");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("Backup & restore");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        Label info = new Label("Your complete finance_analyser.db SQLite file, always local.");
        info.getStyleClass().add("muted");
        info.setWrapText(true);

        Button backupBtn = new Button("\u2193  Backup database");
        backupBtn.getStyleClass().add("btn-black");
        backupBtn.setMaxWidth(Double.MAX_VALUE);
        backupBtn.setOnAction(e -> backup());
        Button restoreBtn = new Button("Restore from backup");
        restoreBtn.getStyleClass().add("btn-outline");
        restoreBtn.setMaxWidth(Double.MAX_VALUE);
        restoreBtn.setOnAction(e -> restore());

        card.getChildren().addAll(eyebrow, title, info, backupBtn, restoreBtn);
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    private VBox buildGeminiCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-white");
        card.setPadding(new Insets(18));
        HBox top = new HBox();
        VBox textBox = new VBox(2);
        Label eyebrow = new Label("OPTIONAL INTELLIGENCE \u00B7 03");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("Gemini API configuration");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        Label info = new Label("Power natural-language advice, or continue with built-in offline logic.");
        info.getStyleClass().add("muted");
        info.setWrapText(true);
        textBox.getChildren().addAll(eyebrow, title, info);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        VBox keyBox = new VBox(4);
        Label keyLabel = new Label("API key");
        keyLabel.getStyleClass().add("muted");
        apiKeyInput = new PasswordField();
        apiKeyInput.setText(GeminiClient.getApiKey());
        apiKeyInput.setPromptText("Paste your Gemini API key");
        apiKeyInput.setPrefWidth(220);
        Button saveBtn = new Button("Save key");
        saveBtn.getStyleClass().add("btn-orange");
        saveBtn.setOnAction(e -> {
            GeminiClient.saveApiKey(apiKeyInput.getText().trim());
            new Alert(Alert.AlertType.INFORMATION, "Gemini API key saved locally.").showAndWait();
        });
        HBox keyRow = new HBox(8, apiKeyInput, saveBtn);
        keyBox.getChildren().addAll(keyLabel, keyRow);

        top.getChildren().addAll(textBox, keyBox);
        top.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(top);
        return card;
    }

    private VBox buildEmailSyncCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-white");
        card.setPadding(new Insets(18));

        Label eyebrow = new Label("AUTOMATION · 04");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("Bank email sync");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        Label info = new Label("Reads unread bank-alert emails over IMAP and logs transactions automatically. " +
                "Credentials are stored locally (obfuscated, not plaintext) and never leave this device.");
        info.getStyleClass().add("muted");
        info.setWrapText(true);

        Map<String, String> existing = db.getEmailSyncConfig(userId);

        TextField hostInput = new TextField(existing != null ? existing.get("imap_host") : "imap.gmail.com");
        hostInput.setPromptText("IMAP host (e.g. imap.gmail.com)");
        TextField emailInput = new TextField(existing != null ? existing.get("email_address") : "");
        emailInput.setPromptText("Your email address");
        PasswordField appPwInput = new PasswordField();
        appPwInput.setPromptText("App password (not your real password)");

        Label pdfLabel = new Label("Statement PDF password formula (optional)");
        pdfLabel.getStyleClass().add("muted");
        TextField nameInputPdf = new TextField();
        nameInputPdf.setPromptText("Full name (as on the statement)");
        DatePicker dobInput = new DatePicker();
        dobInput.setPromptText("Date of birth");
        TextField mobileInput = new TextField();
        mobileInput.setPromptText("Mobile number");

        Button saveBtn = new Button("Save sync settings");
        saveBtn.getStyleClass().add("btn-black");
        Button syncNowBtn = new Button("Sync now");
        syncNowBtn.getStyleClass().add("btn-orange");
        Label statusLabel = new Label(existing != null && existing.get("last_sync_at") != null
                ? "Last synced: " + existing.get("last_sync_at") : "Never synced yet.");
        statusLabel.getStyleClass().add("muted");
        statusLabel.setWrapText(true);

        saveBtn.setOnAction(e -> {
            String formulaHint = nameInputPdf.getText() + " + DOB + mobile (candidates auto-generated)";
            db.saveEmailSyncConfig(userId, hostInput.getText().trim(), 993, emailInput.getText().trim(),
                    EmailSyncConfig.obfuscate(appPwInput.getText()), formulaHint, true);
            new Alert(Alert.AlertType.INFORMATION, "Email sync settings saved.").showAndWait();
        });

        syncNowBtn.setOnAction(e -> {
            Map<String, String> cfg = db.getEmailSyncConfig(userId);
            if (cfg == null || cfg.get("email_address") == null || cfg.get("email_address").isBlank()) {
                new Alert(Alert.AlertType.WARNING, "Save your sync settings first.").showAndWait();
                return;
            }
            EmailSyncConfig syncConfig = new EmailSyncConfig();
            syncConfig.imapHost = cfg.get("imap_host");
            syncConfig.imapPort = Integer.parseInt(cfg.get("imap_port"));
            syncConfig.emailAddress = cfg.get("email_address");
            syncConfig.appPassword = EmailSyncConfig.deobfuscate(cfg.get("app_password_enc"));

            LocalDate dob = dobInput.getValue();
            List<String> candidates = StatementPasswordGenerator.generateCandidates(
                    nameInputPdf.getText(), dob, mobileInput.getText());

            syncNowBtn.setDisable(true);
            statusLabel.setText("Syncing...");
            Task<ImportResult> task = new Task<>() {
                @Override
                protected ImportResult call() {
                    TransactionImportService service = new TransactionImportService(db, userId, syncConfig, candidates);
                    return service.syncNow();
                }
            };
            task.setOnSucceeded(ev -> Platform.runLater(() -> {
                statusLabel.setText(task.getValue().summary());
                syncNowBtn.setDisable(false);
            }));
            task.setOnFailed(ev -> Platform.runLater(() -> {
                statusLabel.setText("Sync failed: " + task.getException().getMessage());
                syncNowBtn.setDisable(false);
            }));
            new Thread(task, "email-sync").start();
        });

        HBox buttonRow = new HBox(8, saveBtn, syncNowBtn);
        card.getChildren().addAll(eyebrow, title, info, hostInput, emailInput, appPwInput,
                pdfLabel, nameInputPdf, dobInput, mobileInput, buttonRow, statusLabel);
        VBox.setVgrow(card, Priority.ALWAYS);
        return card;
    }

    private void backup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Backup Database");
        chooser.setInitialFileName("finance_analyser_backup.db");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite DB", "*.db"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        try {
            db.backupTo(file.getAbsolutePath());
            new Alert(Alert.AlertType.INFORMATION, "Saved to " + file.getAbsolutePath()).showAndWait();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Backup failed: " + e.getMessage()).showAndWait();
        }
    }

    private void restore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Restore Database");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite DB", "*.db"));
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "This replaces your current data with the selected backup. Continue?");
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    db.restoreFrom(file.getAbsolutePath());
                    new Alert(Alert.AlertType.INFORMATION, "Restored. Please restart the app.").showAndWait();
                } catch (IOException | SQLException e) {
                    new Alert(Alert.AlertType.ERROR, "Restore failed: " + e.getMessage()).showAndWait();
                }
            }
        });
    }
}

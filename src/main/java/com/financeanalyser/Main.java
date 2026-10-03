package com.financeanalyser;

import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.User;
import com.financeanalyser.view.LoginView;
import com.financeanalyser.view.MainWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

/**
 * Personal Finance Analyser - entry point.
 * Run with: mvn javafx:run
 *
 * Startup now goes through a lock screen (LoginView) that checks the stored
 * password hash via DatabaseManager.verifyPassword() before the main window
 * is built. This is the enforcement point the password-change feature in
 * the Security tab was missing - previously nothing in the app ever
 * challenged the user for it.
 */
public class Main extends Application {

    private DatabaseManager db;

    @Override
    public void start(Stage stage) {
        db = new DatabaseManager("finance_analyser.db");
        User user = db.ensureDefaultUser("Alex Morgan");
        int userId = user.getUserId();

        Scene scene = new Scene(new javafx.scene.layout.StackPane(), 1280, 820);
        scene.getStylesheets().add(getClass().getResource("/glassmorphism.css").toExternalForm());

        LoginView loginView = new LoginView(db, userId, () -> {
            MainWindow mainWindow = new MainWindow(db, userId);
            scene.setRoot(mainWindow);
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                    mainWindow.getDashboardView()::requestAmountFocus
            );
        });
        scene.setRoot(loginView);

        stage.setTitle("Personal Finance Analyser");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        stage.show();

        loginView.focusField();
    }

    @Override
    public void stop() {
        if (db != null) db.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

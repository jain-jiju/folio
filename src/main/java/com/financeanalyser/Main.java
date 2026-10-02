package com.financeanalyser;

import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.User;
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
 */
public class Main extends Application {

    private DatabaseManager db;

    @Override
    public void start(Stage stage) {
        db = new DatabaseManager("finance_analyser.db");
        User user = db.ensureDefaultUser("Alex Morgan");

        MainWindow mainWindow = new MainWindow(db, user.getUserId());
        Scene scene = new Scene(mainWindow, 1280, 820);
        scene.getStylesheets().add(getClass().getResource("/glassmorphism.css").toExternalForm());
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                mainWindow.getDashboardView()::requestAmountFocus
        );

        stage.setTitle("Personal Finance Analyser");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        stage.show();
    }

    @Override
    public void stop() {
        if (db != null) db.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

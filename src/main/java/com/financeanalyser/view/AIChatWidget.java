package com.financeanalyser.view;

import com.financeanalyser.controller.GeminiClient;
import com.financeanalyser.db.DatabaseManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Floating Personal Finance Copilot chat panel.
 * Messages are rendered as explicit bubble nodes inside a scroll pane so they
 * remain visible and readable at all window sizes.
 */
public class AIChatWidget extends VBox {

    public static final double WIDTH = 380;
    public static final double HEIGHT = 500;

    private static final String[] QUICK_PROMPTS = {
            "Am I on track today?",
            "How can I save ₹2,000?",
            "Summarize this week's spending"
    };

    private final DatabaseManager db;
    private final int userId;
    private final Supplier<Map<String, Object>> contextSupplier;
    private final GeminiClient gemini = new GeminiClient();

    private final VBox historyBox = new VBox(10);
    private final ScrollPane chatScroll = new ScrollPane();
    private final TextField input = new TextField();
    private final Button sendBtn = new Button("Send");
    private final ProgressIndicator thinking = new ProgressIndicator();

    private double dragOffsetX, dragOffsetY;

    public AIChatWidget(DatabaseManager db, int userId, Supplier<Map<String, Object>> contextSupplier) {
        this.db = db;
        this.userId = userId;
        this.contextSupplier = contextSupplier;

        buildUi();
        setVisible(false);
        setManaged(false);

        appendBot("Hi! I'm your finance copilot. Ask me about your spending, saving, or budget.");
    }

    private void buildUi() {
        getStyleClass().add("chat-panel");
        setSpacing(8);
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setPadding(new Insets(10));

        Label grip = new Label("⠿⠿⠿  Personal Finance Copilot");
        grip.getStyleClass().add("chat-grip");
        grip.setMaxWidth(Double.MAX_VALUE);
        grip.setPadding(new Insets(7, 10, 7, 10));
        HBox.setHgrow(grip, Priority.ALWAYS);
        grip.setOnMousePressed(e -> {
            dragOffsetX = e.getSceneX() - getLayoutX();
            dragOffsetY = e.getSceneY() - getLayoutY();
        });
        grip.setOnMouseDragged(e -> {
            if (getParent() instanceof Pane parent) {
                double newX = e.getSceneX() - dragOffsetX;
                double newY = e.getSceneY() - dragOffsetY;
                newX = Math.max(0, Math.min(newX, Math.max(0, parent.getWidth() - getWidth())));
                newY = Math.max(0, Math.min(newY, Math.max(0, parent.getHeight() - getHeight())));
                setLayoutX(newX);
                setLayoutY(newY);
            }
        });

        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().add("button-ghost");
        closeBtn.setOnAction(e -> collapse());

        HBox topRow = new HBox(6, grip, closeBtn);
        topRow.setAlignment(Pos.CENTER);
        topRow.setFillHeight(true);

        historyBox.setPadding(new Insets(10));
        historyBox.setSpacing(10);
        historyBox.setAlignment(Pos.TOP_LEFT);
        historyBox.setFillWidth(true);

        chatScroll.setContent(historyBox);
        chatScroll.setFitToWidth(true);
        chatScroll.setFitToHeight(false);
        chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        chatScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        chatScroll.getStyleClass().add("chat-scroll");
        chatScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(chatScroll, Priority.ALWAYS);

        FlowPane chipsRow = new FlowPane();
        chipsRow.setHgap(6);
        chipsRow.setVgap(6);
        chipsRow.setPrefWrapLength(WIDTH - 35);
        for (String prompt : QUICK_PROMPTS) {
            Button chip = new Button(prompt);
            chip.getStyleClass().add("chat-chip");
            chip.setWrapText(true);
            chip.setOnAction(e -> send(prompt));
            chipsRow.getChildren().add(chip);
        }

        input.setPromptText("Ask about spending or type an expense...");
        input.setOnAction(e -> send(input.getText()));
        HBox.setHgrow(input, Priority.ALWAYS);

        sendBtn.getStyleClass().add("btn-orange");
        sendBtn.setOnAction(e -> send(input.getText()));

        thinking.setPrefSize(18, 18);
        thinking.setMaxSize(18, 18);
        thinking.setVisible(false);
        thinking.setManaged(false);

        HBox inputRow = new HBox(6, input, thinking, sendBtn);
        inputRow.setAlignment(Pos.CENTER);

        getChildren().addAll(topRow, chatScroll, chipsRow, inputRow);
    }

    public void expand() {
        setManaged(true);
        setVisible(true);
        toFront();
        Platform.runLater(() -> {
            chatScroll.layout();
            scrollToBottom();
            input.requestFocus();
        });
    }

    public void collapse() {
        setVisible(false);
        setManaged(false);
    }

    public void toggle() {
        if (isVisible()) collapse(); else expand();
    }

    private Label createMessageLabel(String prefix, String text) {
        Label label = new Label(prefix + text);
        label.setWrapText(true);
        label.setMaxWidth(WIDTH - 55);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setTextFill(Color.web("#141414"));
        label.setPadding(new Insets(9, 11, 9, 11));
        label.setStyle(
                "-fx-background-radius: 12px;" +
                "-fx-border-radius: 12px;" +
                "-fx-font-size: 13px;"
        );
        return label;
    }

    private void appendUser(String text) {
        Label bubble = createMessageLabel("You\n", text);
        bubble.setStyle(
                "-fx-background-color: #ff9100;" +
                "-fx-background-radius: 12px;" +
                "-fx-border-radius: 12px;" +
                "-fx-font-size: 13px;" +
                "-fx-font-weight: bold;"
        );

        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setFillHeight(false);
        historyBox.getChildren().add(row);
        scrollToBottom();
    }

    private void appendBot(String text) {
        Label bubble = createMessageLabel("Copilot\n", text);
        bubble.setStyle(
                "-fx-background-color: #ff5100;" +
                "-fx-background-radius: 12px;" +
                "-fx-border-radius: 12px;" +
                "-fx-font-size: 13px;"
        );

        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(false);
        historyBox.getChildren().add(row);
        scrollToBottom();
    }

    private void appendSystem(String text) {
        Label bubble = createMessageLabel("", text);
        bubble.setTextFill(Color.web("#666666"));
        bubble.setStyle(
                "-fx-background-color: #F7F7F7;" +
                "-fx-background-radius: 10px;" +
                "-fx-font-size: 11px;"
        );
        HBox row = new HBox(bubble);
        row.setAlignment(Pos.CENTER_LEFT);
        historyBox.getChildren().add(row);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            chatScroll.layout();
            historyBox.layout();
            chatScroll.setVvalue(1.0);
        });
    }

    private void setThinking(boolean value) {
        thinking.setVisible(value);
        thinking.setManaged(value);
        sendBtn.setDisable(value);
        input.setDisable(value);
    }

    private void send(String text) {
        if (text == null || text.isBlank() || sendBtn.isDisabled()) return;
        text = text.trim();
        input.clear();
        appendUser(text);

        final String message = text;
        String lower = message.toLowerCase();
        boolean looksLikeExpense = (lower.contains("spent") || lower.contains("paid") || lower.contains("bought"))
                && message.chars().anyMatch(Character::isDigit);

        if (looksLikeExpense) {
            setThinking(true);
            CompletableFuture.supplyAsync(() -> gemini.parseExpense(message))
                    .thenAccept(parsed -> Platform.runLater(() -> {
                        try {
                            if (parsed != null && parsed.amount > 0) {
                                db.addTransaction(userId, parsed.amount, parsed.category, "Expense",
                                        parsed.paymentMethod, LocalDate.now().toString(), parsed.notes);
                                appendBot(String.format("Logged ₹%.0f under %s via %s. Anything else?",
                                        parsed.amount, parsed.category, parsed.paymentMethod));
                            } else {
                                appendBot("I couldn't identify the expense amount. Try something like: Spent ₹150 on an auto ride.");
                            }
                        } finally {
                            setThinking(false);
                            input.requestFocus();
                        }
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            appendBot("I couldn't process that expense right now. Please try again.");
                            setThinking(false);
                        });
                        return null;
                    });
            return;
        }

        setThinking(true);
        Map<String, Object> context = contextSupplier.get();
        CompletableFuture.supplyAsync(() -> gemini.chatReply(message, context))
                .thenAccept(reply -> Platform.runLater(() -> {
                    try {
                        appendBot(reply == null || reply.isBlank()
                                ? "I don't have a response yet. Please try asking in a different way."
                                : reply);
                    } finally {
                        setThinking(false);
                        input.requestFocus();
                    }
                }))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        appendSystem("The assistant is temporarily unavailable. Your existing finance data is safe.");
                        setThinking(false);
                    });
                    return null;
                });
    }
}

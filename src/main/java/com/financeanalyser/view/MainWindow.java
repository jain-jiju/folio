package com.financeanalyser.view;

import com.financeanalyser.controller.ForecastEngine;
import com.financeanalyser.controller.GamificationEngine;
import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.GamificationState;
import com.financeanalyser.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root layout: the "Folio" sidebar (logo, nav, live XP card, profile footer),
 * a top header (eyebrow + page title + date pill), the 7 module tabs in a
 * StackPane, and the omnipresent floating AI chat layered on top so it stays
 * anchored across every tab without losing its state.
 */
public class MainWindow extends AnchorPane {

    private static final String[][] NAV_ITEMS = {
            {"\u2302", "Overview"},
            {"\u21C4", "Transactions"},
            {"\uD83D\uDCB3", "Budget planner"},
            {"\uD83D\uDCCA", "Analytics"},
            {"\uD83C\uDFC6", "Missions"},
            {"\uD83D\uDCCB", "Reports"},
            {"\uD83D\uDD12", "Security"},
    };

    private final DatabaseManager db;
    private final int userId;

    private DashboardView dashboard;
    private TransactionsView transactions;
    private PlannerView planner;
    private AnalyticsView analytics;
    private GamificationView gamification;
    private ReportsView reports;
    private SettingsView settings;

    private StackPane contentStack;
    private AIChatWidget chat;
    private boolean chatPositioned = false;

    private Label headerTitleLabel;
    private Label xpTitleLabel;
    private Label xpSubtitleLabel;
    private ProgressBar xpProgressBar;
    private ImageView sidebarAvatar;
    private Label sidebarNameLabel;

    public MainWindow(DatabaseManager db, int userId) {
        this.db = db;
        this.userId = userId;

        HBox base = new HBox();
        base.getChildren().addAll(buildSidebar(), buildRightColumn());
        AnchorPane.setTopAnchor(base, 0.0);
        AnchorPane.setBottomAnchor(base, 0.0);
        AnchorPane.setLeftAnchor(base, 0.0);
        AnchorPane.setRightAnchor(base, 0.0);
        getChildren().add(base);

        chat = new AIChatWidget(db, userId, this::currentTabContext);
        getChildren().add(chat);

        widthProperty().addListener((o, ov, nv) -> repositionOrClampChat());
        heightProperty().addListener((o, ov, nv) -> repositionOrClampChat());
    }

    // ------------------------------------------------------------- sidebar
    private VBox buildSidebar() {
        VBox sidebar = new VBox(2);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(240);
        sidebar.setMinWidth(240);
        sidebar.setPadding(new Insets(20, 0, 18, 0));

        HBox logoRow = new HBox(10);
        logoRow.setPadding(new Insets(0, 18, 22, 18));
        logoRow.setAlignment(Pos.CENTER_LEFT);
        StackPane logoBox = new StackPane();
        logoBox.getStyleClass().add("sidebar-logo-box");
        logoBox.setPrefSize(36, 36);
        logoBox.setMaxSize(36, 36);
        Label logoLetter = new Label("F");
        logoLetter.getStyleClass().add("sidebar-logo-letter");
        logoBox.getChildren().add(logoLetter);
        VBox logoText = new VBox(0);
        Label logoTitle = new Label("Folio");
        logoTitle.getStyleClass().add("sidebar-logo-title");
        Label logoSub = new Label("Finance made clear");
        logoSub.getStyleClass().add("sidebar-logo-sub");
        logoText.getChildren().addAll(logoTitle, logoSub);
        logoRow.getChildren().addAll(logoBox, logoText);
        sidebar.getChildren().add(logoRow);

        ToggleGroup group = new ToggleGroup();
        for (int i = 0; i < NAV_ITEMS.length; i++) {
            final int idx = i;
            ToggleButton btn = new ToggleButton(NAV_ITEMS[i][0] + "   " + NAV_ITEMS[i][1]);
            btn.getStyleClass().add("nav-button");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setMinHeight(42);
            btn.setToggleGroup(group);
            btn.setOnAction(e -> navigate(idx));
            VBox.setMargin(btn, new Insets(0, 10, 0, 10));
            sidebar.getChildren().add(btn);
            if (i == 0) btn.setSelected(true);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().add(spacer);

        sidebar.getChildren().add(buildXpCard());
        sidebar.getChildren().add(buildProfileFooter());
        return sidebar;
    }

    private VBox buildXpCard() {
        VBox card = new VBox(6);
        card.getStyleClass().add("xp-card");
        card.setPadding(new Insets(14));
        VBox.setMargin(card, new Insets(10, 14, 14, 14));

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        StackPane badge = new StackPane();
        badge.getStyleClass().add("xp-badge");
        badge.setPrefSize(28, 28);
        badge.setMaxSize(28, 28);
        Label sparkle = new Label("\u2726");
        badge.getChildren().add(sparkle);
        topRow.getChildren().add(badge);

        xpTitleLabel = new Label("0 XP earned");
        xpTitleLabel.getStyleClass().add("xp-title");
        xpSubtitleLabel = new Label("");
        xpSubtitleLabel.getStyleClass().add("xp-subtitle");
        xpSubtitleLabel.setWrapText(true);
        xpProgressBar = new ProgressBar(0);
        xpProgressBar.setMaxWidth(Double.MAX_VALUE);
        xpProgressBar.setPrefHeight(8);

        card.getChildren().addAll(topRow, xpTitleLabel, xpSubtitleLabel, xpProgressBar);
        refreshSidebarXp();
        return card;
    }

    private HBox buildProfileFooter() {
        HBox row = new HBox(10);
        row.setPadding(new Insets(0, 18, 0, 18));
        row.setAlignment(Pos.CENTER_LEFT);

        sidebarAvatar = new ImageView();
        sidebarAvatar.setFitWidth(36);
        sidebarAvatar.setFitHeight(36);
        sidebarAvatar.setPreserveRatio(false);
        sidebarAvatar.setClip(new Circle(18, 18, 18));
        StackPane avatarWrap = new StackPane(sidebarAvatar);
        avatarWrap.getStyleClass().add("profile-avatar");
        avatarWrap.setPrefSize(36, 36);
        avatarWrap.setMaxSize(36, 36);

        sidebarNameLabel = new Label();
        sidebarNameLabel.getStyleClass().add("profile-name");

        row.getChildren().addAll(avatarWrap, sidebarNameLabel);
        refreshSidebarProfile();
        return row;
    }

    private void navigate(int idx) {
        contentStack.getChildren().forEach(c -> {
            c.setVisible(false);
            c.setManaged(false);
        });
        Region view = viewAt(idx);
        view.setVisible(true);
        view.setManaged(true);
        headerTitleLabel.setText(NAV_ITEMS[idx][1]);
        refreshView(idx);
        refreshSidebarXp();
    }

    private Region viewAt(int idx) {
        return switch (idx) {
            case 0 -> dashboard;
            case 1 -> transactions;
            case 2 -> planner;
            case 3 -> analytics;
            case 4 -> gamification;
            case 5 -> reports;
            case 6 -> settings;
            default -> throw new IllegalArgumentException("Unknown nav index: " + idx);
        };
    }

    private void refreshView(int idx) {
        switch (idx) {
            case 0 -> dashboard.refresh();
            case 1 -> transactions.refresh();
            case 2 -> planner.refresh();
            case 3 -> analytics.refresh();
            case 4 -> gamification.refresh();
            default -> { /* Reports and Settings have no persistent state to refresh */ }
        }
    }

    // -------------------------------------------------------------- header
    private VBox buildRightColumn() {
        VBox right = new VBox();
        HBox.setHgrow(right, Priority.ALWAYS);
        right.getChildren().add(buildHeader());

        contentStack = new StackPane();
        contentStack.setAlignment(Pos.TOP_CENTER);

        dashboard = new DashboardView(db, userId, this::onTransactionAdded);
        transactions = new TransactionsView(db, userId);
        planner = new PlannerView(db, userId);
        analytics = new AnalyticsView(db, userId);
        gamification = new GamificationView(db, userId);
        reports = new ReportsView(db, userId);
        settings = new SettingsView(db, userId, this::refreshSidebarProfile);

        contentStack.getChildren().addAll(dashboard, transactions, planner, analytics, gamification, reports, settings);
        for (var v : contentStack.getChildren()) {
            v.setVisible(false);
            v.setManaged(false);
        }
        dashboard.setVisible(true);
        dashboard.setManaged(true);

        ScrollPane scroll = new ScrollPane(contentStack);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        right.getChildren().add(scroll);
        return right;
    }

    private HBox buildHeader() {
        HBox header = new HBox();
        header.setPrefHeight(96);
        header.setMinHeight(96);
        header.setPadding(new Insets(20, 28, 10, 28));
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBlock = new VBox(2);
        Label eyebrow = new Label("PERSONAL FINANCE ANALYSER");
        eyebrow.getStyleClass().add("header-eyebrow");
        headerTitleLabel = new Label(NAV_ITEMS[0][1]);
        headerTitleLabel.getStyleClass().add("header-title");
        titleBlock.getChildren().addAll(eyebrow, headerTitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label datePill = new Label(LocalDate.now().format(DateTimeFormatter.ofPattern("MMM d, yyyy")));
        datePill.getStyleClass().add("date-pill");

        StackPane aiAvatar = new StackPane();
        aiAvatar.getStyleClass().add("ai-avatar");
        aiAvatar.setPrefSize(40, 40);
        aiAvatar.setMaxSize(40, 40);
        aiAvatar.setCursor(javafx.scene.Cursor.HAND);
        Label aiIcon = new Label("\u2726");
        aiIcon.setStyle("-fx-text-fill: white;");
        aiAvatar.getChildren().add(aiIcon);
        aiAvatar.setOnMouseClicked(e -> chat.toggle());

        HBox rightGroup = new HBox(12, datePill, aiAvatar);
        rightGroup.setAlignment(Pos.CENTER_RIGHT);

        header.getChildren().addAll(titleBlock, spacer, rightGroup);
        return header;
    }

    /** Re-reads the profile (name + picture) and updates the sidebar footer. Called after Settings saves changes. */
    private void refreshSidebarProfile() {
        User user = db.getUser(userId);
        sidebarNameLabel.setText(user.getUsername());
        SettingsView.loadImageInto(sidebarAvatar, user.getProfilePicPath());
    }

    private void refreshSidebarXp() {
        GamificationState g = db.getGamification(userId);
        int level = g.level();
        int xpIntoLevel = g.getXpPoints() % 300;
        int xpToNext = 300 - xpIntoLevel;
        xpTitleLabel.setText(g.getXpPoints() + " XP earned");
        xpSubtitleLabel.setText("You're " + xpToNext + " points away from level " + (level + 1) + ".");
        xpProgressBar.setProgress(xpIntoLevel / 300.0);
    }

    // ------------------------------------------------------------ AI chat
    private Map<String, Object> currentTabContext() {
        double salary = db.getUser(userId).getMonthlySalary();
        String todayIso = LocalDate.now().toString();
        double spentToday = db.sumExpenses(userId, todayIso, todayIso);
        String[] monthBounds = ForecastEngine.monthBounds();
        double spentMonth = db.sumExpenses(userId, monthBounds[0], monthBounds[1]);

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("todayAllowance", ForecastEngine.todayAllowance(salary));
        context.put("spentToday", spentToday);
        context.put("spentThisMonth", spentMonth);
        context.put("monthlySalary", salary);
        return context;
    }

    private void onTransactionAdded() {
        transactions.refresh();
        analytics.refresh();
        gamification.refresh();
        refreshSidebarXp();
    }

    /** Anchors the chat panel just under the header's AI icon (top-right), and keeps
     * it inside the window bounds if the user drags it or the window is resized. */
    private void repositionOrClampChat() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        double margin = 24;
        if (!chatPositioned) {
            chat.setLayoutX(w - AIChatWidget.WIDTH - margin);
            chat.setLayoutY(96);
            chatPositioned = true;
        } else {
            double clampedX = Math.max(0, Math.min(chat.getLayoutX(), w - AIChatWidget.WIDTH));
            double clampedY = Math.max(0, Math.min(chat.getLayoutY(), h - AIChatWidget.HEIGHT));
            chat.setLayoutX(clampedX);
            chat.setLayoutY(clampedY);
        }
    }

    public DashboardView getDashboardView() {
        return dashboard;
    }
}

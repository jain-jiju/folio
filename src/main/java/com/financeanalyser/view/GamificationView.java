package com.financeanalyser.view;

import com.financeanalyser.controller.ForecastEngine;
import com.financeanalyser.controller.GamificationEngine;
import com.financeanalyser.controller.MissionGenerator;
import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.GamificationState;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Map;

public class GamificationView extends VBox {

    private static final String[] LEVEL_NAMES = {
            "Budget beginner", "Budget explorer", "Budget strategist", "Budget master", "Finance legend"
    };

    private final DatabaseManager db;
    private final int userId;

    private Label levelBadge, levelName, levelSub;
    private Label streakBig, streakSub;
    private Label badgeCountPill;
    private HBox badgeRow;
    private Label missionsXpLabel;
    private VBox missionBox;

    public GamificationView(DatabaseManager db, int userId) {
        super(16);
        this.db = db;
        this.userId = userId;
        setPadding(new Insets(4, 28, 28, 28));
        buildUi();
        refresh();
    }

    private void buildUi() {
        HBox topRow = new HBox(16, buildLevelCard(), buildStreakCard());
        HBox.setHgrow(topRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(topRow.getChildren().get(1), Priority.ALWAYS);
        getChildren().add(topRow);

        VBox badgeCard = new VBox(12);
        badgeCard.getStyleClass().add("card-white");
        badgeCard.setPadding(new Insets(18));
        HBox badgeTop = new HBox();
        VBox badgeTitleBox = new VBox(2);
        Label eyebrow = new Label("COLLECTION");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("Your badge wall");
        title.getStyleClass().add("section-title");
        badgeTitleBox.getChildren().addAll(eyebrow, title);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        badgeCountPill = new Label("0 of 3 unlocked");
        badgeCountPill.getStyleClass().add("pill-dark");
        badgeTop.getChildren().addAll(badgeTitleBox, sp, badgeCountPill);
        badgeTop.setAlignment(Pos.CENTER_LEFT);
        badgeRow = new HBox(14);
        badgeCard.getChildren().addAll(badgeTop, badgeRow);
        getChildren().add(badgeCard);

        VBox missionCard = new VBox(12);
        missionCard.getStyleClass().add("card-white");
        missionCard.setPadding(new Insets(18));
        HBox missionTop = new HBox();
        VBox missionTitleBox = new VBox(2);
        Label mEyebrow = new Label("FRESH EVERY MONDAY");
        mEyebrow.getStyleClass().add("block-eyebrow");
        Label mTitle = new Label("Weekly micro-missions");
        mTitle.getStyleClass().add("section-title");
        missionTitleBox.getChildren().addAll(mEyebrow, mTitle);
        Region msp = new Region();
        HBox.setHgrow(msp, Priority.ALWAYS);
        missionsXpLabel = new Label("+0 XP available");
        missionsXpLabel.setStyle("-fx-text-fill: #F1531F; -fx-font-weight: bold;");
        missionTop.getChildren().addAll(missionTitleBox, msp, missionsXpLabel);
        missionTop.setAlignment(Pos.CENTER_LEFT);
        missionBox = new VBox(14);
        missionCard.getChildren().addAll(missionTop, missionBox);
        getChildren().add(missionCard);
    }

    private VBox buildLevelCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-blue");
        card.setPadding(new Insets(18));

        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        StackPane badge = new StackPane();
        badge.setStyle("-fx-background-color: white; -fx-background-radius: 32px;");
        badge.setPrefSize(64, 64);
        badge.setMaxSize(64, 64);
        levelBadge = new Label("1");
        levelBadge.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        badge.getChildren().add(levelBadge);

        VBox textBox = new VBox(2);
        Label pill = new Label("Level 1");
        pill.getStyleClass().add("pill");
        levelName = new Label("Budget beginner");
        levelName.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        levelSub = new Label("0 XP earned \u00B7 300 XP to next level");
        levelSub.getStyleClass().add("muted");
        textBox.getChildren().addAll(pill, levelName, levelSub);

        row.getChildren().addAll(badge, textBox);
        card.getChildren().add(row);
        return card;
    }

    private VBox buildStreakCard() {
        VBox card = new VBox(6);
        card.getStyleClass().add("card-orange");
        card.setPadding(new Insets(18));
        card.setPrefWidth(240);

        Label eyebrow = new Label("CURRENT STREAK");
        eyebrow.getStyleClass().add("stat-label");
        streakBig = new Label("0");
        streakBig.setStyle("-fx-font-size: 40px; -fx-font-weight: bold;");
        streakSub = new Label("day");
        streakSub.getStyleClass().add("muted");

        card.getChildren().addAll(eyebrow, streakBig, streakSub);
        return card;
    }

    public void refresh() {
        GamificationState g = db.getGamification(userId);
        int level = g.level();
        int xpIntoLevel = g.getXpPoints() % 300;
        int xpToNext = 300 - xpIntoLevel;
        levelBadge.setText(String.valueOf(level));
        levelName.setText(LEVEL_NAMES[Math.min(level - 1, LEVEL_NAMES.length - 1)]);
        levelSub.setText(g.getXpPoints() + " XP earned \u00B7 " + xpToNext + " XP to level " + (level + 1));

        streakBig.setText(String.valueOf(g.getCurrentStreak()));
        streakSub.setText(g.getCurrentStreak() == 1 ? "day" : "days");

        String[] monthBounds = ForecastEngine.monthBounds();
        String[] prevBounds = ForecastEngine.previousMonthBounds();
        List<String> earned = GamificationEngine.refreshBadges(db, userId, monthBounds[0], monthBounds[1], prevBounds[0], prevBounds[1]);

        badgeCountPill.setText(earned.size() + " of " + GamificationEngine.BADGE_DEFS.size() + " unlocked");
        badgeRow.getChildren().clear();
        for (Map.Entry<String, String> entry : GamificationEngine.BADGE_DEFS.entrySet()) {
            boolean unlocked = earned.contains(entry.getKey());
            VBox card = new VBox(8);
            card.getStyleClass().add("card-white");
            card.setPadding(new Insets(16));
            HBox.setHgrow(card, Priority.ALWAYS);

            StackPane icon = new StackPane();
            icon.getStyleClass().add(unlocked ? "card-orange" : "pill-locked");
            icon.setPrefSize(40, 40);
            icon.setMaxSize(40, 40);
            Label iconLbl = new Label(unlocked ? "\uD83C\uDFC6" : "\uD83D\uDD12");
            icon.getChildren().add(iconLbl);

            Label name = new Label(entry.getKey());
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
            Label desc = new Label(entry.getValue());
            desc.getStyleClass().add("muted");
            desc.setWrapText(true);
            Label status = new Label(unlocked ? "Unlocked" : "Locked");
            status.getStyleClass().add(unlocked ? "pill-unlocked" : "pill-locked");

            card.getChildren().addAll(icon, name, desc, status);
            badgeRow.getChildren().add(card);
        }

        List<MissionGenerator.Mission> missions = MissionGenerator.generateWeeklyMissions(db, userId);
        missionsXpLabel.setText("+" + (missions.size() * 100) + " XP available");
        missionBox.getChildren().clear();
        for (int i = 0; i < missions.size(); i++) {
            MissionGenerator.Mission m = missions.get(i);
            VBox row = new VBox(6);
            HBox header = new HBox(10);
            Label num = new Label(String.format("%02d", i + 1));
            num.setStyle("-fx-text-fill: #F1531F; -fx-font-weight: bold;");
            Label title = new Label(m.title);
            title.setStyle("-fx-font-weight: bold;");
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label progressLabel = new Label(String.format("\u20B9%,.0f of \u20B9%,.0f", m.progress, m.target));
            progressLabel.getStyleClass().add("muted");
            header.getChildren().addAll(num, title, sp, progressLabel);
            header.setAlignment(Pos.CENTER_LEFT);

            ProgressBar bar = new ProgressBar(m.target > 0 ? Math.min(1.0, m.progress / m.target) : 0);
            bar.setMaxWidth(Double.MAX_VALUE);

            row.getChildren().addAll(header, bar);
            missionBox.getChildren().add(row);
        }
    }
}

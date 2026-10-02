package com.financeanalyser.view;

import com.financeanalyser.controller.ForecastEngine;
import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.CategoryTotal;
import com.financeanalyser.model.DateTotal;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.util.*;

public class AnalyticsView extends VBox {

    private final DatabaseManager db;
    private final int userId;

    private Label momValue, momSub, peakValue, peakSub, adherenceValue, adherenceSub;
    private BarChart<String, Number> varianceChart;
    private Label ringPctLabel, ringSubLabel, adherenceHeadline, adherenceTargetLabel;
    private Circle ringOuter;
    private Label insightHeadline, insightSub;

    public AnalyticsView(DatabaseManager db, int userId) {
        super(16);
        this.db = db;
        this.userId = userId;
        setPadding(new Insets(4, 28, 28, 28));
        buildUi();
        refresh();
    }

    private void buildUi() {
        HBox cardsRow = new HBox(16);
        getChildren().add(cardsRow);
        buildTopCards(cardsRow);

        HBox middleRow = new HBox(16, buildVarianceCard(), buildAdherenceCard());
        HBox.setHgrow(middleRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(middleRow.getChildren().get(1), Priority.ALWAYS);
        getChildren().add(middleRow);

        getChildren().add(buildInsightBar());
    }

    private void buildTopCards(HBox cardsRow) {
        VBox momCard = new VBox(8);
        momCard.getStyleClass().add("card-blue");
        momCard.setPadding(new Insets(18));
        Label momLabel = new Label("Month-over-month");
        momLabel.getStyleClass().add("muted");
        momValue = new Label("0%");
        momValue.getStyleClass().add("stat-value");
        momSub = new Label("");
        momSub.getStyleClass().add("muted");
        momCard.getChildren().addAll(momLabel, momValue, momSub);
        HBox.setHgrow(momCard, Priority.ALWAYS);

        VBox peakCard = new VBox(8);
        peakCard.getStyleClass().add("card-orange");
        peakCard.setPadding(new Insets(18));
        Label peakLabel = new Label("Peak spending day");
        peakLabel.getStyleClass().add("muted");
        peakValue = new Label("-");
        peakValue.getStyleClass().add("stat-value");
        peakSub = new Label("");
        peakSub.getStyleClass().add("muted");
        peakCard.getChildren().addAll(peakLabel, peakValue, peakSub);
        HBox.setHgrow(peakCard, Priority.ALWAYS);

        VBox adhCard = new VBox(8);
        adhCard.getStyleClass().add("card-cream");
        adhCard.setPadding(new Insets(18));
        Label adhLabel = new Label("Budget adherence");
        adhLabel.getStyleClass().add("muted");
        adherenceValue = new Label("0%");
        adherenceValue.getStyleClass().add("stat-value");
        adherenceSub = new Label("");
        adherenceSub.getStyleClass().add("muted");
        adhCard.getChildren().addAll(adhLabel, adherenceValue, adherenceSub);
        HBox.setHgrow(adhCard, Priority.ALWAYS);

        cardsRow.getChildren().addAll(momCard, peakCard, adhCard);
    }

    private VBox buildVarianceCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-white");
        card.setPadding(new Insets(18));

        HBox top = new HBox();
        VBox titleBox = new VBox(2);
        Label eyebrow = new Label("CATEGORY VARIANCE");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("This month vs last month");
        title.getStyleClass().add("section-title");
        titleBox.getChildren().addAll(eyebrow, title);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label pill = new Label(LocalDate.now().getMonth().toString().charAt(0) + LocalDate.now().getMonth().toString().substring(1).toLowerCase());
        pill.getStyleClass().add("pill");
        top.getChildren().addAll(titleBox, sp, pill);
        top.setAlignment(Pos.CENTER_LEFT);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        varianceChart = new BarChart<>(xAxis, yAxis);
        varianceChart.setPrefHeight(260);
        varianceChart.setLegendVisible(true);

        card.getChildren().addAll(top, varianceChart);
        return card;
    }

    private VBox buildAdherenceCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-black");
        card.setPadding(new Insets(18));
        card.setPrefWidth(300);

        Label eyebrow = new Label("DAILY ADHERENCE");
        eyebrow.getStyleClass().add("stat-label");
        adherenceHeadline = new Label("On track");
        adherenceHeadline.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");
        adherenceHeadline.setWrapText(true);
        adherenceTargetLabel = new Label("");
        adherenceTargetLabel.getStyleClass().add("muted");

        StackPane ring = new StackPane();
        ring.setPrefSize(150, 150);
        ringOuter = new Circle(70);
        ringOuter.setFill(Color.web("#F1531F"));
        Circle inner = new Circle(50);
        inner.setFill(Color.web("#181818"));
        VBox centerText = new VBox(0);
        centerText.setAlignment(Pos.CENTER);
        ringPctLabel = new Label("0%");
        ringPctLabel.setStyle("-fx-text-fill: white; -fx-font-size: 24px; -fx-font-weight: bold;");
        ringSubLabel = new Label("used");
        ringSubLabel.getStyleClass().add("muted");
        centerText.getChildren().addAll(ringPctLabel, ringSubLabel);
        ring.getChildren().addAll(ringOuter, inner, centerText);
        StackPane.setAlignment(ring, Pos.CENTER);
        HBox ringWrap = new HBox(ring);
        ringWrap.setAlignment(Pos.CENTER);

        card.getChildren().addAll(eyebrow, adherenceHeadline, adherenceTargetLabel, ringWrap);
        return card;
    }

    private VBox buildInsightBar() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-cream");
        card.setPadding(new Insets(18));

        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        StackPane icon = new StackPane();
        icon.getStyleClass().add("card-blue");
        icon.setPrefSize(40, 40);
        icon.setMaxSize(40, 40);
        Label sparkle = new Label("\u2726");
        icon.getChildren().add(sparkle);

        VBox textBox = new VBox(2);
        Label eyebrow = new Label("AI INSIGHT");
        eyebrow.getStyleClass().add("block-eyebrow");
        insightHeadline = new Label("Keep an eye on your top category");
        insightHeadline.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        insightHeadline.setWrapText(true);
        insightSub = new Label("");
        insightSub.getStyleClass().add("muted");
        insightSub.setWrapText(true);
        textBox.getChildren().addAll(eyebrow, insightHeadline, insightSub);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Button createCapBtn = new Button("Create cap");
        createCapBtn.getStyleClass().add("btn-orange");
        createCapBtn.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
                "Head to the Budget planner tab to set a cap for this category.").showAndWait());

        row.getChildren().addAll(icon, textBox, createCapBtn);
        card.getChildren().add(row);
        return card;
    }

    public void refresh() {
        String[] monthBounds = ForecastEngine.monthBounds();
        String[] prevBounds = ForecastEngine.previousMonthBounds();

        double thisTotal = db.sumExpenses(userId, monthBounds[0], monthBounds[1]);
        double lastTotal = db.sumExpenses(userId, prevBounds[0], prevBounds[1]);
        Double growth = ForecastEngine.monthOverMonthGrowth(thisTotal, lastTotal);
        momValue.setText(growth != null ? String.format("%+.0f%%", growth) : "N/A");
        momSub.setText(growth != null && growth >= 0 ? "Spending increased" : "Spending decreased");

        List<DateTotal> daily = db.dailyTotals(userId, monthBounds[0], monthBounds[1]);
        if (!daily.isEmpty()) {
            DateTotal peak = daily.stream().max(Comparator.comparingDouble(DateTotal::getTotal)).get();
            peakValue.setText(LocalDate.parse(peak.getDate()).getMonth().toString().substring(0, 3) + " " + LocalDate.parse(peak.getDate()).getDayOfMonth());
            peakSub.setText(String.format("\u20B9%,.0f total", peak.getTotal()));
        } else {
            peakValue.setText("-");
            peakSub.setText("No spending yet");
        }

        double salary = db.getUser(userId).getMonthlySalary();
        int daysElapsed = LocalDate.now().getDayOfMonth();
        double expectedPace = salary > 0 ? (salary / ForecastEngine.daysInMonth(LocalDate.now())) * daysElapsed : 0;
        double adherence = thisTotal > 0 ? Math.min(expectedPace / thisTotal * 100, 999) : 100;
        adherenceValue.setText(String.format("%.0f%%", adherence));
        adherenceSub.setText(adherence < 100 ? "Above daily limit" : "Within daily limit");

        drawVariance(monthBounds, prevBounds);
        drawAdherenceRing(daily, salary, thisTotal);
        drawInsight(monthBounds);
    }

    private void drawVariance(String[] monthBounds, String[] prevBounds) {
        varianceChart.getData().clear();
        Map<String, Double> thisByCat = new LinkedHashMap<>();
        for (CategoryTotal ct : db.sumByCategory(userId, monthBounds[0], monthBounds[1], "Expense")) {
            thisByCat.put(ct.getCategory(), ct.getTotal());
        }
        Map<String, Double> prevByCat = new LinkedHashMap<>();
        for (CategoryTotal ct : db.sumByCategory(userId, prevBounds[0], prevBounds[1], "Expense")) {
            prevByCat.put(ct.getCategory(), ct.getTotal());
        }
        Set<String> categories = new TreeSet<>();
        categories.addAll(thisByCat.keySet());
        categories.addAll(prevByCat.keySet());
        if (categories.isEmpty()) return;

        XYChart.Series<String, Number> lastMonthSeries = new XYChart.Series<>();
        lastMonthSeries.setName("Last month");
        XYChart.Series<String, Number> thisMonthSeries = new XYChart.Series<>();
        thisMonthSeries.setName("This month");
        for (String cat : categories) {
            lastMonthSeries.getData().add(new XYChart.Data<>(cat, prevByCat.getOrDefault(cat, 0.0)));
            thisMonthSeries.getData().add(new XYChart.Data<>(cat, thisByCat.getOrDefault(cat, 0.0)));
        }
        varianceChart.getData().addAll(thisMonthSeries, lastMonthSeries);
    }

    private void drawAdherenceRing(List<DateTotal> daily, double salary, double thisTotal) {
        if (daily.isEmpty() || salary <= 0) {
            adherenceHeadline.setText("No data yet");
            adherenceTargetLabel.setText(salary <= 0 ? "Set a salary to see adherence" : "No expenses logged yet this month");
            ringPctLabel.setText("0%");
            ringOuter.setFill(Color.web("#AECBFA"));
            return;
        }
        double dailyTarget = salary / ForecastEngine.daysInMonth(LocalDate.now());
        int daysElapsed = LocalDate.now().getDayOfMonth();
        double expectedSoFar = dailyTarget * daysElapsed;
        double usedPct = expectedSoFar > 0 ? thisTotal / expectedSoFar * 100 : 0;
        double over = thisTotal - expectedSoFar;

        ringPctLabel.setText(String.format("%.0f%%", usedPct));
        ringOuter.setFill(usedPct > 100 ? Color.web("#F1531F") : Color.web("#AECBFA"));
        adherenceTargetLabel.setText(String.format("Daily Limit: \u20B9%,.0f", dailyTarget));
        if (over > 0) {
            adherenceHeadline.setText(String.format("You're \u20B9%,.0f over target", over));
        } else {
            adherenceHeadline.setText(String.format("You're \u20B9%,.0f under target", -over));
        }
    }

    private void drawInsight(String[] monthBounds) {
        List<CategoryTotal> byCat = db.sumByCategory(userId, monthBounds[0], monthBounds[1], "Expense");
        if (byCat.isEmpty()) {
            insightHeadline.setText("Log a few expenses to unlock insights");
            insightSub.setText("");
            return;
        }
        double total = byCat.stream().mapToDouble(CategoryTotal::getTotal).sum();
        CategoryTotal top = byCat.get(0);
        double pct = total > 0 ? top.getTotal() / total * 100 : 0;
        insightHeadline.setText(String.format("%s drove %.0f%% of your spend", top.getCategory(), pct));
        insightSub.setText("Set a weekly cap on this category to keep your savings goal on track.");
    }
}

package com.financeanalyser.view;

import com.financeanalyser.controller.ForecastEngine;
import com.financeanalyser.controller.GamificationEngine;
import com.financeanalyser.controller.MissionGenerator;
import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.CategoryTotal;
import com.financeanalyser.model.DateTotal;
import com.financeanalyser.model.GamificationState;
import com.financeanalyser.model.SalarySegmentation;
import com.financeanalyser.model.Transaction;
import com.financeanalyser.model.User;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class DashboardView extends VBox {

    private static final String[] CATEGORIES = {"Food", "Travel", "Utilities", "Subscriptions", "Shopping", "Training", "Misc"};
    private static final String[] PAYMENT_METHODS = {"UPI", "Card", "Cash"};

    private final DatabaseManager db;
    private final int userId;
    private final Runnable onTransactionAdded;

    private Label availableValue, monthlyIncomeValue, spentSoFarValue;
    private Label pulseValue, pulsePill;
    private HBox weekBarsRow;
    private Label streakBig, streakSub;
    private DatePicker dateInput;
    private Spinner<Double> amountInput;
    private ComboBox<String> categoryInput, paymentInput;
    private TextField notesInput;
    private Label planHeadline;
    private PieChart segChart;
    private Label savedCenterLabel;
    private VBox segLegend;
    private VBox recentActivityBox;
    private Label missionHeadline, missionSub, missionAmountLabel;
    private ProgressBar missionBar;

    public DashboardView(DatabaseManager db, int userId, Runnable onTransactionAdded) {
        super(16);
        this.db = db;
        this.userId = userId;
        this.onTransactionAdded = onTransactionAdded;
        setPadding(new Insets(4, 28, 28, 28));
        buildUi();
        refresh();
    }

    private void buildUi() {
        HBox row1 = new HBox(16, buildBalanceCard(), buildPulseCard(), buildStreakCard());
        for (var n : row1.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        getChildren().add(row1);

        HBox row2 = new HBox(16, buildQuickActionBlock(), buildSegmentationCard());
        HBox.setHgrow(row2.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(row2.getChildren().get(1), Priority.ALWAYS);
        getChildren().add(row2);

        HBox row3 = new HBox(16, buildActivityCard(), buildMissionCard());
        HBox.setHgrow(row3.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(row3.getChildren().get(1), Priority.ALWAYS);
        getChildren().add(row3);
    }

    // --------------------------------------------------------------- row 1
    private VBox buildBalanceCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-black");
        card.setPadding(new Insets(18));

        Label pill = new Label(LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " balance");
        pill.getStyleClass().add("pill-dark");

        Label availLabel = new Label("Available to spend");
        availLabel.getStyleClass().add("muted");
        availableValue = new Label("\u20B90");
        availableValue.getStyleClass().add("stat-value");
        availableValue.setStyle("-fx-text-fill: white; -fx-font-size: 34px;");

        HBox subRow = new HBox(28);
        VBox incomeBox = new VBox(2);
        Label incomeLabel = new Label("Monthly income");
        incomeLabel.getStyleClass().add("muted");
        monthlyIncomeValue = new Label("\u20B90");
        monthlyIncomeValue.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        incomeBox.getChildren().addAll(incomeLabel, monthlyIncomeValue);

        VBox spentBox = new VBox(2);
        Label spentLabel = new Label("Spent so far");
        spentLabel.getStyleClass().add("muted");
        spentSoFarValue = new Label("\u20B90");
        spentSoFarValue.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        spentBox.getChildren().addAll(spentLabel, spentSoFarValue);

        subRow.getChildren().addAll(incomeBox, spentBox);
        card.getChildren().addAll(pill, availLabel, availableValue, subRow);
        return card;
    }

    private VBox buildPulseCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-orange");
        card.setPadding(new Insets(18));

        HBox top = new HBox();
        Label label = new Label("SPENDING PULSE");
        label.getStyleClass().add("stat-label");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        pulsePill = new Label("0% used");
        pulsePill.getStyleClass().add("pill");
        top.getChildren().addAll(label, sp, pulsePill);

        pulseValue = new Label("\u20B90");
        pulseValue.getStyleClass().add("stat-value");

        weekBarsRow = new HBox(8);
        weekBarsRow.setAlignment(Pos.BOTTOM_CENTER);
        weekBarsRow.setPrefHeight(70);

        card.getChildren().addAll(top, pulseValue, weekBarsRow);
        return card;
    }

    private VBox buildStreakCard() {
        VBox card = new VBox(6);
        card.getStyleClass().add("card-blue");
        card.setPadding(new Insets(18));
        card.setAlignment(Pos.TOP_LEFT);

        streakBig = new Label("0");
        streakBig.setStyle("-fx-font-size: 46px; -fx-font-weight: bold; -fx-text-fill: #141414;");

        Region sp = new Region();
        VBox.setVgrow(sp, Priority.ALWAYS);

        Label label = new Label("CURRENT STREAK");
        label.getStyleClass().add("stat-label");
        streakSub = new Label("");
        streakSub.getStyleClass().add("muted");
        streakSub.setWrapText(true);

        card.getChildren().addAll(streakBig, sp, label, streakSub);
        return card;
    }

    // --------------------------------------------------------------- row 2
    private VBox buildQuickActionBlock() {
        VBox block = new VBox(12);
        block.setPadding(new Insets(4, 0, 0, 0));

        HBox top = new HBox();
        VBox titleBox = new VBox(2);
        Label eyebrow = new Label("QUICK ACTION");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("Add an expense");
        title.getStyleClass().add("block-title");
        titleBox.getChildren().addAll(eyebrow, title);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button plusBtn = new Button("+");
        plusBtn.getStyleClass().add("btn-orange");
        plusBtn.setOnAction(e -> submitExpense());
        top.getChildren().addAll(titleBox, sp, plusBtn);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(10);
        form.getStyleClass().add("card-white");
        form.setPadding(new Insets(18));

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        amountInput = new Spinner<>(0.0, 10_000_000.0, 0.0, 10.0);
        amountInput.setEditable(true);
        amountInput.setPrefWidth(140);
        dateInput = new DatePicker(LocalDate.now());
        categoryInput = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES));
        categoryInput.getSelectionModel().selectFirst();
        paymentInput = new ComboBox<>(FXCollections.observableArrayList(PAYMENT_METHODS));
        paymentInput.getSelectionModel().selectFirst();
        notesInput = new TextField();
        notesInput.setPromptText("What was it for?");

        Label amountLbl = new Label("Amount"); amountLbl.getStyleClass().add("muted");
        Label dateLbl = new Label("Date"); dateLbl.getStyleClass().add("muted");
        Label catLbl = new Label("Category"); catLbl.getStyleClass().add("muted");
        Label payLbl = new Label("Payment"); payLbl.getStyleClass().add("muted");
        Label noteLbl = new Label("Note"); noteLbl.getStyleClass().add("muted");

        grid.addRow(0, amountLbl, dateLbl);
        grid.addRow(1, amountInput, dateInput);
        grid.addRow(2, catLbl, payLbl);
        grid.addRow(3, categoryInput, paymentInput);
        grid.add(noteLbl, 0, 4, 2, 1);
        grid.add(notesInput, 0, 5, 2, 1);

        Button addBtn = new Button("Add expense  \u2192");
        addBtn.getStyleClass().add("btn-orange");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(e -> submitExpense());
        amountInput.getEditor().setOnAction(e -> submitExpense());
        notesInput.setOnAction(e -> submitExpense());

        form.getChildren().addAll(grid, addBtn);
        block.getChildren().addAll(top, form);
        return block;
    }

    private VBox buildSegmentationCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card-cream");
        card.setPadding(new Insets(18));

        HBox top = new HBox();
        VBox titleBox = new VBox(2);
        Label eyebrow = new Label("SALARY SEGMENTATION");
        eyebrow.getStyleClass().add("block-eyebrow");
        planHeadline = new Label("Your plan");
        planHeadline.getStyleClass().add("block-title");
        titleBox.getChildren().addAll(eyebrow, planHeadline);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        top.getChildren().addAll(titleBox, sp);

        HBox content = new HBox(18);
        content.setAlignment(Pos.CENTER_LEFT);

        StackPane donutWrap = new StackPane();
        segChart = new PieChart();
        segChart.setLegendVisible(false);
        segChart.setLabelsVisible(false);
        segChart.setPrefSize(150, 150);
        segChart.setMaxSize(150, 150);
        savedCenterLabel = new Label("Saved\n0%");
        savedCenterLabel.setStyle("-fx-font-weight: bold; -fx-text-alignment: center; -fx-alignment: center;");
        savedCenterLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        donutWrap.getChildren().addAll(segChart, savedCenterLabel);

        segLegend = new VBox(10);
        HBox.setHgrow(segLegend, Priority.ALWAYS);

        content.getChildren().addAll(donutWrap, segLegend);
        card.getChildren().addAll(top, content);
        return card;
    }

    // --------------------------------------------------------------- row 3
    private VBox buildActivityCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-white");
        card.setPadding(new Insets(18));

        HBox top = new HBox();
        VBox titleBox = new VBox(2);
        Label eyebrow = new Label("LATEST ACTIVITY");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("Recent transactions");
        title.getStyleClass().add("section-title");
        titleBox.getChildren().addAll(eyebrow, title);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        top.getChildren().addAll(titleBox, sp);

        recentActivityBox = new VBox(10);
        card.getChildren().addAll(top, recentActivityBox);
        return card;
    }

    private VBox buildMissionCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-black");
        card.setPadding(new Insets(18));

        Label pill = new Label("Weekly mission");
        pill.getStyleClass().add("pill-dark");
        missionHeadline = new Label("Keep spending in check");
        missionHeadline.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");
        missionHeadline.setWrapText(true);
        missionSub = new Label("");
        missionSub.getStyleClass().add("muted");
        missionSub.setWrapText(true);

        missionAmountLabel = new Label("\u20B90");
        missionAmountLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        missionBar = new ProgressBar(0);
        missionBar.setMaxWidth(Double.MAX_VALUE);

        card.getChildren().addAll(pill, missionHeadline, missionSub, missionAmountLabel, missionBar);
        return card;
    }

    // ----------------------------------------------------------------- data
    private void submitExpense() {
        amountInput.increment(0);
        double amount = amountInput.getValue();
        if (amount <= 0) return;
        String tdate = dateInput.getValue().toString();
        db.addTransaction(userId, amount, categoryInput.getValue(), "Expense",
                paymentInput.getValue(), tdate, notesInput.getText());
        amountInput.getValueFactory().setValue(0.0);
        notesInput.clear();

        double salary = db.getUser(userId).getMonthlySalary();
        double allowance = ForecastEngine.todayAllowance(salary);
        double spentToday = db.sumExpenses(userId, LocalDate.now().toString(), LocalDate.now().toString());
        GamificationEngine.logTransactionGamification(db, userId, allowance, spentToday);

        refresh();
        if (onTransactionAdded != null) onTransactionAdded.run();
    }

    /** Focuses the Amount field (wired to the global Ctrl+N accelerator). */
    public void requestAmountFocus() {
        amountInput.getEditor().requestFocus();
        amountInput.getEditor().selectAll();
    }

    public void refresh() {
        User user = db.getUser(userId);
        double salary = user.getMonthlySalary();
        String todayIso = LocalDate.now().toString();
        String[] monthBounds = ForecastEngine.monthBounds();
        double spentMonth = db.sumExpenses(userId, monthBounds[0], monthBounds[1]);
        double available = salary - spentMonth;

        availableValue.setText(String.format("\u20B9%,.0f", available));
        monthlyIncomeValue.setText(String.format("\u20B9%,.0f", salary));
        spentSoFarValue.setText(String.format("\u20B9%,.0f", spentMonth));

        pulseValue.setText(String.format("\u20B9%,.0f", spentMonth));
        int usedPct = salary > 0 ? (int) Math.round(spentMonth / salary * 100) : 0;
        pulsePill.setText(usedPct + "% used");
        drawWeekBars();

        GamificationState g = db.getGamification(userId);
        streakBig.setText(String.valueOf(g.getCurrentStreak()));
        double spentToday = db.sumExpenses(userId, todayIso, todayIso);
        streakSub.setText(spentToday > 0 ? "Nice - you've logged today. Keep it going!" : "Log something today to keep it alive.");

        SalarySegmentation seg = db.latestSegmentation(userId);
        planHeadline.setText(seg != null ? String.format("Your \u20B9%,.0fk game plan", seg.getTotalIncome() / 1000.0) : "Set up your plan");
        drawSegmentation(seg);

        drawRecentActivity();
        drawMission();
    }

    private void drawWeekBars() {
        weekBarsRow.getChildren().clear();
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        Map<LocalDate, Double> byDay = new TreeMap<>();
        for (int i = 0; i < 7; i++) byDay.put(monday.plusDays(i), 0.0);
        List<DateTotal> daily = db.dailyTotals(userId, monday.toString(), monday.plusDays(6).toString());
        for (DateTotal dt : daily) {
            LocalDate d = LocalDate.parse(dt.getDate());
            byDay.put(d, dt.getTotal());
        }
        double max = byDay.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max <= 0) max = 1;
        for (Map.Entry<LocalDate, Double> e : byDay.entrySet()) {
            boolean hasSpend = e.getValue() > 0;
            double h = Math.max(6, (e.getValue() / max) * 56);
            Rectangle bar = new Rectangle(14, h);
            bar.setArcWidth(8);
            bar.setArcHeight(8);
            bar.setFill(hasSpend ? Color.web("#AECBFA") : Color.web("rgba(255,255,255,0.55)"));
            VBox col = new VBox(4);
            col.setAlignment(Pos.BOTTOM_CENTER);
            Label dayLbl = new Label(e.getKey().getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.ENGLISH));
            dayLbl.getStyleClass().add("muted");
            col.getChildren().addAll(bar, dayLbl);
            weekBarsRow.getChildren().add(col);
        }
    }

    private void drawSegmentation(SalarySegmentation seg) {
        segLegend.getChildren().clear();
        if (seg == null || seg.getTotalIncome() <= 0) {
            segChart.setData(FXCollections.observableArrayList());
            savedCenterLabel.setText("No plan\nyet");
            Label hint = new Label("Set a salary and strategy below to see your plan here.");
            hint.getStyleClass().add("muted");
            hint.setWrapText(true);
            segLegend.getChildren().add(hint);
            return;
        }
        double savedPct = seg.getSavingsCap() / seg.getTotalIncome() * 100;
        savedCenterLabel.setText(String.format("Saved\n%.0f%%", savedPct));

        var data = FXCollections.observableArrayList(
                new PieChart.Data("Groceries", seg.getGroceriesCap()),
                new PieChart.Data("Entertainment", seg.getEntertainmentCap()),
                new PieChart.Data("Training", seg.getTrainingCap()),
                new PieChart.Data("Savings", seg.getSavingsCap())
        );
        segChart.setData(data);

        String[] colors = {"#AECBFA", "#F1531F", "#ECE0C6", "#181818"};
        String[] labels = {"Groceries", "Entertainment", "Training", "Savings"};
        double[] values = {seg.getGroceriesCap(), seg.getEntertainmentCap(), seg.getTrainingCap(), seg.getSavingsCap()};
        for (int i = 0; i < labels.length; i++) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            Rectangle dot = new Rectangle(8, 8);
            dot.setArcWidth(8); dot.setArcHeight(8);
            dot.setFill(Color.web(colors[i]));
            Label name = new Label(labels[i]);
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            double pct = seg.getTotalIncome() > 0 ? values[i] / seg.getTotalIncome() * 100 : 0;
            Label pctLabel = new Label(String.format("%.0f%%", pct));
            pctLabel.setStyle("-fx-font-weight: bold;");
            row.getChildren().addAll(dot, name, sp, pctLabel);
            segLegend.getChildren().add(row);
        }
    }

    private void drawRecentActivity() {
        recentActivityBox.getChildren().clear();
        List<Transaction> rows = db.getTransactions(userId, null, null, "All", null);
        int limit = Math.min(4, rows.size());
        if (limit == 0) {
            Label empty = new Label("No transactions logged yet.");
            empty.getStyleClass().add("muted");
            recentActivityBox.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < limit; i++) {
            Transaction t = rows.get(i);
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);

            StackPane avatar = new StackPane();
            avatar.getStyleClass().add("card-blue");
            avatar.setPrefSize(32, 32);
            avatar.setMaxSize(32, 32);
            Label letter = new Label(t.getCategory().substring(0, 1).toUpperCase());
            letter.setStyle("-fx-font-weight: bold;");
            avatar.getChildren().add(letter);

            VBox textBox = new VBox(1);
            String desc = (t.getNotes() != null && !t.getNotes().isBlank()) ? t.getNotes() : t.getCategory();
            Label descLbl = new Label(desc);
            descLbl.setStyle("-fx-font-weight: bold;");
            Label subLbl = new Label(t.getCategory() + " \u00B7 " + t.getPaymentMethod());
            subLbl.getStyleClass().add("muted");
            textBox.getChildren().addAll(descLbl, subLbl);

            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            String sign = "Income".equals(t.getTransactionType()) ? "+" : "-";
            Label amountLbl = new Label(String.format("%s\u20B9%,.0f", sign, t.getAmount()));
            amountLbl.setStyle("-fx-font-weight: bold;");

            row.getChildren().addAll(avatar, textBox, sp, amountLbl);
            recentActivityBox.getChildren().add(row);
        }
    }

    private void drawMission() {
        List<MissionGenerator.Mission> missions = MissionGenerator.generateWeeklyMissions(db, userId);
        if (missions.isEmpty()) return;
        MissionGenerator.Mission top = missions.get(0);
        missionHeadline.setText(top.title);
        missionSub.setText(String.format("Stay under \u20B9%,.0f this week.", top.target));
        missionAmountLabel.setText(String.format("\u20B9%,.0f of \u20B9%,.0f", top.progress, top.target));
        missionBar.setProgress(top.target > 0 ? Math.min(1.0, top.progress / top.target) : 0);
    }
}

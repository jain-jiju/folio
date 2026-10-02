package com.financeanalyser.view;

import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.SalarySegmentation;
import com.financeanalyser.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class PlannerView extends VBox {

    private final DatabaseManager db;
    private final int userId;

    private Spinner<Double> incomeInput;
    private final Map<String, Slider> sliders = new LinkedHashMap<>();
    private final Map<String, Label> sliderValueLabels = new LinkedHashMap<>();
    private final Map<String, Label> capValueLabels = new LinkedHashMap<>();
    private final Map<String, Label> capPctLabels = new LinkedHashMap<>();
    private Label totalPctLabel;

    private static final String[] KEYS = {"groceries", "entertainment", "training", "savings"};
    private static final String[] TITLES = {"Groceries", "Entertainment", "Training", "Savings"};
    private static final String[] SUBTITLES = {"Kitchen & essentials", "Fun without guilt", "Invest in yourself", "Future you fund"};
    private static final String[] CARD_STYLES = {"card-blue", "card-orange", "card-cream", "card-black"};
    private static final String[] ICON_LETTERS = {"G", "E", "T", "S"};

    public PlannerView(DatabaseManager db, int userId) {
        super(16);
        this.db = db;
        this.userId = userId;
        setPadding(new Insets(4, 28, 28, 28));
        buildUi();
        refresh();
    }

    private void buildUi() {
        HBox top = new HBox(16, buildGuidedCard(), buildSliderCard());
        HBox.setHgrow(top.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);
        getChildren().add(top);

        HBox capsRow = new HBox(16);
        for (int i = 0; i < KEYS.length; i++) {
            VBox cap = buildCapCard(i);
            HBox.setHgrow(cap, Priority.ALWAYS);
            capsRow.getChildren().add(cap);
        }
        getChildren().add(capsRow);

        getChildren().add(buildCeilingsBar());
    }

    private VBox buildGuidedCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-black");
        card.setPadding(new Insets(22));
        card.setPrefWidth(320);

        Label pill = new Label("AI guided");
        pill.getStyleClass().add("pill-dark");
        Label headline = new Label("Give every rupee a job.");
        headline.setStyle("-fx-text-fill: white; -fx-font-size: 26px; -fx-font-weight: bold;");
        headline.setWrapText(true);
        Label desc = new Label("Shape a practical monthly plan, then save it as your active spending guardrail.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);

        Region sp = new Region();
        VBox.setVgrow(sp, Priority.ALWAYS);
        Label incomeLabel = new Label("Monthly income");
        incomeLabel.getStyleClass().add("muted");
        Label incomeBig = new Label("\u20B90");
        incomeBig.setStyle("-fx-text-fill: white; -fx-font-size: 28px; -fx-font-weight: bold;");
        incomeBigRef = incomeBig;

        card.getChildren().addAll(pill, headline, desc, sp, incomeLabel, incomeBig);
        return card;
    }
    private Label incomeBigRef;

    private VBox buildSliderCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-white");
        card.setPadding(new Insets(22));

        HBox top = new HBox();
        VBox titleBox = new VBox(2);
        Label eyebrow = new Label("SALARY SEGMENTATION");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("Monthly split");
        title.getStyleClass().add("section-title");
        titleBox.getChildren().addAll(eyebrow, title);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        totalPctLabel = new Label("0% allocated");
        totalPctLabel.getStyleClass().add("pill");
        top.getChildren().addAll(titleBox, sp, totalPctLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        for (int i = 0; i < KEYS.length; i++) {
            String key = KEYS[i];
            StackPane icon = new StackPane();
            icon.getStyleClass().add(CARD_STYLES[i]);
            icon.setPrefSize(34, 34);
            icon.setMaxSize(34, 34);
            icon.getStyleClass().add("sidebar-logo-box"); // reuse rounded-square shape
            Label letter = new Label(ICON_LETTERS[i]);
            letter.setStyle("-fx-font-weight: bold;");
            icon.getChildren().add(letter);

            VBox labelBox = new VBox(0);
            Label name = new Label(TITLES[i]);
            name.setStyle("-fx-font-weight: bold;");
            Label sub = new Label(SUBTITLES[i]);
            sub.getStyleClass().add("muted");
            labelBox.getChildren().addAll(name, sub);

            Slider slider = new Slider(0, 100, 25);
            slider.setPrefWidth(220);
            Label valueLabel = new Label("25%");
            valueLabel.setStyle("-fx-font-weight: bold;");
            slider.valueProperty().addListener((o, ov, nv) -> {
                valueLabel.setText(nv.intValue() + "%");
                recomputePreview();
            });
            sliders.put(key, slider);
            sliderValueLabels.put(key, valueLabel);

            grid.addRow(i, icon, labelBox, slider, valueLabel);
        }

        Button saveBtn = new Button("Save active budget  \u2192");
        saveBtn.getStyleClass().add("btn-orange");
        saveBtn.setOnAction(e -> saveBudget());
        HBox saveRow = new HBox(saveBtn);
        saveRow.setAlignment(Pos.CENTER_RIGHT);

        incomeInput = new Spinner<>(0.0, 10_000_000.0, 25000.0, 500.0);
        incomeInput.setEditable(true);
        incomeInput.valueProperty().addListener((o, ov, nv) -> recomputePreview());
        HBox incomeRow = new HBox(10, new Label("Simulated income:"), incomeInput);
        incomeRow.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(top, incomeRow, grid, saveRow);
        return card;
    }

    private VBox buildCapCard(int i) {
        VBox card = new VBox(6);
        card.getStyleClass().add(CARD_STYLES[i]);
        card.setPadding(new Insets(18));

        Label pct = new Label("0%");
        pct.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        if ("card-black".equals(CARD_STYLES[i])) pct.setStyle(pct.getStyle() + " -fx-text-fill: white;");
        capPctLabels.put(KEYS[i], pct);

        Region sp = new Region();
        VBox.setVgrow(sp, Priority.ALWAYS);

        Label label = new Label(TITLES[i].toUpperCase() + " CAP");
        label.getStyleClass().add("stat-label");
        Label value = new Label("\u20B90");
        value.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        if ("card-black".equals(CARD_STYLES[i])) value.setStyle(value.getStyle() + " -fx-text-fill: white;");
        capValueLabels.put(KEYS[i], value);

        card.getChildren().addAll(pct, sp, label, value);
        return card;
    }

    private HBox buildCeilingsBar() {
        HBox bar = new HBox(14);
        bar.getStyleClass().add("card-white");
        bar.setPadding(new Insets(18));
        bar.setAlignment(Pos.CENTER_LEFT);

        StackPane lockIcon = new StackPane();
        lockIcon.getStyleClass().add("card-black");
        lockIcon.setPrefSize(40, 40);
        lockIcon.setMaxSize(40, 40);
        Label lock = new Label("\uD83D\uDD12");
        lockIcon.getChildren().add(lock);

        VBox textBox = new VBox(2);
        Label title = new Label("Hard monthly ceiling limits");
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        Label desc = new Label("Spending above category caps triggers an amber alert and pauses your Budget Master badge.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);
        textBox.getChildren().addAll(title, desc);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        bar.getChildren().addAll(lockIcon, textBox);
        return bar;
    }

    public void refresh() {
        SalarySegmentation seg = db.latestSegmentation(userId);
        User user = db.getUser(userId);
        double income = (seg != null ? seg.getTotalIncome() : user.getMonthlySalary());
        if (income <= 0) income = 25000;
        incomeInput.getValueFactory().setValue(income);
        incomeBigRef.setText(String.format("\u20B9%,.0f", user.getMonthlySalary()));

        if (seg != null && seg.getTotalIncome() > 0) {
            setSlider("groceries", seg.getGroceriesCap() / seg.getTotalIncome() * 100);
            setSlider("entertainment", seg.getEntertainmentCap() / seg.getTotalIncome() * 100);
            setSlider("training", seg.getTrainingCap() / seg.getTotalIncome() * 100);
            setSlider("savings", seg.getSavingsCap() / seg.getTotalIncome() * 100);
        } else {
            for (String key : KEYS) setSlider(key, 25);
        }
        recomputePreview();
    }

    private void setSlider(String key, double pct) {
        sliders.get(key).setValue(Math.round(pct));
    }

    private void recomputePreview() {
        int totalPct = sliders.values().stream().mapToInt(s -> (int) Math.round(s.getValue())).sum();
        totalPctLabel.setText(totalPct + "% allocated");
        double income = incomeInput.getValue();
        for (String key : KEYS) {
            double cap = income * sliders.get(key).getValue() / 100.0;
            capValueLabels.get(key).setText(String.format("\u20B9%,.0f", cap));
            capPctLabels.get(key).setText(Math.round(sliders.get(key).getValue()) + "%");
        }
    }

    private void saveBudget() {
        int totalPct = sliders.values().stream().mapToInt(s -> (int) Math.round(s.getValue())).sum();
        if (totalPct != 100) {
            totalPctLabel.setText("\u26A0 " + totalPct + "% - must equal 100%");
            return;
        }
        incomeInput.increment(0);
        double income = incomeInput.getValue();
        double groceries = income * sliders.get("groceries").getValue() / 100.0;
        double entertainment = income * sliders.get("entertainment").getValue() / 100.0;
        double training = income * sliders.get("training").getValue() / 100.0;
        double savings = income * sliders.get("savings").getValue() / 100.0;
        db.saveSegmentation(userId, income, "Custom", groceries, entertainment, training, savings);
        refresh();
    }
}

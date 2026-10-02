package com.financeanalyser.view;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** A small glass-styled stat card: a title, a big value, and an optional subtitle. */
public class GlassCard extends VBox {
    private final Label titleLabel;
    private final Label valueLabel;
    private final Label subtitleLabel;

    public GlassCard(String title, String value) {
        this(title, value, null);
    }

    public GlassCard(String title, String value, String subtitle) {
        super(4);
        getStyleClass().add("glass-card");
        setMinHeight(100);
        setFillWidth(true);

        titleLabel = new Label(title.toUpperCase());
        titleLabel.getStyleClass().add("card-title");

        valueLabel = new Label(value);
        valueLabel.getStyleClass().add("card-value");

        getChildren().addAll(titleLabel, valueLabel);

        subtitleLabel = new Label(subtitle == null ? "" : subtitle);
        subtitleLabel.getStyleClass().add("muted");
        if (subtitle != null) {
            getChildren().add(subtitleLabel);
        }
        VBox.setVgrow(this, Priority.ALWAYS);
    }

    public void setValue(String value) { valueLabel.setText(value); }
    public void setTitle(String title) { titleLabel.setText(title.toUpperCase()); }

    /** Swap the status style (green/amber/red/default) used for budget-status cards. */
    public void setStatusStyle(String status) {
        getStyleClass().removeAll("glass-card-green", "glass-card-amber", "glass-card-red");
        switch (status) {
            case "green" -> getStyleClass().add("glass-card-green");
            case "amber" -> getStyleClass().add("glass-card-amber");
            case "red" -> getStyleClass().add("glass-card-red");
            default -> { /* keep base .glass-card look */ }
        }
    }
}

package com.financeanalyser.view;

import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.CategoryTotal;
import com.financeanalyser.model.Transaction;
import com.financeanalyser.model.User;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

public class ReportsView extends VBox {

    private final DatabaseManager db;
    private final int userId;
    private DatePicker startDate, endDate;

    public ReportsView(DatabaseManager db, int userId) {
        super(16);
        this.db = db;
        this.userId = userId;
        setPadding(new Insets(4, 28, 28, 28));
        buildUi();
    }

    private void buildUi() {
        HBox topRow = new HBox(16, buildHeroCard(), buildBuildReportCard());
        HBox.setHgrow(topRow.getChildren().get(0), Priority.ALWAYS);
        HBox.setHgrow(topRow.getChildren().get(1), Priority.ALWAYS);
        getChildren().add(topRow);
        getChildren().add(buildIncludesCard());
    }

    private VBox buildHeroCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-orange");
        card.setPadding(new Insets(22));

        Label pill = new Label(LocalDate.now().getMonth().toString().charAt(0)
                + LocalDate.now().getMonth().toString().substring(1).toLowerCase() + " report");
        pill.getStyleClass().add("pill");

        Label headline = new Label("Your money story,\nready to share.");
        headline.setStyle("-fx-font-size: 30px; -fx-font-weight: bold; -fx-text-fill: #141414;");

        Label desc = new Label("Generate a polished financial statement or export raw data for your own records.");
        desc.getStyleClass().add("muted");
        desc.setWrapText(true);

        HBox swatches = new HBox(10);
        String[] styles = {"card-white", "card-blue", "card-white", "card-black"};
        for (String s : styles) {
            Region block = new Region();
            block.getStyleClass().add(s);
            block.setPrefSize(60, 60);
            swatches.getChildren().add(block);
        }

        card.getChildren().addAll(pill, headline, desc, swatches);
        return card;
    }

    private VBox buildBuildReportCard() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-cream");
        card.setPadding(new Insets(22));
        card.setPrefWidth(300);

        Label eyebrow = new Label("CUSTOM RANGE");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("Build your report");
        title.getStyleClass().add("block-title");

        LocalDate now = LocalDate.now();
        startDate = new DatePicker(now.withDayOfMonth(1));
        endDate = new DatePicker(now);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        Label fromLbl = new Label("From"); fromLbl.getStyleClass().add("muted");
        Label toLbl = new Label("To"); toLbl.getStyleClass().add("muted");
        grid.addRow(0, fromLbl, toLbl);
        grid.addRow(1, startDate, endDate);

        Button pdfBtn = new Button("\u2193  Export PDF statement");
        pdfBtn.getStyleClass().add("btn-orange");
        pdfBtn.setMaxWidth(Double.MAX_VALUE);
        pdfBtn.setOnAction(e -> exportPdf());
        Button csvBtn = new Button("\u2193  Export CSV summary");
        csvBtn.getStyleClass().add("btn-black");
        csvBtn.setMaxWidth(Double.MAX_VALUE);
        csvBtn.setOnAction(e -> exportCsvSummary());

        card.getChildren().addAll(eyebrow, title, grid, pdfBtn, csvBtn);
        return card;
    }

    private VBox buildIncludesCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-white");
        card.setPadding(new Insets(18));
        Label eyebrow = new Label("REPORT INCLUDES");
        eyebrow.getStyleClass().add("block-eyebrow");
        card.getChildren().add(eyebrow);

        String[] items = {
                "Income & balance summary", "Category breakdown",
                "Complete transaction ledger", "Budget adherence notes"
        };
        for (int i = 0; i < items.length; i++) {
            HBox row = new HBox(14);
            row.setAlignment(Pos.CENTER_LEFT);
            Label num = new Label(String.format("%02d", i + 1));
            num.setStyle("-fx-text-fill: #F1531F; -fx-font-weight: bold; -fx-font-size: 14px;");
            num.setMinWidth(28);
            Label text = new Label(items[i]);
            text.setStyle("-fx-font-weight: bold;");
            row.getChildren().addAll(num, text);
            card.getChildren().add(row);
        }
        return card;
    }

    private String[] range() {
        return new String[]{startDate.getValue().toString(), endDate.getValue().toString()};
    }

    private void exportCsvSummary() {
        String[] range = range();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export CSV Summary");
        chooser.setInitialFileName("finance_summary.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        List<CategoryTotal> byCat = db.sumByCategory(userId, range[0], range[1], "Expense");
        double total = db.sumExpenses(userId, range[0], range[1]);
        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            w.write("Personal Finance Analyser - Summary Report"); w.newLine();
            w.write("Period," + range[0] + " to " + range[1]); w.newLine();
            w.newLine();
            w.write("Category,Total Spent (Rs)"); w.newLine();
            for (CategoryTotal ct : byCat) {
                w.write(ct.getCategory() + "," + String.format("%.2f", ct.getTotal()));
                w.newLine();
            }
            w.newLine();
            w.write("Total Expenses," + String.format("%.2f", total)); w.newLine();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Failed to write file: " + e.getMessage()).showAndWait();
            return;
        }
        new Alert(Alert.AlertType.INFORMATION, "Saved to " + file.getAbsolutePath()).showAndWait();
    }

    private void exportPdf() {
        String[] range = range();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PDF Statement");
        chooser.setInitialFileName("finance_statement.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        User user = db.getUser(userId);
        List<CategoryTotal> byCat = db.sumByCategory(userId, range[0], range[1], "Expense");
        double total = db.sumExpenses(userId, range[0], range[1]);
        List<Transaction> rows = db.getTransactions(userId, range[0], range[1], "All", null);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(doc, page);
            float margin = 56.7f; // ~20mm
            float y = page.getMediaBox().getHeight() - margin;

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
            cs.newLineAtOffset(margin, y);
            cs.showText("Personal Finance Analyser - Statement");
            cs.endText();
            y -= 24;

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 10);
            cs.newLineAtOffset(margin, y);
            cs.showText("Name: " + user.getUsername());
            cs.endText();
            y -= 16;

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA, 10);
            cs.newLineAtOffset(margin, y);
            cs.showText("Period: " + range[0] + " to " + range[1] + "    Generated: " + LocalDate.now());
            cs.endText();
            y -= 28;

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
            cs.newLineAtOffset(margin, y);
            cs.showText("Category Breakdown");
            cs.endText();
            y -= 20;

            cs.setFont(PDType1Font.HELVETICA, 10);
            for (CategoryTotal ct : byCat) {
                cs.beginText();
                cs.newLineAtOffset(margin + 6, y);
                cs.showText(ct.getCategory());
                cs.endText();
                cs.beginText();
                cs.newLineAtOffset(page.getMediaBox().getWidth() - margin - 80, y);
                cs.showText(String.format("Rs %.2f", ct.getTotal()));
                cs.endText();
                y -= 17;
            }
            y -= 8;
            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
            cs.newLineAtOffset(margin, y);
            cs.showText("Total Expenses");
            cs.endText();
            cs.beginText();
            cs.newLineAtOffset(page.getMediaBox().getWidth() - margin - 80, y);
            cs.showText(String.format("Rs %.2f", total));
            cs.endText();
            y -= 34;

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
            cs.newLineAtOffset(margin, y);
            cs.showText("Transaction Log");
            cs.endText();
            y -= 20;

            cs.setFont(PDType1Font.HELVETICA, 8);
            for (Transaction t : rows) {
                if (y < margin) {
                    cs.close();
                    page = new PDPage(PDRectangle.A4);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = page.getMediaBox().getHeight() - margin;
                    cs.setFont(PDType1Font.HELVETICA, 8);
                }
                String line = String.format("%s  %-7s  %-13s  %-5s  Rs%.2f  %s",
                        t.getTransactionDate(), t.getTransactionType(), t.getCategory(),
                        t.getPaymentMethod(), t.getAmount(), t.getNotes() == null ? "" : t.getNotes());
                if (line.length() > 110) line = line.substring(0, 110);
                cs.beginText();
                cs.newLineAtOffset(margin, y);
                cs.showText(line);
                cs.endText();
                y -= 13;
            }
            cs.close();
            doc.save(file);
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Failed to write PDF: " + e.getMessage()).showAndWait();
            return;
        }
        new Alert(Alert.AlertType.INFORMATION, "Saved to " + file.getAbsolutePath()).showAndWait();
    }
}

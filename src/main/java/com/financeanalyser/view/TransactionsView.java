package com.financeanalyser.view;

import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.CategoryTotal;
import com.financeanalyser.model.Transaction;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.Callback;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;

public class TransactionsView extends VBox {

    private static final String[] CATEGORIES_FILTER = {"All", "Food", "Travel", "Utilities", "Subscriptions", "Shopping", "Training", "Misc"};
    private static final String[] CATEGORIES = {"Food", "Travel", "Utilities", "Subscriptions", "Shopping", "Training", "Misc"};
    private static final String[] TYPES = {"Income", "Expense"};
    private static final String[] PAYMENTS = {"UPI", "Card", "Cash"};
    private static final String[] TAGS = {"", "Needs", "Wants"};

    private final DatabaseManager db;
    private final int userId;

    private Label outflowValue, outflowSub;
    private TextField searchInput;
    private ComboBox<String> categoryFilter;
    private DatePicker startDate, endDate;
    private TableView<Transaction> table;
    private Label countLabel;
    private final Map<Integer, SimpleBooleanProperty> checkStates = new HashMap<>();

    public TransactionsView(DatabaseManager db, int userId) {
        super(16);
        this.db = db;
        this.userId = userId;
        setPadding(new Insets(4, 28, 28, 28));
        buildUi();
        refresh();
    }

    private void buildUi() {
        HBox topRow = new HBox(16, buildOutflowCard(), buildFilterCard());
        HBox.setHgrow(topRow.getChildren().get(1), Priority.ALWAYS);
        getChildren().add(topRow);

        VBox ledgerCard = new VBox(10);
        ledgerCard.getStyleClass().add("card-white");
        ledgerCard.setPadding(new Insets(18));
        VBox.setVgrow(ledgerCard, Priority.ALWAYS);

        HBox ledgerTop = new HBox();
        VBox titleBox = new VBox(2);
        Label eyebrow = new Label("LEDGER");
        eyebrow.getStyleClass().add("block-eyebrow");
        Label title = new Label("All transactions");
        title.getStyleClass().add("section-title");
        titleBox.getChildren().addAll(eyebrow, title);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button importBtn = new Button("Import CSV");
        importBtn.getStyleClass().add("btn-outline");
        importBtn.setOnAction(e -> importCsv());
        Button exportBtn = new Button("\u2193  Export CSV");
        exportBtn.getStyleClass().add("btn-black");
        exportBtn.setOnAction(e -> exportCsv());
        HBox btnGroup = new HBox(8, importBtn, exportBtn);
        ledgerTop.getChildren().addAll(titleBox, sp, btnGroup);
        ledgerTop.setAlignment(Pos.CENTER_LEFT);

        table = buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        HBox footer = new HBox();
        countLabel = new Label("0 transactions");
        countLabel.getStyleClass().add("muted");
        Region fsp = new Region();
        HBox.setHgrow(fsp, Priority.ALWAYS);
        Button deleteBtn = new Button("\uD83D\uDDD1  Delete selected");
        deleteBtn.getStyleClass().add("button-danger");
        deleteBtn.setOnAction(e -> deleteSelected());
        footer.getChildren().addAll(countLabel, fsp, deleteBtn);
        footer.setAlignment(Pos.CENTER_LEFT);

        ledgerCard.getChildren().addAll(ledgerTop, table, footer);
        VBox.setVgrow(ledgerCard, Priority.ALWAYS);
        getChildren().add(ledgerCard);
    }

    private VBox buildOutflowCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card-black");
        card.setPadding(new Insets(18));
        card.setPrefWidth(220);
        Label label = new Label("TOTAL OUTFLOW");
        label.getStyleClass().add("stat-label");
        outflowValue = new Label("\u20B90");
        outflowValue.getStyleClass().add("stat-value");
        outflowValue.setStyle("-fx-text-fill: white; -fx-font-size: 30px;");
        outflowSub = new Label("");
        outflowSub.getStyleClass().add("muted");
        card.getChildren().addAll(label, outflowValue, outflowSub);
        return card;
    }

    private VBox buildFilterCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card-white");
        card.setPadding(new Insets(18));

        searchInput = new TextField();
        searchInput.setPromptText("Description, category or payment");
        searchInput.textProperty().addListener((o, ov, nv) -> refresh());

        categoryFilter = new ComboBox<>(FXCollections.observableArrayList(CATEGORIES_FILTER));
        categoryFilter.getSelectionModel().selectFirst();
        categoryFilter.setOnAction(e -> refresh());

        startDate = new DatePicker(LocalDate.now().minusMonths(1));
        endDate = new DatePicker(LocalDate.now());
        startDate.setOnAction(e -> refresh());
        endDate.setOnAction(e -> refresh());

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        Label l1 = new Label("Search"); l1.getStyleClass().add("muted");
        Label l2 = new Label("Category"); l2.getStyleClass().add("muted");
        Label l3 = new Label("From"); l3.getStyleClass().add("muted");
        Label l4 = new Label("To"); l4.getStyleClass().add("muted");
        grid.addRow(0, l1, l2, l3, l4);
        grid.addRow(1, searchInput, categoryFilter, startDate, endDate);
        GridPane.setHgrow(searchInput, Priority.ALWAYS);
        searchInput.setMaxWidth(Double.MAX_VALUE);

        card.getChildren().add(grid);
        return card;
    }

    private TableView<Transaction> buildTable() {
        TableView<Transaction> tv = new TableView<>();
        tv.setEditable(true);
        tv.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<Transaction, Boolean> checkCol = new TableColumn<>("");
        checkCol.setCellValueFactory(c -> checkStates.computeIfAbsent(c.getValue().getTransactionId(),
                id -> new SimpleBooleanProperty(false)));
        checkCol.setCellFactory(CheckBoxTableCell.forTableColumn(checkCol));
        checkCol.setEditable(true);
        checkCol.setPrefWidth(36);

        TableColumn<Transaction, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getTransactionDate()));
        dateCol.setCellFactory(TextFieldTableCell.forTableColumn());
        dateCol.setOnEditCommit(e -> {
            Transaction t = e.getRowValue();
            t.setTransactionDate(e.getNewValue());
            db.updateTransactionField(t.getTransactionId(), "transaction_date", e.getNewValue());
        });
        dateCol.setPrefWidth(100);

        TableColumn<Transaction, String> notesCol = new TableColumn<>("Description");
        notesCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getNotes() == null || c.getValue().getNotes().isBlank() ? c.getValue().getCategory() : c.getValue().getNotes()));
        notesCol.setCellFactory(TextFieldTableCell.forTableColumn());
        notesCol.setOnEditCommit(e -> {
            Transaction t = e.getRowValue();
            t.setNotes(e.getNewValue());
            db.updateTransactionField(t.getTransactionId(), "notes", e.getNewValue());
        });
        notesCol.setPrefWidth(220);

        TableColumn<Transaction, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getCategory()));
        categoryCol.setCellFactory(ComboBoxTableCell.forTableColumn(CATEGORIES));
        categoryCol.setOnEditCommit(e -> {
            Transaction t = e.getRowValue();
            t.setCategory(e.getNewValue());
            db.updateTransactionField(t.getTransactionId(), "category", e.getNewValue());
        });
        categoryCol.setPrefWidth(130);

        TableColumn<Transaction, String> paymentCol = new TableColumn<>("Payment");
        paymentCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getPaymentMethod()));
        paymentCol.setCellFactory(ComboBoxTableCell.forTableColumn(PAYMENTS));
        paymentCol.setOnEditCommit(e -> {
            Transaction t = e.getRowValue();
            t.setPaymentMethod(e.getNewValue());
            db.updateTransactionField(t.getTransactionId(), "payment_method", e.getNewValue());
        });
        paymentCol.setPrefWidth(90);

        TableColumn<Transaction, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                ("Income".equals(c.getValue().getTransactionType()) ? "+\u20B9" : "-\u20B9")
                        + String.format("%,.2f", c.getValue().getAmount())));
        amountCol.setCellFactory(TextFieldTableCell.forTableColumn());
        amountCol.setOnEditCommit(e -> {
            Transaction t = e.getRowValue();
            try {
                String raw = e.getNewValue().replace("+", "").replace("-", "").replace("\u20B9", "").replace(",", "").trim();
                double val = Double.parseDouble(raw);
                t.setAmount(val);
                db.updateTransactionField(t.getTransactionId(), "amount", String.valueOf(val));
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.WARNING, "Amount must be a number.").showAndWait();
            }
            refresh();
        });
        amountCol.setPrefWidth(110);
        amountCol.setStyle("-fx-alignment: CENTER_RIGHT; -fx-font-weight: bold;");

        TableColumn<Transaction, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getTransactionType()));
        typeCol.setCellFactory(ComboBoxTableCell.forTableColumn(TYPES));
        typeCol.setOnEditCommit(e -> {
            Transaction t = e.getRowValue();
            t.setTransactionType(e.getNewValue());
            db.updateTransactionField(t.getTransactionId(), "transaction_type", e.getNewValue());
        });
        typeCol.setPrefWidth(80);

        // Needs vs. Wants tag (Update 3 mission) - blank means "not tagged yet".
        TableColumn<Transaction, String> tagCol = new TableColumn<>("Tag");
        tagCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(
                c.getValue().getTag() == null ? "" : c.getValue().getTag()));
        tagCol.setCellFactory(ComboBoxTableCell.forTableColumn(TAGS));
        tagCol.setOnEditCommit(e -> {
            Transaction t = e.getRowValue();
            t.setTag(e.getNewValue());
            db.tagTransaction(t.getTransactionId(), e.getNewValue());
        });
        tagCol.setPrefWidth(90);

        tv.getColumns().addAll(List.of(checkCol, dateCol, notesCol, categoryCol, paymentCol, typeCol, amountCol, tagCol));
        return tv;
    }

    public void refresh() {
        List<Transaction> rows = db.getTransactions(
                userId,
                startDate.getValue() != null ? startDate.getValue().toString() : null,
                endDate.getValue() != null ? endDate.getValue().toString() : null,
                categoryFilter.getValue(),
                searchInput.getText().isEmpty() ? null : searchInput.getText()
        );
        ObservableList<Transaction> items = FXCollections.observableArrayList(rows);
        table.setItems(items);
        countLabel.setText(rows.size() + " transaction" + (rows.size() == 1 ? "" : "s"));

        String[] monthBounds = com.financeanalyser.controller.ForecastEngine.monthBounds();
        double total = db.sumExpenses(userId, monthBounds[0], monthBounds[1]);
        outflowValue.setText(String.format("\u20B9%,.0f", total));
        List<CategoryTotal> byCat = db.sumByCategory(userId, monthBounds[0], monthBounds[1], "Expense");
        outflowSub.setText(byCat.size() + " categor" + (byCat.size() == 1 ? "y" : "ies") + " this month");
    }

    private void deleteSelected() {
        List<Integer> toDelete = new ArrayList<>();
        for (Transaction t : table.getItems()) {
            SimpleBooleanProperty p = checkStates.get(t.getTransactionId());
            if (p != null && p.get()) toDelete.add(t.getTransactionId());
        }
        if (toDelete.isEmpty()) {
            for (Transaction t : table.getSelectionModel().getSelectedItems()) toDelete.add(t.getTransactionId());
        }
        for (int id : toDelete) {
            db.deleteTransaction(id);
            checkStates.remove(id);
        }
        refresh();
    }

    // ------------------------------------------------------------ CSV I/O
    private void importCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Transactions CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) return;

        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) return;
            List<String> headers = splitCsvLine(headerLine);
            for (int i = 0; i < headers.size(); i++) headers.set(i, headers.get(i).trim().toLowerCase());
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> values = splitCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size() && i < values.size(); i++) {
                    row.put(headers.get(i), values.get(i));
                }
                rows.add(row);
            }
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Failed to read file: " + e.getMessage()).showAndWait();
            return;
        }

        List<Map<String, String>> normalized = new ArrayList<>();
        for (Map<String, String> r : rows) {
            Map<String, String> n = new HashMap<>();
            n.put("amount", r.getOrDefault("amount", "0"));
            n.put("category", r.getOrDefault("category", "Misc"));
            n.put("transaction_type", r.containsKey("type") ? r.get("type") : r.getOrDefault("transaction_type", "Expense"));
            n.put("payment_method", r.containsKey("payment") ? r.get("payment") : r.getOrDefault("payment_method", "Cash"));
            n.put("transaction_date", r.containsKey("date") ? r.get("date") : r.getOrDefault("transaction_date", LocalDate.now().toString()));
            n.put("notes", r.containsKey("description") ? r.get("description") : r.getOrDefault("notes", ""));
            normalized.add(n);
        }
        int count = db.bulkImport(userId, normalized);
        new Alert(Alert.AlertType.INFORMATION, "Imported " + count + " transactions.").showAndWait();
        refresh();
    }

    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Transactions CSV");
        chooser.setInitialFileName("transactions.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;

        List<Transaction> rows = db.getTransactions(userId, null, null, "All", null);
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write("id,date,amount,type,category,payment_method,notes");
            writer.newLine();
            for (Transaction t : rows) {
                writer.write(String.join(",",
                        String.valueOf(t.getTransactionId()),
                        t.getTransactionDate(),
                        String.valueOf(t.getAmount()),
                        t.getTransactionType(),
                        t.getCategory(),
                        t.getPaymentMethod(),
                        csvEscape(t.getNotes())));
                writer.newLine();
            }
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, "Failed to write file: " + e.getMessage()).showAndWait();
            return;
        }
        new Alert(Alert.AlertType.INFORMATION, "Saved to " + file.getAbsolutePath()).showAndWait();
    }

    private static String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static List<String> splitCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    result.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        result.add(cur.toString());
        return result;
    }
}

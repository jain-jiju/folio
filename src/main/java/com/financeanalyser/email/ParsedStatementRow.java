package com.financeanalyser.email;

/** One transaction row extracted from a bank statement PDF or alert email. */
public class ParsedStatementRow {
    public String date;        // ISO yyyy-MM-dd
    public String description; // vendor / narration text
    public double amount;
    public String type;        // "Income" or "Expense"
    public String category = "Misc";

    public ParsedStatementRow(String date, String description, double amount, String type) {
        this.date = date;
        this.description = description;
        this.amount = amount;
        this.type = type;
    }
}

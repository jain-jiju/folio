package com.financeanalyser.model;

public class Transaction {
    private int transactionId;
    private int userId;
    private double amount;
    private String category;
    private String transactionType; // "Income" or "Expense"
    private String paymentMethod;   // "UPI", "Card", "Cash"
    private String transactionDate; // ISO yyyy-MM-dd
    private String notes;

    public Transaction(int transactionId, int userId, double amount, String category,
                        String transactionType, String paymentMethod, String transactionDate, String notes) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.category = category;
        this.transactionType = transactionType;
        this.paymentMethod = paymentMethod;
        this.transactionDate = transactionDate;
        this.notes = notes;
    }

    public int getTransactionId() { return transactionId; }
    public int getUserId() { return userId; }
    public double getAmount() { return amount; }
    public String getCategory() { return category; }
    public String getTransactionType() { return transactionType; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getTransactionDate() { return transactionDate; }
    public String getNotes() { return notes; }

    public void setAmount(double amount) { this.amount = amount; }
    public void setCategory(String category) { this.category = category; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }
    public void setNotes(String notes) { this.notes = notes; }
}

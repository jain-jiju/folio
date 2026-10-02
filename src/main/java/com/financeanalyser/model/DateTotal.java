package com.financeanalyser.model;

/** Simple (date, total) pair returned by daily-grouped queries. */
public class DateTotal {
    private final String date;
    private final double total;

    public DateTotal(String date, double total) {
        this.date = date;
        this.total = total;
    }

    public String getDate() { return date; }
    public double getTotal() { return total; }
}

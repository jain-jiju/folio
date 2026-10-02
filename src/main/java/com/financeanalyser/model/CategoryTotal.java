package com.financeanalyser.model;

/** Simple (category, total) pair returned by grouped queries. */
public class CategoryTotal {
    private final String category;
    private final double total;

    public CategoryTotal(String category, double total) {
        this.category = category;
        this.total = total;
    }

    public String getCategory() { return category; }
    public double getTotal() { return total; }
}

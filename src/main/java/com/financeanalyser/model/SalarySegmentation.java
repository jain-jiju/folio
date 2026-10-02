package com.financeanalyser.model;

public class SalarySegmentation {
    private int segmentId;
    private int userId;
    private double totalIncome;
    private String preferenceMode;
    private double groceriesCap;
    private double entertainmentCap;
    private double trainingCap;
    private double savingsCap;

    public SalarySegmentation(int segmentId, int userId, double totalIncome, String preferenceMode,
                               double groceriesCap, double entertainmentCap, double trainingCap, double savingsCap) {
        this.segmentId = segmentId;
        this.userId = userId;
        this.totalIncome = totalIncome;
        this.preferenceMode = preferenceMode;
        this.groceriesCap = groceriesCap;
        this.entertainmentCap = entertainmentCap;
        this.trainingCap = trainingCap;
        this.savingsCap = savingsCap;
    }

    public int getSegmentId() { return segmentId; }
    public int getUserId() { return userId; }
    public double getTotalIncome() { return totalIncome; }
    public String getPreferenceMode() { return preferenceMode; }
    public double getGroceriesCap() { return groceriesCap; }
    public double getEntertainmentCap() { return entertainmentCap; }
    public double getTrainingCap() { return trainingCap; }
    public double getSavingsCap() { return savingsCap; }
}

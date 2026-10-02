package com.financeanalyser.controller;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Analytics & Forecasting Engine.
 * Pure-Java moving-average / velocity forecasting - runs in-process,
 * no external sidecar process needed.
 */
public class ForecastEngine {

    public static int daysInMonth(LocalDate d) {
        return YearMonth.from(d).lengthOfMonth();
    }

    public static String[] monthBounds(LocalDate d) {
        LocalDate start = d.withDayOfMonth(1);
        LocalDate end = d.withDayOfMonth(daysInMonth(d));
        return new String[]{start.toString(), end.toString()};
    }

    public static String[] monthBounds() {
        return monthBounds(LocalDate.now());
    }

    public static String[] previousMonthBounds(LocalDate d) {
        LocalDate firstThis = d.withDayOfMonth(1);
        LocalDate lastMonthEnd = firstThis.minusDays(1);
        LocalDate lastMonthStart = lastMonthEnd.withDayOfMonth(1);
        return new String[]{lastMonthStart.toString(), lastMonthEnd.toString()};
    }

    public static String[] previousMonthBounds() {
        return previousMonthBounds(LocalDate.now());
    }

    /** Remaining Monthly Budget / Remaining Days in Month */
    public static double todayAllowance(double monthlyBudget, LocalDate today) {
        int totalDays = daysInMonth(today);
        int remainingDays = Math.max(1, totalDays - today.getDayOfMonth() + 1);
        return monthlyBudget == 0 ? 0.0 : monthlyBudget / remainingDays;
    }

    public static double todayAllowance(double monthlyBudget) {
        return todayAllowance(monthlyBudget, LocalDate.now());
    }

    /** "green" &lt;75%, "amber" 75-99%, "red" &gt;=100% */
    public static String budgetStatus(double spentRatio) {
        if (spentRatio >= 1.0) return "red";
        if (spentRatio >= 0.75) return "amber";
        return "green";
    }

    /** Projected Total = Current Spent + (Current Spent / Days Elapsed * Days Remaining) */
    public static double projectedMonthTotal(double currentSpent, LocalDate today) {
        int daysElapsed = today.getDayOfMonth();
        int totalDays = daysInMonth(today);
        int daysRemaining = totalDays - daysElapsed;
        if (daysElapsed == 0) return currentSpent;
        double velocity = currentSpent / daysElapsed;
        return currentSpent + velocity * daysRemaining;
    }

    public static double projectedMonthTotal(double currentSpent) {
        return projectedMonthTotal(currentSpent, LocalDate.now());
    }

    /** Days (from today) until spending velocity exhausts the monthly budget, or -1 if it won't this month. */
    public static int daysUntilExhaustion(double currentSpent, double monthlyBudget, LocalDate today) {
        int daysElapsed = Math.max(1, today.getDayOfMonth());
        double velocity = currentSpent / daysElapsed;
        if (velocity <= 0) return -1;
        double remainingBudget = monthlyBudget - currentSpent;
        if (remainingBudget <= 0) return 0;
        double daysLeftAtPace = remainingBudget / velocity;
        int daysRemainingInMonth = daysInMonth(today) - today.getDayOfMonth();
        if (daysLeftAtPace < daysRemainingInMonth) {
            return (int) Math.round(daysLeftAtPace);
        }
        return -1; // will not exhaust this month at current pace
    }

    public static int daysUntilExhaustion(double currentSpent, double monthlyBudget) {
        return daysUntilExhaustion(currentSpent, monthlyBudget, LocalDate.now());
    }

    public static Double monthOverMonthGrowth(double thisMonthTotal, double lastMonthTotal) {
        if (lastMonthTotal == 0) return null;
        return (thisMonthTotal - lastMonthTotal) / lastMonthTotal * 100.0;
    }

    // ---- Salary segmentation presets ----
    public static final Map<String, double[]> STRATEGY_PRESETS = new LinkedHashMap<>();
    static {
        // {groceries, entertainment, training, savings}
        STRATEGY_PRESETS.put("Standard Balanced (50/30/20)", new double[]{0.50, 0.30, 0.00, 0.20});
        STRATEGY_PRESETS.put("Student Growth (35/10/25/30)", new double[]{0.35, 0.10, 0.25, 0.30});
        STRATEGY_PRESETS.put("Aggressive Saver (35/10/10/45)", new double[]{0.35, 0.10, 0.10, 0.45});
    }

    public static class Segmentation {
        public final double groceries, entertainment, training, savings;
        public Segmentation(double g, double e, double t, double s) {
            groceries = g; entertainment = e; training = t; savings = s;
        }
    }

    public static Segmentation computeSegmentation(double totalIncome, String strategy, double[] customRatios) {
        double[] ratios = STRATEGY_PRESETS.getOrDefault(strategy,
                strategy.equals("Custom") && customRatios != null ? customRatios
                        : STRATEGY_PRESETS.get("Standard Balanced (50/30/20)"));
        return new Segmentation(
                round2(totalIncome * ratios[0]),
                round2(totalIncome * ratios[1]),
                round2(totalIncome * ratios[2]),
                round2(totalIncome * ratios[3])
        );
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

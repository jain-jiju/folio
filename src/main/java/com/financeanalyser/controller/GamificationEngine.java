package com.financeanalyser.controller;

import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.CategoryTotal;
import com.financeanalyser.model.SalarySegmentation;

import java.util.*;

public class GamificationEngine {

    public static final int XP_DAILY_LOG = 50;
    public static final int XP_UNDER_ALLOWANCE = 200;

    public static final LinkedHashMap<String, String> BADGE_DEFS = new LinkedHashMap<>();
    static {
        BADGE_DEFS.put("Budget Master", "Stay under budget across all categories for a full month.");
        BADGE_DEFS.put("Saver Ninja", "Allocate >= 20% of income to savings.");
        BADGE_DEFS.put("Impulse Control Hero", "Reduce discretionary spending (Shopping/Dining out) by 10% vs. prior month.");
    }

    public static void logTransactionGamification(DatabaseManager db, int userId, double allowanceToday, double spentTodayTotal) {
        int xp = XP_DAILY_LOG;
        if (spentTodayTotal <= allowanceToday) {
            xp += XP_UNDER_ALLOWANCE;
        }
        db.awardXpAndStreak(userId, xp, null);
    }

    public static List<String> evaluateBadges(DatabaseManager db, int userId,
                                                String monthStart, String monthEnd,
                                                String prevStart, String prevEnd) {
        List<String> earned = new ArrayList<>();

        SalarySegmentation seg = db.latestSegmentation(userId);
        if (seg != null && seg.getTotalIncome() > 0) {
            double savingsRatio = seg.getSavingsCap() / seg.getTotalIncome();
            if (savingsRatio >= 0.20) earned.add("Saver Ninja");
        }

        Map<String, Double> byCat = new HashMap<>();
        for (CategoryTotal ct : db.sumByCategory(userId, monthStart, monthEnd, "Expense")) {
            byCat.put(ct.getCategory(), ct.getTotal());
        }

        if (seg != null) {
            Map<String, Double> caps = new HashMap<>();
            caps.put("Food", seg.getGroceriesCap());
            caps.put("Utilities", seg.getGroceriesCap());
            caps.put("Shopping", seg.getEntertainmentCap());
            caps.put("Subscriptions", seg.getEntertainmentCap());
            caps.put("Training", seg.getTrainingCap());

            boolean overBudget = false;
            for (Map.Entry<String, Double> e : caps.entrySet()) {
                if (e.getValue() > 0 && byCat.getOrDefault(e.getKey(), 0.0) > e.getValue()) {
                    overBudget = true;
                    break;
                }
            }
            if (!overBudget && !byCat.isEmpty()) earned.add("Budget Master");
        }

        double thisDiscretionary = byCat.getOrDefault("Shopping", 0.0);
        Map<String, Double> prevByCat = new HashMap<>();
        for (CategoryTotal ct : db.sumByCategory(userId, prevStart, prevEnd, "Expense")) {
            prevByCat.put(ct.getCategory(), ct.getTotal());
        }
        double prevDiscretionary = prevByCat.getOrDefault("Shopping", 0.0);
        if (prevDiscretionary > 0 && thisDiscretionary <= prevDiscretionary * 0.90) {
            earned.add("Impulse Control Hero");
        }

        return earned;
    }

    public static List<String> refreshBadges(DatabaseManager db, int userId,
                                              String monthStart, String monthEnd,
                                              String prevStart, String prevEnd) {
        List<String> badges = evaluateBadges(db, userId, monthStart, monthEnd, prevStart, prevEnd);
        db.setBadges(userId, badges);
        return badges;
    }
}

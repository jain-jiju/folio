package com.financeanalyser.controller;

import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.CategoryTotal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MissionGenerator {

    public static class Mission {
        public final String title;
        public final String category;
        public final double target;
        public final double progress;

        public Mission(String title, String category, double target, double progress) {
            this.title = title; this.category = category; this.target = target; this.progress = progress;
        }
    }

    public static List<Mission> generateWeeklyMissions(DatabaseManager db, int userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String start = weekStart.toString();
        String end = today.toString();

        Map<String, Double> byCat = new HashMap<>();
        for (CategoryTotal ct : db.sumByCategory(userId, start, end, "Expense")) {
            byCat.put(ct.getCategory(), ct.getTotal());
        }

        List<Mission> missions = new ArrayList<>();

        double travelSpent = byCat.getOrDefault("Travel", 0.0);
        double targetTravel = travelSpent > 0 ? roundTen(Math.max(200, travelSpent * 0.8)) : 300;
        missions.add(new Mission(
                String.format("Keep Travel expenses under \u20B9%.0f this week", targetTravel),
                "Travel", targetTravel, travelSpent));

        double shoppingSpent = byCat.getOrDefault("Shopping", 0.0);
        double targetShopping = shoppingSpent > 0 ? roundTen(Math.max(300, shoppingSpent * 0.85)) : 500;
        missions.add(new Mission(
                String.format("Cap Shopping spend at \u20B9%.0f this week", targetShopping),
                "Shopping", targetShopping, shoppingSpent));

        int streak = db.getGamification(userId).getCurrentStreak();
        missions.add(new Mission("Log every expense for 5 days straight", "Streak", 5, Math.min(5, streak)));

        return missions;
    }

    private static double roundTen(double v) {
        return Math.round(v / 10.0) * 10.0;
    }
}

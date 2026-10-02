package com.financeanalyser.model;

import java.util.List;

public class GamificationState {
    private int userId;
    private int xpPoints;
    private int currentStreak;
    private String lastLoggedDate; // ISO yyyy-MM-dd, nullable
    private List<String> badges;

    public GamificationState(int userId, int xpPoints, int currentStreak, String lastLoggedDate, List<String> badges) {
        this.userId = userId;
        this.xpPoints = xpPoints;
        this.currentStreak = currentStreak;
        this.lastLoggedDate = lastLoggedDate;
        this.badges = badges;
    }

    public int getUserId() { return userId; }
    public int getXpPoints() { return xpPoints; }
    public int getCurrentStreak() { return currentStreak; }
    public String getLastLoggedDate() { return lastLoggedDate; }
    public List<String> getBadges() { return badges; }

    public int level() {
        return Math.max(1, xpPoints / 300 + 1);
    }
}

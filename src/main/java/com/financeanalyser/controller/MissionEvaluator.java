package com.financeanalyser.controller;

import com.financeanalyser.db.DatabaseManager;
import com.financeanalyser.model.CategoryTotal;
import com.financeanalyser.model.Mission;
import com.financeanalyser.model.MissionType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the user's current missions from the database - no mission in this
 * class can be completed by spending more, and none demands an impossible
 * zero-spend day. Replaces the old MissionGenerator, which capped category
 * spend at an arbitrary rupee figure.
 *
 * Call evaluateWeekly() on app startup / dashboard refresh; it's cheap
 * (a handful of indexed SELECTs) so it's safe to call on every refresh
 * rather than caching.
 */
public class MissionEvaluator {

    /** Default weekly micro-savings goal shown to a brand-new user with no history yet. */
    private static final double DEFAULT_MICRO_SAVINGS_WEEKLY_GOAL = 100.0;
    public static final int XP_MICRO_SAVING_LOG = 30;       // instant XP per micro-saving entry
    public static final int XP_DAILY_MICRO_CHECK = 40;
    public static final int XP_TAGGING_COMPLETE = 60;
    public static final int XP_MICRO_SAVINGS_GOAL = 80;
    public static final int XP_BEAT_BASELINE = 120;

    /** How many of the user's top-spending categories get a "beat your baseline" mission. */
    private static final int BASELINE_CATEGORY_COUNT = 2;
    private static final int BASELINE_LOOKBACK_WEEKS = 4;

    public static List<Mission> evaluateWeekly(DatabaseManager db, int userId) {
        List<Mission> missions = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String weekStartStr = weekStart.toString();
        String todayStr = today.toString();

        missions.add(dailyMicroCheck(db, userId));
        missions.add(needsVsWantsTagging(db, userId, weekStartStr, todayStr));
        missions.add(microSwapSavingsLog(db, userId, weekStartStr, todayStr));
        missions.addAll(beatYourBaseline(db, userId, weekStartStr, todayStr));

        return missions;
    }

    // ---------------------------------------------------- Daily Micro-Check
    private static Mission dailyMicroCheck(DatabaseManager db, int userId) {
        boolean loggedToday = db.hasLoggedToday(userId);
        return new Mission(
                "daily_micro_check",
                MissionType.DAILY_MICRO_CHECK,
                "Daily micro-check",
                "Log or review today's expenses before the day ends - just awareness, no spending required.",
                XP_DAILY_MICRO_CHECK,
                loggedToday ? 1 : 0, 1,
                loggedToday
        );
    }

    // ------------------------------------------------ Needs vs. Wants tagging
    private static Mission needsVsWantsTagging(DatabaseManager db, int userId, String start, String end) {
        int totalExpenses = db.countExpenseTransactions(userId, start, end);
        int tagged = db.countTaggedTransactions(userId, start, end);
        boolean completed = totalExpenses > 0 && tagged >= totalExpenses;
        String desc = totalExpenses == 0
                ? "Log an expense this week, then tag it Needs or Wants from the Transactions tab."
                : "Tag every expense logged this week as a Need or a Want, from the Transactions tab.";
        return new Mission(
                "needs_vs_wants_tagging",
                MissionType.NEEDS_VS_WANTS_TAGGING,
                "Needs vs. Wants tagging",
                desc,
                XP_TAGGING_COMPLETE,
                tagged, Math.max(totalExpenses, 1),
                completed
        );
    }

    // ---------------------------------------------------- Micro-Swap / Savings
    private static Mission microSwapSavingsLog(DatabaseManager db, int userId, String start, String end) {
        double savedThisWeek = db.sumMicroSavings(userId, start, end);
        boolean completed = savedThisWeek >= DEFAULT_MICRO_SAVINGS_WEEKLY_GOAL;
        return new Mission(
                "micro_swap_savings_log",
                MissionType.MICRO_SWAP_SAVINGS_LOG,
                "Micro-swap savings log",
                String.format("Log small deliberate savings (a cheaper swap, a skipped impulse buy) - " +
                        "reach ₹%.0f saved this week.", DEFAULT_MICRO_SAVINGS_WEEKLY_GOAL),
                XP_MICRO_SAVINGS_GOAL,
                savedThisWeek, DEFAULT_MICRO_SAVINGS_WEEKLY_GOAL,
                completed
        );
    }

    // ---------------------------------------------------- Beat Your Baseline
    private static List<Mission> beatYourBaseline(DatabaseManager db, int userId, String weekStart, String weekEnd) {
        List<Mission> result = new ArrayList<>();
        List<CategoryTotal> thisWeekByCat = db.sumByCategory(userId, weekStart, weekEnd, "Expense");
        // Rank by the user's own historical average, not this week's spend, so the
        // mission targets categories they actually have a baseline for.
        thisWeekByCat.sort((a, b) -> Double.compare(b.getTotal(), a.getTotal()));

        int count = 0;
        for (CategoryTotal ct : thisWeekByCat) {
            if (count >= BASELINE_CATEGORY_COUNT) break;
            double baseline = db.averageWeeklySpend(userId, ct.getCategory(), BASELINE_LOOKBACK_WEEKS);
            if (baseline <= 0) continue; // not enough history for this category yet
            double thisWeekSpend = ct.getTotal();
            boolean beaten = thisWeekSpend < baseline;
            result.add(new Mission(
                    "beat_baseline_" + ct.getCategory(),
                    MissionType.BEAT_YOUR_BASELINE,
                    "Beat your baseline: " + ct.getCategory(),
                    String.format("Your %s spend has averaged ₹%.0f/week over the last %d weeks. " +
                            "Spend less than that this week.", ct.getCategory(), baseline, BASELINE_LOOKBACK_WEEKS),
                    XP_BEAT_BASELINE,
                    thisWeekSpend, baseline,
                    beaten
            ));
            count++;
        }
        return result;
    }
}

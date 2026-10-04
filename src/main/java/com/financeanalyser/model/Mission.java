package com.financeanalyser.model;

/**
 * A single mission instance evaluated for "today" or "this week".
 * progress/target are expressed in whatever unit fits the mission
 * (rupees for savings/baseline missions, a 0/1 count for yes-no missions,
 * a tag count for the tagging mission) - the UI reads completed rather
 * than assuming progress >= target means success in every case.
 */
public class Mission {
    private final String id;
    private final MissionType type;
    private final String title;
    private final String description;
    private final int xpReward;
    private final double progress;
    private final double target;
    private final boolean completed;

    public Mission(String id, MissionType type, String title, String description,
                    int xpReward, double progress, double target, boolean completed) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.description = description;
        this.xpReward = xpReward;
        this.progress = progress;
        this.target = target;
        this.completed = completed;
    }

    public String getId() { return id; }
    public MissionType getType() { return type; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getXpReward() { return xpReward; }
    public double getProgress() { return progress; }
    public double getTarget() { return target; }
    public boolean isCompleted() { return completed; }

    /** 0.0-1.0, clamped, for progress bars. */
    public double progressRatio() {
        if (target <= 0) return completed ? 1.0 : 0.0;
        return Math.max(0.0, Math.min(1.0, progress / target));
    }
}

package com.financeanalyser.model;

/**
 * Categories of mission in the redesigned, restraint-and-awareness-focused
 * missions system (Update 3). None of these can be satisfied by spending
 * more, hitting an impossible zero-spend day, or a hardcoded rupee cap -
 * they reward logging, categorizing, small deliberate savings, and beating
 * the user's own historical average.
 */
public enum MissionType {
    /** Reward for logging/reviewing today's expenses before the day ends. */
    DAILY_MICRO_CHECK,
    /** Reward for tagging logged transactions as Needs or Wants. */
    NEEDS_VS_WANTS_TAGGING,
    /** Reward for recording a small deliberate saving (a cheaper swap, a skipped impulse buy). */
    MICRO_SWAP_SAVINGS_LOG,
    /** Reward for spending less in a category this week than the user's own recent average. */
    BEAT_YOUR_BASELINE
}

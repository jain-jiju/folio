-- Personal Finance Analyser - SQLite Schema
PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
    user_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    username        TEXT NOT NULL UNIQUE,
    password_hash   TEXT NOT NULL,
    roll_no         TEXT,
    semester        TEXT,
    monthly_salary  REAL DEFAULT 0,
    profile_pic_path TEXT,
    created_at      TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS daily_budgets (
    budget_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          INTEGER NOT NULL,
    budget_date      TEXT NOT NULL,
    allocated_amount REAL NOT NULL,
    UNIQUE(user_id, budget_date),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          INTEGER NOT NULL,
    amount           REAL NOT NULL,
    category         TEXT NOT NULL,
    transaction_type TEXT NOT NULL CHECK (transaction_type IN ('Income','Expense')),
    payment_method   TEXT NOT NULL,
    transaction_date TEXT NOT NULL,
    notes            TEXT,
    created_at       TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS salary_segmentation (
    segment_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id           INTEGER NOT NULL,
    total_income      REAL NOT NULL,
    preference_mode   TEXT NOT NULL,
    groceries_cap     REAL NOT NULL DEFAULT 0,
    entertainment_cap REAL NOT NULL DEFAULT 0,
    training_cap      REAL NOT NULL DEFAULT 0,
    savings_cap       REAL NOT NULL DEFAULT 0,
    created_at        TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS gamification (
    user_id          INTEGER PRIMARY KEY,
    xp_points        INTEGER NOT NULL DEFAULT 0,
    current_streak   INTEGER NOT NULL DEFAULT 0,
    last_logged_date TEXT,
    badges_json      TEXT NOT NULL DEFAULT '[]',
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_transactions_user_date ON transactions(user_id, transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_category ON transactions(user_id, category);

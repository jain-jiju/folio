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

-- Needs vs. Wants tagging (Missions Update 3) and email-import dedup (Update 1)
-- added as ALTER TABLE in DatabaseManager.migrateSchema() for existing databases
-- declared here too so a brand-new database already has them.
ALTER TABLE transactions ADD COLUMN tag TEXT;
ALTER TABLE transactions ADD COLUMN source_message_id TEXT;
ALTER TABLE transactions ADD COLUMN dedup_hash TEXT;

CREATE INDEX IF NOT EXISTS idx_transactions_dedup ON transactions(dedup_hash);

-- Small wins the user logs manually (Micro-Swap missions) - "saved Rs.50 by
-- choosing a cheaper alternative" style entries, separate from real transactions.
CREATE TABLE IF NOT EXISTS micro_savings_log (
    saving_id    INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id      INTEGER NOT NULL,
    log_date     TEXT NOT NULL,
    amount_saved REAL NOT NULL,
    note         TEXT,
    created_at   TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Automated bank-email sync configuration (Update 1). app_password_enc is
-- obfuscated with a simple reversible cipher, NOT strong encryption - see
-- EmailSyncConfig's class comment for why, and use a dedicated Gmail "App
-- Password" here, never your real account password.
CREATE TABLE IF NOT EXISTS email_sync_config (
    user_id          INTEGER PRIMARY KEY,
    imap_host        TEXT,
    imap_port        INTEGER DEFAULT 993,
    email_address    TEXT,
    app_password_enc TEXT,
    password_formula  TEXT,
    enabled          INTEGER NOT NULL DEFAULT 0,
    last_sync_at     TEXT,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

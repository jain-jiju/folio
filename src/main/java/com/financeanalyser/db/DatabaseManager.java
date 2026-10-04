package com.financeanalyser.db;

import com.financeanalyser.model.*;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * All SQLite access goes through this class. Uses the sqlite-jdbc driver -
 * 100% local, zero-configuration, offline embedded database.
 */
public class DatabaseManager {

    private Connection conn;
    private String dbPath;

    public DatabaseManager(String dbPath) {
        this.dbPath = dbPath;
        boolean firstRun = !Files.exists(Paths.get(dbPath));
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
            if (firstRun) {
                initSchema();
            }
            migrateSchema();
        } catch (Exception e) {
            throw new RuntimeException("Failed to open database: " + e.getMessage(), e);
        }
    }

    /** Adds columns introduced after the original schema to existing databases.
     * Safe to run every startup - each ALTER is wrapped so "duplicate column" is ignored. */
    private void migrateSchema() {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE users ADD COLUMN profile_pic_path TEXT");
        } catch (SQLException ignored) {
            // column already exists - nothing to do
        }
        // --- Update 1 (email import) / Update 3 (missions) columns ---
        for (String alter : new String[]{
                "ALTER TABLE transactions ADD COLUMN tag TEXT",
                "ALTER TABLE transactions ADD COLUMN source_message_id TEXT",
                "ALTER TABLE transactions ADD COLUMN dedup_hash TEXT"
        }) {
            try (Statement st = conn.createStatement()) {
                st.execute(alter);
            } catch (SQLException ignored) {
                // column already exists - nothing to do
            }
        }
        // CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS are idempotent,
        // so these are safe to run unconditionally on every startup. Each gets its
        // own Statement (see initSchema() for why) instead of one reused handle.
        String[] migrations = {
                "CREATE INDEX IF NOT EXISTS idx_transactions_dedup ON transactions(dedup_hash)",
                "CREATE TABLE IF NOT EXISTS micro_savings_log (" +
                        "saving_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, " +
                        "log_date TEXT NOT NULL, amount_saved REAL NOT NULL, note TEXT, " +
                        "created_at TEXT NOT NULL DEFAULT (datetime('now')), " +
                        "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE)",
                "CREATE TABLE IF NOT EXISTS email_sync_config (" +
                        "user_id INTEGER PRIMARY KEY, imap_host TEXT, imap_port INTEGER DEFAULT 993, " +
                        "email_address TEXT, app_password_enc TEXT, password_formula TEXT, " +
                        "enabled INTEGER NOT NULL DEFAULT 0, last_sync_at TEXT, " +
                        "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE)"
        };
        for (String sql : migrations) {
            try (Statement st = conn.createStatement()) {
                st.execute(sql);
            } catch (SQLException e) {
                throw new RuntimeException("Schema migration failed: " + e.getMessage(), e);
            }
        }
    }

    // ------------------------------------------------------------- setup
    private void initSchema() throws IOException, SQLException {
        String schemaSql = readResource("/schema.sql");
        // sqlite-jdbc's Statement.execute() only runs a single statement reliably,
        // so split the script on ";" and run each non-empty statement in turn.
        // A fresh Statement per execute (rather than one reused across the whole
        // loop) avoids a sqlite-jdbc 3.45.1.0 native-pointer finalization bug
        // ("The prepared statement has been finalized") that a long reused loop
        // can trigger.
        for (String stmt : schemaSql.split(";")) {
            String trimmed = stmt.trim();
            if (trimmed.isEmpty()) continue;
            try (Statement st = conn.createStatement()) {
                st.execute(trimmed);
            }
        }
    }

    private String readResource(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new FileNotFoundException("Resource not found: " + path);
            return new String(is.readAllBytes());
        }
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public User ensureDefaultUser(String username) {
        try {
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM users LIMIT 1")) {
                if (rs.next()) return mapUser(rs);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (username, password_hash, monthly_salary) VALUES (?,?,0)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, username);
                ps.setString(2, sha256("changeme"));
                ps.executeUpdate();
                ResultSet keys = ps.getGeneratedKeys();
                keys.next();
                int userId = keys.getInt(1);
                try (PreparedStatement gp = conn.prepareStatement(
                        "INSERT INTO gamification (user_id, xp_points, current_streak, last_logged_date, badges_json) " +
                                "VALUES (?, 0, 0, NULL, '[]')")) {
                    gp.setInt(1, userId);
                    gp.executeUpdate();
                }
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM users LIMIT 1")) {
                rs.next();
                return mapUser(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean verifyPassword(int userId, String password) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM users WHERE user_id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getString("password_hash").equals(sha256(password));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setPassword(int userId, String newPassword) {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET password_hash=? WHERE user_id=?")) {
            ps.setString(1, sha256(newPassword));
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public User getUser(int userId) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE user_id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapUser(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateMonthlySalary(int userId, double salary) {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE users SET monthly_salary=? WHERE user_id=?")) {
            ps.setDouble(1, salary);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("user_id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getDouble("monthly_salary"), rs.getString("created_at"), rs.getString("profile_pic_path")
        );
    }

    /** Updates the display name and/or profile picture path. Pass null for profilePicPath to leave it unchanged. */
    public void updateUserProfile(int userId, String username, String profilePicPath) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET username=?, profile_pic_path=COALESCE(?, profile_pic_path) WHERE user_id=?")) {
            ps.setString(1, username);
            ps.setString(2, profilePicPath);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------------------------------------------------- transactions
    public int addTransaction(int userId, double amount, String category, String type,
                               String paymentMethod, String transactionDate, String notes) {
        String date = (transactionDate == null || transactionDate.isEmpty())
                ? LocalDate.now().toString() : transactionDate;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO transactions (user_id, amount, category, transaction_type, payment_method, transaction_date, notes) " +
                        "VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setDouble(2, amount);
            ps.setString(3, category);
            ps.setString(4, type);
            ps.setString(5, paymentMethod);
            ps.setString(6, date);
            ps.setString(7, notes);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            return keys.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateTransactionField(int transactionId, String field, String value) {
        String[] numericFields = {"amount"};
        boolean numeric = Arrays.asList(numericFields).contains(field);
        String sql = "UPDATE transactions SET " + field + "=? WHERE transaction_id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (numeric) {
                ps.setDouble(1, Double.parseDouble(value));
            } else {
                ps.setString(1, value);
            }
            ps.setInt(2, transactionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteTransaction(int transactionId) {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM transactions WHERE transaction_id=?")) {
            ps.setInt(1, transactionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Transaction> getTransactions(int userId, String start, String end, String category, String search) {
        StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE user_id=?");
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (start != null) { sql.append(" AND transaction_date >= ?"); params.add(start); }
        if (end != null) { sql.append(" AND transaction_date <= ?"); params.add(end); }
        if (category != null && !category.equals("All")) { sql.append(" AND category = ?"); params.add(category); }
        if (search != null && !search.isEmpty()) {
            sql.append(" AND (notes LIKE ? OR category LIKE ? OR payment_method LIKE ?)");
            String like = "%" + search + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sql.append(" ORDER BY transaction_date DESC, transaction_id DESC");

        List<Transaction> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new Transaction(
                        rs.getInt("transaction_id"), rs.getInt("user_id"), rs.getDouble("amount"),
                        rs.getString("category"), rs.getString("transaction_type"),
                        rs.getString("payment_method"), rs.getString("transaction_date"), rs.getString("notes"),
                        rs.getString("tag")
                ));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public double sumExpenses(int userId, String start, String end) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(amount),0) AS total FROM transactions " +
                        "WHERE user_id=? AND transaction_type='Expense' AND transaction_date BETWEEN ? AND ?")) {
            ps.setInt(1, userId);
            ps.setString(2, start);
            ps.setString(3, end);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getDouble("total");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<CategoryTotal> sumByCategory(int userId, String start, String end, String type) {
        List<CategoryTotal> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT category, COALESCE(SUM(amount),0) AS total FROM transactions " +
                        "WHERE user_id=? AND transaction_type=? AND transaction_date BETWEEN ? AND ? " +
                        "GROUP BY category ORDER BY total DESC")) {
            ps.setInt(1, userId);
            ps.setString(2, type);
            ps.setString(3, start);
            ps.setString(4, end);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(new CategoryTotal(rs.getString("category"), rs.getDouble("total")));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public List<DateTotal> dailyTotals(int userId, String start, String end) {
        List<DateTotal> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT transaction_date, COALESCE(SUM(amount),0) AS total FROM transactions " +
                        "WHERE user_id=? AND transaction_type='Expense' AND transaction_date BETWEEN ? AND ? " +
                        "GROUP BY transaction_date ORDER BY transaction_date ASC")) {
            ps.setInt(1, userId);
            ps.setString(2, start);
            ps.setString(3, end);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) result.add(new DateTotal(rs.getString("transaction_date"), rs.getDouble("total")));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public int bulkImport(int userId, List<Map<String, String>> rows) {
        int inserted = 0;
        for (Map<String, String> row : rows) {
            try {
                double amount = Double.parseDouble(row.getOrDefault("amount", "0"));
                addTransaction(userId, amount,
                        row.getOrDefault("category", "Misc"),
                        row.getOrDefault("transaction_type", "Expense"),
                        row.getOrDefault("payment_method", "Cash"),
                        row.getOrDefault("transaction_date", LocalDate.now().toString()),
                        row.getOrDefault("notes", ""));
                inserted++;
            } catch (Exception ignored) {
                // skip malformed rows
            }
        }
        return inserted;
    }

    // -------------------------------------------------------- segmentation
    public void saveSegmentation(int userId, double totalIncome, String mode,
                                  double groceries, double entertainment, double training, double savings) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO salary_segmentation (user_id, total_income, preference_mode, " +
                        "groceries_cap, entertainment_cap, training_cap, savings_cap) VALUES (?,?,?,?,?,?,?)")) {
            ps.setInt(1, userId);
            ps.setDouble(2, totalIncome);
            ps.setString(3, mode);
            ps.setDouble(4, groceries);
            ps.setDouble(5, entertainment);
            ps.setDouble(6, training);
            ps.setDouble(7, savings);
            ps.executeUpdate();
            updateMonthlySalary(userId, totalIncome);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public SalarySegmentation latestSegmentation(int userId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM salary_segmentation WHERE user_id=? ORDER BY segment_id DESC LIMIT 1")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new SalarySegmentation(
                        rs.getInt("segment_id"), rs.getInt("user_id"), rs.getDouble("total_income"),
                        rs.getString("preference_mode"), rs.getDouble("groceries_cap"),
                        rs.getDouble("entertainment_cap"), rs.getDouble("training_cap"), rs.getDouble("savings_cap")
                );
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ------------------------------------------------------------ gamified
    public GamificationState getGamification(int userId) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM gamification WHERE user_id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                try (PreparedStatement ip = conn.prepareStatement(
                        "INSERT INTO gamification (user_id, xp_points, current_streak, last_logged_date, badges_json) " +
                                "VALUES (?,0,0,NULL,'[]')")) {
                    ip.setInt(1, userId);
                    ip.executeUpdate();
                }
                return new GamificationState(userId, 0, 0, null, new ArrayList<>());
            }
            List<String> badges = parseBadgesJson(rs.getString("badges_json"));
            return new GamificationState(userId, rs.getInt("xp_points"), rs.getInt("current_streak"),
                    rs.getString("last_logged_date"), badges);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void awardXpAndStreak(int userId, int xpDelta, String logDate) {
        String date = (logDate == null) ? LocalDate.now().toString() : logDate;
        GamificationState g = getGamification(userId);
        int streak = g.getCurrentStreak();
        String last = g.getLastLoggedDate();
        if (!date.equals(last)) {
            if (last != null) {
                long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(last), LocalDate.parse(date));
                if (daysBetween == 1) streak += 1;
                else if (daysBetween > 1) streak = 1;
                // daysBetween <= 0 (same day or backdated): leave streak unchanged
            } else {
                streak = 1;
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE gamification SET xp_points = xp_points + ?, current_streak=?, last_logged_date=? WHERE user_id=?")) {
            ps.setInt(1, xpDelta);
            ps.setInt(2, streak);
            ps.setString(3, date);
            ps.setInt(4, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setBadges(int userId, List<String> badges) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < badges.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(badges.get(i).replace("\"", "\\\"")).append("\"");
        }
        json.append("]");
        try (PreparedStatement ps = conn.prepareStatement("UPDATE gamification SET badges_json=? WHERE user_id=?")) {
            ps.setString(1, json.toString());
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> parseBadgesJson(String json) {
        List<String> badges = new ArrayList<>();
        if (json == null) return badges;
        json = json.trim();
        if (json.length() <= 2) return badges; // "[]"
        String inner = json.substring(1, json.length() - 1);
        for (String part : inner.split(",")) {
            String cleaned = part.trim();
            if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            if (!cleaned.isEmpty()) badges.add(cleaned);
        }
        return badges;
    }

    // -------------------------------------------------------------- backup
    public void backupTo(String destPath) throws IOException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA wal_checkpoint(FULL)");
        } catch (SQLException ignored) { }
        Files.copy(Paths.get(dbPath), Paths.get(destPath), StandardCopyOption.REPLACE_EXISTING);
    }

    public void restoreFrom(String srcPath) throws IOException, SQLException {
        conn.close();
        Files.copy(Paths.get(srcPath), Paths.get(dbPath), StandardCopyOption.REPLACE_EXISTING);
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
        }
    }

    public void close() {
        try { conn.close(); } catch (SQLException ignored) { }
    }

    // ===================================================================
    // UPDATE 1 - Automated email statement import & deduplication
    // ===================================================================

    /** SHA-256 of the fields that make a transaction "the same" for dedup purposes. */
    public static String computeDedupHash(int userId, String date, double amount, String notes) {
        String basis = userId + "|" + date + "|" + String.format("%.2f", amount) + "|"
                + (notes == null ? "" : notes.trim().toLowerCase());
        return sha256(basis);
    }

    /** True if a transaction with this message-id or dedup hash is already stored. */
    public boolean isDuplicateTransaction(int userId, String messageId, String dedupHash) {
        if (messageId != null && !messageId.isBlank()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM transactions WHERE user_id=? AND source_message_id=? LIMIT 1")) {
                ps.setInt(1, userId);
                ps.setString(2, messageId);
                if (ps.executeQuery().next()) return true;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        if (dedupHash != null && !dedupHash.isBlank()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM transactions WHERE user_id=? AND dedup_hash=? LIMIT 1")) {
                ps.setInt(1, userId);
                ps.setString(2, dedupHash);
                if (ps.executeQuery().next()) return true;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return false;
    }

    /**
     * Inserts a transaction parsed from an email/PDF only if it isn't a duplicate
     * of something already imported or logged manually. Returns true if a new row
     * was inserted, false if it was skipped as a duplicate.
     */
    public boolean addTransactionIfNew(int userId, double amount, String category, String type,
                                        String paymentMethod, String transactionDate, String notes,
                                        String sourceMessageId) {
        String date = (transactionDate == null || transactionDate.isEmpty())
                ? LocalDate.now().toString() : transactionDate;
        String dedupHash = computeDedupHash(userId, date, amount, notes);
        if (isDuplicateTransaction(userId, sourceMessageId, dedupHash)) {
            return false;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO transactions (user_id, amount, category, transaction_type, payment_method, " +
                        "transaction_date, notes, source_message_id, dedup_hash) VALUES (?,?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, userId);
            ps.setDouble(2, amount);
            ps.setString(3, category);
            ps.setString(4, type);
            ps.setString(5, paymentMethod);
            ps.setString(6, date);
            ps.setString(7, notes);
            ps.setString(8, sourceMessageId);
            ps.setString(9, dedupHash);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveEmailSyncConfig(int userId, String imapHost, int imapPort, String emailAddress,
                                     String appPasswordEnc, String passwordFormula, boolean enabled) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO email_sync_config (user_id, imap_host, imap_port, email_address, " +
                        "app_password_enc, password_formula, enabled) VALUES (?,?,?,?,?,?,?) " +
                        "ON CONFLICT(user_id) DO UPDATE SET imap_host=excluded.imap_host, " +
                        "imap_port=excluded.imap_port, email_address=excluded.email_address, " +
                        "app_password_enc=excluded.app_password_enc, password_formula=excluded.password_formula, " +
                        "enabled=excluded.enabled")) {
            ps.setInt(1, userId);
            ps.setString(2, imapHost);
            ps.setInt(3, imapPort);
            ps.setString(4, emailAddress);
            ps.setString(5, appPasswordEnc);
            ps.setString(6, passwordFormula);
            ps.setInt(7, enabled ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void touchEmailSyncLastRun(int userId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE email_sync_config SET last_sync_at=? WHERE user_id=?")) {
            ps.setString(1, java.time.LocalDateTime.now().toString());
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns null if the user has never configured email sync. */
    public Map<String, String> getEmailSyncConfig(int userId) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM email_sync_config WHERE user_id=?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return null;
            Map<String, String> cfg = new HashMap<>();
            cfg.put("imap_host", rs.getString("imap_host"));
            cfg.put("imap_port", String.valueOf(rs.getInt("imap_port")));
            cfg.put("email_address", rs.getString("email_address"));
            cfg.put("app_password_enc", rs.getString("app_password_enc"));
            cfg.put("password_formula", rs.getString("password_formula"));
            cfg.put("enabled", String.valueOf(rs.getInt("enabled")));
            cfg.put("last_sync_at", rs.getString("last_sync_at"));
            return cfg;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ===================================================================
    // UPDATE 3 - Needs vs. Wants tagging, micro-savings log, baselines
    // ===================================================================

    /** Tags a transaction "Needs" or "Wants" for the Needs vs. Wants mission. */
    public void tagTransaction(int transactionId, String tag) {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE transactions SET tag=? WHERE transaction_id=?")) {
            ps.setString(1, tag);
            ps.setInt(2, transactionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Count of expense transactions in [start,end] that already have a Needs/Wants tag. */
    public int countTaggedTransactions(int userId, String start, String end) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) AS c FROM transactions WHERE user_id=? AND transaction_type='Expense' " +
                        "AND transaction_date BETWEEN ? AND ? AND tag IS NOT NULL AND tag != ''")) {
            ps.setInt(1, userId);
            ps.setString(2, start);
            ps.setString(3, end);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt("c");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** Count of all expense transactions in [start,end], tagged or not. */
    public int countExpenseTransactions(int userId, String start, String end) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) AS c FROM transactions WHERE user_id=? AND transaction_type='Expense' " +
                        "AND transaction_date BETWEEN ? AND ?")) {
            ps.setInt(1, userId);
            ps.setString(2, start);
            ps.setString(3, end);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getInt("c");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** True if the user has logged at least one transaction today - the Daily Micro-Check mission. */
    public boolean hasLoggedToday(int userId) {
        String today = LocalDate.now().toString();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM transactions WHERE user_id=? AND transaction_date=? LIMIT 1")) {
            ps.setInt(1, userId);
            ps.setString(2, today);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void recordMicroSaving(int userId, String logDate, double amountSaved, String note) {
        String date = (logDate == null || logDate.isEmpty()) ? LocalDate.now().toString() : logDate;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO micro_savings_log (user_id, log_date, amount_saved, note) VALUES (?,?,?,?)")) {
            ps.setInt(1, userId);
            ps.setString(2, date);
            ps.setDouble(3, amountSaved);
            ps.setString(4, note);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public double sumMicroSavings(int userId, String start, String end) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(amount_saved),0) AS total FROM micro_savings_log " +
                        "WHERE user_id=? AND log_date BETWEEN ? AND ?")) {
            ps.setInt(1, userId);
            ps.setString(2, start);
            ps.setString(3, end);
            ResultSet rs = ps.executeQuery();
            rs.next();
            return rs.getDouble("total");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Average weekly spend in a category over the last N completed weeks
     * (excluding the current, in-progress week) - the user's own baseline,
     * used by the "Beat Your Baseline" mission instead of a hardcoded cap.
     */
    public double averageWeeklySpend(int userId, String category, int weeksBack) {
        LocalDate thisWeekStart = LocalDate.now().with(
                java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate periodStart = thisWeekStart.minusWeeks(weeksBack);
        LocalDate periodEnd = thisWeekStart.minusDays(1);
        if (!periodStart.isBefore(periodEnd)) return 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(SUM(amount),0) AS total FROM transactions WHERE user_id=? " +
                        "AND transaction_type='Expense' AND category=? AND transaction_date BETWEEN ? AND ?")) {
            ps.setInt(1, userId);
            ps.setString(2, category);
            ps.setString(3, periodStart.toString());
            ps.setString(4, periodEnd.toString());
            ResultSet rs = ps.executeQuery();
            rs.next();
            double total = rs.getDouble("total");
            return total / weeksBack;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

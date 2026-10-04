package com.financeanalyser.email;

/**
 * IMAP + PDF-password configuration for Update 1 (automated bank-email import).
 *
 * SECURITY NOTE: appPassword is obfuscated with a simple reversible XOR
 * cipher before being written to SQLite via obfuscate()/deobfuscate() -
 * this stops the password from sitting in the .db file as plain text, but
 * it is NOT strong encryption (there is no secure place to hide the XOR key
 * in an open-source desktop app). Two things matter more for real safety:
 *   1. Always use a dedicated Gmail/Outlook "App Password", never the
 *      account's real password - an app password can be revoked on its own
 *      without changing your real login, and it usually can't be used to
 *      sign in to the account interactively.
 *   2. The database file (finance_analyser.db) is already excluded from git
 *      via .gitignore - keep it that way, and treat any backup of it as
 *      sensitive.
 */
public class EmailSyncConfig {

    private static final byte[] XOR_KEY = "FolioLocalObfuscationKey!".getBytes();

    public String imapHost = "imap.gmail.com";
    public int imapPort = 993;
    public String emailAddress;
    public String appPassword;       // plaintext in memory only - never logged, never written as-is
    public String passwordFormula;   // free-text hint, e.g. "name+DOBddMMyyyy" - shown back to the user
    public boolean enabled;
    public String lastSyncAt;

    public static String obfuscate(String plain) {
        if (plain == null) return null;
        return java.util.Base64.getEncoder().encodeToString(xor(plain.getBytes()));
    }

    public static String deobfuscate(String stored) {
        if (stored == null || stored.isBlank()) return "";
        try {
            return new String(xor(java.util.Base64.getDecoder().decode(stored)));
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static byte[] xor(byte[] data) {
        byte[] out = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ XOR_KEY[i % XOR_KEY.length]);
        }
        return out;
    }
}

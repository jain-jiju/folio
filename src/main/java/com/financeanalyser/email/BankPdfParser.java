package com.financeanalyser.email;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opens a password-protected bank-statement PDF (in memory, straight from
 * the email attachment bytes - nothing is written to disk unencrypted
 * except the PDF itself, which the caller is responsible for deleting
 * after parsing) and extracts transaction rows with a couple of generic
 * regex layouts.
 *
 * LIMITATION: every bank formats its statement table differently. The two
 * patterns below cover the two most common layouts (a single amount column
 * with a trailing Dr/Cr marker, and separate Debit/Credit/Balance columns),
 * but a real deployment should expect to tune ROW_WITH_DRCR / ROW_DEBIT_CREDIT
 * (or add a third pattern) once you see your own bank's actual text layout -
 * run extractRawText() once on a real statement and look at it.
 */
public class BankPdfParser {

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
    );

    // "01/02/2024  UPI/VENDOR NAME/REF  1,250.00 Dr"  (or Cr, Debit, Credit)
    private static final Pattern ROW_WITH_DRCR = Pattern.compile(
            "(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})\\s+(.+?)\\s+([\\d,]+\\.\\d{2})\\s*(Dr|Cr|DR|CR|Debit|Credit)\\b",
            Pattern.CASE_INSENSITIVE);

    // "01/02/2024  VENDOR NAME   500.00   -   24500.00"  (date, desc, debit, credit, balance)
    private static final Pattern ROW_DEBIT_CREDIT = Pattern.compile(
            "(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4})\\s+(.+?)\\s+([\\d,]+\\.\\d{2}|-)\\s+([\\d,]+\\.\\d{2}|-)\\s+([\\d,]+\\.\\d{2})$");

    private static final Map<String, String[]> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    static {
        CATEGORY_KEYWORDS.put("Food", new String[]{"swiggy", "zomato", "restaurant", "cafe", "food"});
        CATEGORY_KEYWORDS.put("Travel", new String[]{"uber", "ola", "irctc", "fuel", "petrol", "metro"});
        CATEGORY_KEYWORDS.put("Utilities", new String[]{"electricity", "recharge", "broadband", "dth", "bill"});
        CATEGORY_KEYWORDS.put("Subscriptions", new String[]{"netflix", "spotify", "prime", "hotstar"});
        CATEGORY_KEYWORDS.put("Shopping", new String[]{"amazon", "flipkart", "myntra", "retail"});
    }

    /** Tries each candidate password in order; returns the raw extracted text, or null if none worked. */
    public String extractRawText(byte[] pdfBytes, List<String> candidatePasswords) throws IOException {
        // Always try an empty password first - many statements aren't actually encrypted.
        List<String> attempts = new ArrayList<>();
        attempts.add("");
        attempts.addAll(candidatePasswords);

        for (String password : attempts) {
            try (PDDocument doc = PDDocument.load(pdfBytes, password == null ? "" : password)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(doc);
            } catch (org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException wrongPassword) {
                // try the next candidate
            }
        }
        return null; // exhausted every candidate - caller should ask the user for the password
    }

    public List<ParsedStatementRow> parseTransactionRows(String rawText) {
        List<ParsedStatementRow> rows = new ArrayList<>();
        if (rawText == null) return rows;

        for (String line : rawText.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            Matcher m1 = ROW_WITH_DRCR.matcher(trimmed);
            if (m1.find()) {
                ParsedStatementRow row = buildRow(m1.group(1), m1.group(2), m1.group(3),
                        m1.group(4).toLowerCase().startsWith("c") ? "Income" : "Expense");
                if (row != null) rows.add(row);
                continue;
            }

            Matcher m2 = ROW_DEBIT_CREDIT.matcher(trimmed);
            if (m2.find()) {
                String debit = m2.group(3);
                String credit = m2.group(4);
                boolean isDebit = !debit.equals("-");
                String amountStr = isDebit ? debit : credit;
                if (amountStr.equals("-")) continue;
                ParsedStatementRow row = buildRow(m2.group(1), m2.group(2), amountStr,
                        isDebit ? "Expense" : "Income");
                if (row != null) rows.add(row);
            }
        }
        return rows;
    }

    private ParsedStatementRow buildRow(String rawDate, String description, String rawAmount, String type) {
        LocalDate date = parseDate(rawDate);
        if (date == null) return null;
        double amount;
        try {
            amount = Double.parseDouble(rawAmount.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        if (amount <= 0) return null;
        ParsedStatementRow row = new ParsedStatementRow(date.toString(), description.trim(), amount, type);
        row.category = guessCategory(description);
        return row;
    }

    private static LocalDate parseDate(String raw) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, fmt);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    private static String guessCategory(String description) {
        String lower = description.toLowerCase();
        for (Map.Entry<String, String[]> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) return entry.getKey();
            }
        }
        return "Misc";
    }
}

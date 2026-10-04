package com.financeanalyser.email;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex fallback for the plain-text/HTML "transaction alert" emails banks
 * send instantly (as opposed to a PDF statement at month-end) - e.g.
 * "Rs.500.00 debited from A/c XX1234 on 02-Feb-24 to VPA vendor@upi" or
 * "INR 1,200 credited to your account ending 1234 on 02/02/2024".
 *
 * Covers the common phrasings; a bank whose wording differs will need its
 * own pattern added to AMOUNT_DEBIT / AMOUNT_CREDIT below.
 */
public class EmailAlertRegexParser {

    private static final Pattern AMOUNT_DEBIT = Pattern.compile(
            "(?:Rs\\.?|INR)\\s?([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:is\\s)?(?:debited|spent|paid|withdrawn)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_CREDIT = Pattern.compile(
            "(?:Rs\\.?|INR)\\s?([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:is\\s)?(?:credited|received|deposited)",
            Pattern.CASE_INSENSITIVE);

    // "to VPA foo@bank", "to <NAME>", "at <MERCHANT>"
    private static final Pattern VENDOR = Pattern.compile(
            "(?:to|at)\\s+(?:VPA\\s+)?([A-Za-z0-9@._\\-\\s]{3,40}?)(?:\\s+on\\b|\\s+ref\\b|[.,]|$)",
            Pattern.CASE_INSENSITIVE);

    /** Strips HTML tags down to plain text so the same regexes work on either body type. */
    public static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("(?s)<[^>]*>", " ").replaceAll("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }

    /** Returns null if neither a debit nor a credit amount could be found. */
    public static ParsedStatementRow parse(String bodyText, LocalDate receivedDate) {
        if (bodyText == null) return null;

        Matcher debitMatcher = AMOUNT_DEBIT.matcher(bodyText);
        Matcher creditMatcher = AMOUNT_CREDIT.matcher(bodyText);

        String type;
        double amount;
        if (debitMatcher.find()) {
            type = "Expense";
            amount = parseAmount(debitMatcher.group(1));
        } else if (creditMatcher.find()) {
            type = "Income";
            amount = parseAmount(creditMatcher.group(1));
        } else {
            return null;
        }
        if (amount <= 0) return null;

        String vendor = "Bank alert";
        Matcher vendorMatcher = VENDOR.matcher(bodyText);
        if (vendorMatcher.find()) {
            vendor = vendorMatcher.group(1).trim();
        }

        ParsedStatementRow row = new ParsedStatementRow(receivedDate.toString(), vendor, amount, type);
        row.category = guessCategory(bodyText);
        return row;
    }

    private static double parseAmount(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String guessCategory(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("swiggy") || lower.contains("zomato")) return "Food";
        if (lower.contains("uber") || lower.contains("ola") || lower.contains("irctc")) return "Travel";
        if (lower.contains("netflix") || lower.contains("spotify") || lower.contains("prime")) return "Subscriptions";
        if (lower.contains("amazon") || lower.contains("flipkart")) return "Shopping";
        if (lower.contains("electricity") || lower.contains("recharge") || lower.contains("bill")) return "Utilities";
        return "Misc";
    }
}

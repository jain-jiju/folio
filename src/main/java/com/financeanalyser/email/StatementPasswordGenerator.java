package com.financeanalyser.email;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates candidate passwords for password-protected bank-statement PDFs
 * using the formula patterns most Indian banks (and many others) actually
 * use: pieces of the account holder's name, date of birth, and mobile
 * number, combined a handful of standard ways. BankPdfParser tries every
 * candidate in order and stops at the first one that opens the file, so
 * this only needs to be reasonably complete, not exact.
 *
 * None of this guesses a password for someone else's account - it only
 * reconstructs a password the user already knows the recipe for (their own
 * bank told them the formula when the statement was set up), so they don't
 * have to type it in by hand every time a statement email arrives.
 */
public class StatementPasswordGenerator {

    public static List<String> generateCandidates(String fullName, LocalDate dob, String mobileNumber) {
        List<String> candidates = new ArrayList<>();

        String nameUpper = fullName == null ? "" : fullName.trim().toUpperCase().replaceAll("\\s+", "");
        String first4Name = nameUpper.length() >= 4 ? nameUpper.substring(0, 4) : nameUpper;
        String firstName = fullName == null ? "" : fullName.trim().split("\\s+")[0].toUpperCase();

        String ddMMyyyy = "", ddMMyy = "", MMyyyy = "", yyyyMMdd = "", ddMM = "";
        if (dob != null) {
            ddMMyyyy = dob.format(DateTimeFormatter.ofPattern("ddMMyyyy"));
            ddMMyy = dob.format(DateTimeFormatter.ofPattern("ddMMyy"));
            MMyyyy = dob.format(DateTimeFormatter.ofPattern("MMyyyy"));
            yyyyMMdd = dob.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            ddMM = dob.format(DateTimeFormatter.ofPattern("ddMM"));
        }

        String last4Mobile = (mobileNumber != null && mobileNumber.length() >= 4)
                ? mobileNumber.substring(mobileNumber.length() - 4) : "";

        // --- common single-field formulas ---
        if (!ddMMyyyy.isEmpty()) candidates.add(ddMMyyyy);
        if (!ddMMyy.isEmpty()) candidates.add(ddMMyy);
        if (!last4Mobile.isEmpty()) candidates.add(last4Mobile);

        // --- name + DOB combinations (the most common bank formula) ---
        if (!first4Name.isEmpty() && !ddMMyyyy.isEmpty()) {
            candidates.add(first4Name + ddMMyyyy);
            candidates.add(first4Name + ddMM);
            candidates.add(ddMMyyyy + first4Name);
        }
        if (!firstName.isEmpty() && !ddMMyyyy.isEmpty()) {
            candidates.add(firstName + ddMMyyyy);
            candidates.add(firstName.substring(0, Math.min(3, firstName.length())) + ddMMyy);
        }

        // --- name + mobile combinations ---
        if (!first4Name.isEmpty() && !last4Mobile.isEmpty()) {
            candidates.add(first4Name + last4Mobile);
            candidates.add(last4Mobile + first4Name);
        }

        // --- DOB + mobile ---
        if (!ddMM.isEmpty() && !last4Mobile.isEmpty()) {
            candidates.add(ddMM + last4Mobile);
        }

        if (!MMyyyy.isEmpty()) candidates.add(MMyyyy);
        if (!yyyyMMdd.isEmpty()) candidates.add(yyyyMMdd);

        return candidates;
    }
}

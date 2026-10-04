package com.financeanalyser.email;

import java.util.ArrayList;
import java.util.List;

/** Summary returned by TransactionImportService.syncNow() for the Settings UI to show. */
public class ImportResult {
    public int emailsScanned;
    public int transactionsImported;
    public int duplicatesSkipped;
    public final List<String> errors = new ArrayList<>();

    public String summary() {
        if (!errors.isEmpty()) {
            return String.format("Scanned %d email(s): imported %d, skipped %d duplicate(s), %d error(s) - see details.",
                    emailsScanned, transactionsImported, duplicatesSkipped, errors.size());
        }
        return String.format("Scanned %d email(s): imported %d transaction(s), skipped %d duplicate(s).",
                emailsScanned, transactionsImported, duplicatesSkipped);
    }
}

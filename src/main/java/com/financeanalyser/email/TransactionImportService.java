package com.financeanalyser.email;

import com.financeanalyser.db.DatabaseManager;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;

/**
 * Orchestrates Update 1: connects to IMAP, finds unread bank emails, routes
 * each one to the PDF parser or the regex fallback, and writes new
 * transactions through DatabaseManager.addTransactionIfNew() (which does
 * the dedup check). Call syncNow() from a background thread (e.g. a
 * JavaFX Task) - IMAP round trips are slow and must never block the UI
 * thread.
 */
public class TransactionImportService {

    /** Only emails whose subject contains one of these (case-insensitive) are considered. */
    private static final String[] SUBJECT_KEYWORDS = {
            "statement", "transaction alert", "debited", "credited", "e-statement",
            "account summary", "upi", "payment"
    };

    private final DatabaseManager db;
    private final int userId;
    private final EmailSyncConfig config;
    private final List<String> pdfPasswordCandidates;

    public TransactionImportService(DatabaseManager db, int userId, EmailSyncConfig config,
                                     List<String> pdfPasswordCandidates) {
        this.db = db;
        this.userId = userId;
        this.config = config;
        this.pdfPasswordCandidates = pdfPasswordCandidates;
    }

    public ImportResult syncNow() {
        ImportResult result = new ImportResult();

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", config.imapHost);
        props.put("mail.imaps.port", String.valueOf(config.imapPort));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "15000");

        Session session = Session.getInstance(props);
        Store store = null;
        Folder inbox = null;
        try {
            store = session.getStore("imaps");
            store.connect(config.imapHost, config.emailAddress, config.appPassword);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] unseen = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));
            for (Message message : unseen) {
                if (!matchesKeywords(message)) continue;
                result.emailsScanned++;
                try {
                    processMessage(message, result);
                } catch (Exception perMessage) {
                    result.errors.add("Message \"" + safeSubject(message) + "\": " + perMessage.getMessage());
                } finally {
                    message.setFlag(Flags.Flag.SEEN, true);
                }
            }
            db.touchEmailSyncLastRun(userId);
        } catch (Exception e) {
            result.errors.add("Connection failed: " + e.getMessage());
        } finally {
            closeQuietly(inbox);
            closeQuietly(store);
        }
        return result;
    }

    private boolean matchesKeywords(Message message) {
        try {
            String subject = message.getSubject();
            if (subject == null) return false;
            String lower = subject.toLowerCase();
            for (String kw : SUBJECT_KEYWORDS) {
                if (lower.contains(kw)) return true;
            }
            return false;
        } catch (MessagingException e) {
            return false;
        }
    }

    private void processMessage(Message message, ImportResult result) throws MessagingException, IOException {
        String messageId = firstHeaderOrNull(message, "Message-ID");
        LocalDate receivedDate = message.getReceivedDate() != null
                ? message.getReceivedDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                : LocalDate.now();

        byte[] pdfAttachment = findPdfAttachment(message);
        List<ParsedStatementRow> rows;

        if (pdfAttachment != null) {
            BankPdfParser pdfParser = new BankPdfParser();
            String text = pdfParser.extractRawText(pdfAttachment, pdfPasswordCandidates);
            if (text == null) {
                result.errors.add("Could not open PDF in \"" + safeSubject(message)
                        + "\" with any configured password.");
                return;
            }
            rows = pdfParser.parseTransactionRows(text);
        } else {
            String bodyText = EmailAlertRegexParser.stripHtml(extractText(message));
            ParsedStatementRow row = EmailAlertRegexParser.parse(bodyText, receivedDate);
            rows = row == null ? List.of() : List.of(row);
        }

        for (ParsedStatementRow row : rows) {
            boolean inserted = db.addTransactionIfNew(userId, row.amount, row.category, row.type,
                    "Bank sync", row.date, row.description, messageId);
            if (inserted) result.transactionsImported++;
            else result.duplicatesSkipped++;
        }
    }

    // ------------------------------------------------------------- helpers

    private static byte[] findPdfAttachment(Part part) throws MessagingException, IOException {
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            for (int i = 0; i < mp.getCount(); i++) {
                byte[] found = findPdfAttachment(mp.getBodyPart(i));
                if (found != null) return found;
            }
            return null;
        }
        String disposition = part.getDisposition();
        String filename = part.getFileName();
        boolean looksLikePdf = filename != null && filename.toLowerCase().endsWith(".pdf");
        if (looksLikePdf && (disposition == null || Part.ATTACHMENT.equalsIgnoreCase(disposition)
                || Part.INLINE.equalsIgnoreCase(disposition))) {
            try (InputStream in = part.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                in.transferTo(out);
                return out.toByteArray();
            }
        }
        return null;
    }

    private static String extractText(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain") || part.isMimeType("text/html")) {
            Object content = part.getContent();
            return content == null ? "" : content.toString();
        }
        if (part.isMimeType("multipart/alternative")) {
            Multipart mp = (Multipart) part.getContent();
            // prefer the last part (usually text/html, which carries more detail)
            String best = "";
            for (int i = 0; i < mp.getCount(); i++) {
                best = extractText(mp.getBodyPart(i));
            }
            return best;
        }
        if (part.isMimeType("multipart/*")) {
            Multipart mp = (Multipart) part.getContent();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                sb.append(extractText(mp.getBodyPart(i))).append(' ');
            }
            return sb.toString();
        }
        return "";
    }

    private static String firstHeaderOrNull(Message message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        return (values != null && values.length > 0) ? values[0] : null;
    }

    private static String safeSubject(Message message) {
        try {
            return message.getSubject();
        } catch (MessagingException e) {
            return "(unknown subject)";
        }
    }

    private static void closeQuietly(Folder f) {
        if (f != null && f.isOpen()) {
            try { f.close(false); } catch (MessagingException ignored) { }
        }
    }

    private static void closeQuietly(Store s) {
        if (s != null) {
            try { s.close(); } catch (MessagingException ignored) { }
        }
    }
}

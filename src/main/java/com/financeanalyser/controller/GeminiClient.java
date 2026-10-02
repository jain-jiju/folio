package com.financeanalyser.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gemini AI Copilot client.
 * - Reads the API key from resources/config.properties (set via the Backup & Security tab).
 * - If no key is configured, or the HTTPS call fails (e.g. offline), every method
 *   degrades gracefully to a local rule-based fallback, so the app keeps working
 *   with zero configuration (100% privacy-first / offline requirement).
 */
public class GeminiClient {

    private static final Path CONFIG_PATH = Paths.get("config.properties");
    private static final Map<String, String[]> CATEGORY_KEYWORDS = new LinkedHashMap<>();
    static {
        CATEGORY_KEYWORDS.put("Food", new String[]{"food", "lunch", "dinner", "breakfast", "snack", "restaurant", "swiggy", "zomato", "coffee", "tea"});
        CATEGORY_KEYWORDS.put("Travel", new String[]{"auto", "cab", "uber", "ola", "bus", "train", "petrol", "fuel", "ride", "taxi", "metro"});
        CATEGORY_KEYWORDS.put("Utilities", new String[]{"electricity", "water bill", "recharge", "wifi", "internet", "bill", "utility"});
        CATEGORY_KEYWORDS.put("Subscriptions", new String[]{"netflix", "spotify", "subscription", "prime", "hotstar", "youtube premium"});
        CATEGORY_KEYWORDS.put("Shopping", new String[]{"shopping", "clothes", "amazon", "flipkart", "shoes", "bought"});
        CATEGORY_KEYWORDS.put("Training", new String[]{"course", "book", "certification", "workshop", "udemy", "class"});
    }

    private final String model;
    private final HttpClient httpClient;

    public GeminiClient() {
        this("gemini-3.8-flash");
    }

    public GeminiClient(String model) {
        this.model = model;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    public static void saveApiKey(String apiKey) {
        try {
            Properties props = loadProps();
            props.setProperty("gemini_api_key", apiKey);
            try (var out = Files.newOutputStream(CONFIG_PATH)) {
                props.store(out, "Personal Finance Analyser local config");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getApiKey() {
        return loadProps().getProperty("gemini_api_key", "");
    }

    private static Properties loadProps() {
        Properties props = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (var in = Files.newInputStream(CONFIG_PATH)) {
                props.load(in);
            } catch (IOException ignored) { }
        }
        return props;
    }

    public static class ParsedExpense {
        public double amount;
        public String category = "Misc";
        public String paymentMethod = "UPI";
        public String notes = "";
    }

    private static String guessCategory(String text) {
        String lower = text.toLowerCase();
        for (Map.Entry<String, String[]> entry : CATEGORY_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw)) return entry.getKey();
            }
        }
        return "Misc";
    }

    /** Regex fallback: extracts an amount and guesses category/payment method from casual text. */
    public static ParsedExpense localParseExpense(String text) {
        Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(text);
        if (!m.find()) return null;
        ParsedExpense parsed = new ParsedExpense();
        parsed.amount = Double.parseDouble(m.group(1));
        parsed.category = guessCategory(text);
        String lower = text.toLowerCase();
        if (lower.contains("cash")) parsed.paymentMethod = "Cash";
        else if (lower.contains("card")) parsed.paymentMethod = "Card";
        else parsed.paymentMethod = "UPI";
        parsed.notes = text.trim();
        return parsed;
    }

    private String call(String prompt, String system) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) return null;
        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model
                    + ":generateContent?key=" + apiKey;
            String combined = system.isEmpty() ? prompt : system + "\n\n" + prompt;

            JsonObject part = new JsonObject();
            part.addProperty("text", combined);
            JsonArray parts = new JsonArray();
            parts.add(part);
            JsonObject content = new JsonObject();
            content.add("parts", parts);
            JsonArray contents = new JsonArray();
            contents.add(content);
            JsonObject payload = new JsonObject();
            payload.add("contents", contents);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;

            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            return root.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts").get(0).getAsJsonObject()
                    .get("text").getAsString();
        } catch (Exception e) {
            return null; // any network/parsing failure -> caller falls back to offline logic
        }
    }

    public ParsedExpense parseExpense(String text) {
        String system = "Extract a single expense from the user's message. Reply ONLY with compact JSON: " +
                "{\"amount\": number, \"category\": one of [Food,Travel,Utilities,Subscriptions,Shopping,Training,Misc], " +
                "\"payment_method\": one of [UPI,Card,Cash], \"notes\": string}. No prose, no markdown.";
        String raw = call(text, system);
        if (raw != null) {
            try {
                String cleaned = raw.trim().replace("```json", "").replace("```", "").trim();
                JsonObject obj = JsonParser.parseString(cleaned).getAsJsonObject();
                ParsedExpense parsed = new ParsedExpense();
                parsed.amount = obj.get("amount").getAsDouble();
                parsed.category = obj.has("category") ? obj.get("category").getAsString() : "Misc";
                parsed.paymentMethod = obj.has("payment_method") ? obj.get("payment_method").getAsString() : "UPI";
                parsed.notes = obj.has("notes") ? obj.get("notes").getAsString() : text;
                return parsed;
            } catch (Exception ignored) {
                // fall through to local parsing
            }
        }
        return localParseExpense(text);
    }

    public String chatReply(String message, Map<String, Object> context) {
        String system = "You are a friendly, concise personal finance copilot inside a student's budgeting app. " +
                "Use the given context about their spending to answer in 2-4 short sentences. Context: " + context;
        String raw = call(message, system);
        if (raw != null) return raw.trim();
        return cannedReply(message, context);
    }

    private static String cannedReply(String message, Map<String, Object> context) {
        double allowance = context.get("todayAllowance") == null ? 0 : (double) context.get("todayAllowance");
        double spent = context.get("spentToday") == null ? 0 : (double) context.get("spentToday");
        double remaining = allowance - spent;
        String lower = message.toLowerCase();
        if (lower.contains("track") || lower.contains("today")) {
            if (remaining >= 0) {
                return String.format("You're on track - you've spent \u20B9%.0f of today's \u20B9%.0f allowance, \u20B9%.0f left.",
                        spent, allowance, remaining);
            }
            return String.format("You're \u20B9%.0f over today's \u20B9%.0f allowance. Consider skipping non-essential spends for the rest of the day.",
                    Math.abs(remaining), allowance);
        }
        if (lower.contains("save")) {
            return "Try trimming your top discretionary category by 10-15%, and route the difference into savings "
                    + "before the month resets - small, consistent cuts compound fast.";
        }
        return "I can see your current spending context, but I'm running in offline mode (no Gemini API key set). "
                + "Add a key in Backup & Security for richer answers - meanwhile: keep logging daily to protect your streak!";
    }
}

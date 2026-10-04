# Folio — Personal Finance Analyser (Java / JavaFX)

A privacy-first, fully offline-first desktop personal-finance app for a computer-science
mini-project. Light "Bento Grid" UI (black/orange/blue/cream card layout), a local-password lock
screen, local SQLite storage, in-process forecasting engine, automated bank-email transaction
import, a restraint-focused missions system, and an optional Gemini AI copilot. Pure Java - no
Python, no external processes.

## Stack
- **GUI**: Java 17+ / JavaFX 21, hand-built scene graph (MVC: `db/` = model access, `controller/` =
  business logic, `view/` = JavaFX UI)
- **Styling**: `resources/glassmorphism.css` - JavaFX's CSS3 subset, light Bento Grid theme
  (black/orange/blue/cream rounded cards)
- **Access control**: a lock screen (`LoginView`) checks the stored password hash via
  `DatabaseManager.verifyPassword()` before the main window is built
- **Storage**: SQLite3 (`finance_analyser.db`) via the `sqlite-jdbc` embedded driver - zero
  configuration, created automatically on first run
- **Analytics/Forecasting**: pure-Java moving-average & velocity forecasting (`ForecastEngine`) -
  runs in-process, no external sidecar needed
- **Charts**: native JavaFX `PieChart`, `LineChart`, `BarChart`
- **Automated bank-email import**: `jakarta.mail` IMAP client reads unread bank-alert emails,
  decrypts attached statement PDFs with PDFBox using guessed name/DOB/mobile password formulas,
  and falls back to regex parsing for plain-text/HTML alert emails - all deduplicated against
  existing transactions (`email/` package)
- **Missions**: a restraint-and-awareness missions system (`MissionEvaluator`) - daily logging
  check-ins, Needs vs. Wants tagging, a micro-savings log, and "beat your own historical average"
  per category - no mission can be completed by spending more
- **AI Copilot**: Google Gemini (model configurable in `GeminiClient`) via `java.net.http.HttpClient`
  + Gson, with a built-in offline rule-based fallback so the app is 100% usable with zero API key
- **PDF export**: Apache PDFBox

## Project structure
```
finance-analyser-java/
├── pom.xml                       # Maven build (JavaFX, sqlite-jdbc, gson, pdfbox)
├── src/main/resources/
│   ├── schema.sql                 # normalized SQLite schema
│   └── glassmorphism.css          # light Bento Grid theme
└── src/main/java/com/financeanalyser/
    ├── Main.java                  # JavaFX entry point - shows LoginView, then builds MainWindow
    ├── db/
    │   └── DatabaseManager.java    # all SQLite access - single source of truth for queries
    ├── model/                     # plain POJOs: User, Transaction (now with a Needs/Wants tag),
    │                               # SalarySegmentation, GamificationState, CategoryTotal,
    │                               # DateTotal, Mission, MissionType
    ├── controller/
    │   ├── ForecastEngine.java     # allowance, projection, MoM growth, segmentation presets
    │   ├── GamificationEngine.java # XP / streak / badge rules
    │   ├── MissionEvaluator.java   # daily check-in, tagging, micro-savings, beat-your-baseline
    │   └── GeminiClient.java       # Gemini REST client + offline fallback parser
    ├── email/                     # Update: automated bank-statement email import
    │   ├── EmailSyncConfig.java        # IMAP + PDF-password config model (app password obfuscated)
    │   ├── StatementPasswordGenerator.java # name/DOB/mobile candidate PDF passwords
    │   ├── BankPdfParser.java          # PDFBox decrypt + regex transaction-row extraction
    │   ├── EmailAlertRegexParser.java  # regex fallback for plain-text/HTML bank alerts
    │   ├── ParsedStatementRow.java     # one extracted transaction row
    │   ├── ImportResult.java           # scanned/imported/duplicate/error summary
    │   └── TransactionImportService.java # orchestrates IMAP -> parse -> dedup -> DB insert
    └── view/
        ├── LoginView.java           # local-password lock screen shown before MainWindow
        ├── MainWindow.java          # Folio sidebar (logo, nav, live XP card, profile footer)
        │                             # + header + tab stack + floating chat host
        ├── DashboardView.java       # "Overview" tab
        ├── TransactionsView.java    # "Transactions" tab (includes the Needs/Wants Tag column)
        ├── PlannerView.java         # "Budget planner" tab
        ├── AnalyticsView.java       # "Analytics" tab
        ├── GamificationView.java    # "Missions" tab (badges + the 4 redesigned missions +
        │                             # micro-saving quick-log box)
        ├── ReportsView.java         # "Reports" tab
        ├── SettingsView.java        # "Security" tab (profile, password, backup, Gemini key,
        │                             # bank email sync)
        └── AIChatWidget.java        # Chat panel toggled from the header icon (Gemini-backed)
```

## Prerequisites
- **JDK 17 or newer** (JDK 21 recommended to match JavaFX 21) - a *full JDK*, not just a JRE
  (you need `javac`). Check with: `javac -version`
- **Maven 3.8+** - check with: `mvn -version`

## Setup & Run
```bash
cd finance-analyser-java
mvn clean javafx:run
```
The `javafx-maven-plugin` resolves the correct native JavaFX modules for your OS automatically -
no manual `--module-path`/`--add-modules` flags needed.

On first launch the app creates `finance_analyser.db` in the working directory and seeds a
default local profile named "Alex Morgan". Default local password is `changeme` - change it,
and set your own name and profile picture, from the **Security** tab.

## Building a runnable jar (optional)
```bash
mvn clean package
java -jar target/personal-finance-analyser.jar
```
> **Note:** JavaFX's native libraries are platform-specific. The shaded jar built above will run
> on the same OS/architecture you built it on. For a cross-platform distributable, prefer
> `jpackage` (bundled with the JDK) or run via `mvn javafx:run` on each target machine instead.

## Using the AI Copilot without an API key
Open the chat panel from the chat icon in the header (`AIChatWidget`) - it works out of the box:
- Natural-language expense logging ("Spent 150 on auto ride") is parsed with a local
  keyword/regex model (`GeminiClient.localParseExpense`).
- Chat questions ("Am I on track today?", "How can I save ₹2,000?") get deterministic,
  context-aware canned answers computed from your real data.

To upgrade to real Gemini responses, get a free key from https://aistudio.google.com/apikey and
paste it into **Security → Gemini API configuration**. No restart needed - the key is stored in
`config.properties` next to the jar/working directory. The app only ever talks to Gemini (or the
local offline fallback above) - there is no local/on-device LLM, so no extra setup (Ollama, GPU,
etc.) is needed for the chatbot.

## Setting up automated bank-email sync
In **Security → Bank email sync**:
1. Enter your email provider's IMAP host (e.g. `imap.gmail.com`) and port (`993`).
2. Enter the email address that receives your bank's e-statements/alerts, and an **app password**
   (for Gmail: Google Account → Security → 2-Step Verification → App passwords - never use your
   real account password here, since it's only lightly obfuscated, not strongly encrypted, in the
   local database).
3. Enter your full name, date of birth, and mobile number - these feed
   `StatementPasswordGenerator` to guess the password on password-protected statement PDFs (most
   Indian banks use some combination of these).
4. Click **Sync now**. The app scans unread mail matching common bank-alert subjects, decrypts and
   parses attached PDFs (or falls back to regex parsing of the email body for plain-text/HTML
   alerts), and inserts any new transactions - duplicates are skipped automatically via a hash of
   (date, amount, notes) plus the email's Message-ID.

## Notes on this build
- You must log in with the local password (default `changeme`) every time the app starts -
  `LoginView.verifyPassword()` gates access to `MainWindow`. Change the password from the
  **Security** tab.
- `Ctrl+N` from anywhere focuses the Overview tab's Amount field for fast logging; `Enter` submits.
- Category ceilings, badge eligibility (Budget Master / Saver Ninja / Impulse Control Hero) and
  the spending-pulse/forecast numbers are all computed live from your active salary segmentation -
  set one on the Overview or Budget planner tab first.
- Missions (Missions tab) are intentionally *not* spending-cap based - you can't complete a
  mission by spending more. The four mission types (Daily Micro-Check, Needs vs. Wants Tagging,
  Micro-Swap Savings Log, Beat Your Baseline) reward logging consistently, tagging transactions
  Needs/Wants, logging small deliberate savings, and spending less than your own historical
  average in a category. Tag transactions from the new **Tag** column on the Transactions tab.
- CSV import (Transactions tab) accepts headers `date/amount/category/type/payment/description`
  (case-insensitive, some aliases supported) and handles basic quoted fields.
- The segmentation and daily-adherence "rings" use JavaFX shapes (no punched-out center hole via
  native chart support); functionally identical data, close visual approximation of a donut.
- Student/Roll No/Semester fields have been fully removed from the app (model, database writes,
  and PDF reports) - only a name and an optional profile picture are stored, editable from the
  Security tab, matching your updated design.
- This is a single-user local app - the `users` table supports multiple rows in the schema for
  future multi-profile support, but the UI currently drives one profile at a time.
- Automated bank-email sync and the password-guessing it relies on are best-effort - formats vary
  a lot bank to bank. If a statement PDF can't be decrypted or parsed, nothing is inserted and the
  sync summary will report it as an error rather than guessing at transaction data.

## A note on how this was built
This code has been compiled and run for real on the user's own machine (`mvn clean javafx:run`),
not just reviewed line-by-line. Several real issues surfaced and were fixed during that process -
an invalid import in `LoginView`, two separate SQLite driver issues triggered by how the schema
migration SQL was being split and executed, and (during an earlier, since-removed experiment with
a local on-device chatbot) a tool-calling timeout. All of those are fixed in this build. If
anything still fails to compile or run on your machine, send the exact error and it'll get fixed.

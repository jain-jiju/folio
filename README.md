# Folio — Personal Finance Analyser (Java / JavaFX)

A privacy-first, fully offline desktop personal-finance app for a computer-science mini-project.
Light "Bento Grid" UI (black/orange/blue/cream card layout), local SQLite storage, in-process
forecasting engine, gamified budgeting, and an optional Gemini AI copilot. Pure Java - no Python,
no external processes.

## Stack
- **GUI**: Java 17+ / JavaFX 21, hand-built scene graph (MVC: `db/` = model access, `controller/` =
  business logic, `view/` = JavaFX UI)
- **Styling**: `resources/glassmorphism.css` - JavaFX's CSS3 subset, light Bento Grid theme
  (black/orange/blue/cream rounded cards)
- **Storage**: SQLite3 (`finance_analyser.db`) via the `sqlite-jdbc` embedded driver - zero
  configuration, created automatically on first run
- **Analytics/Forecasting**: pure-Java moving-average & velocity forecasting (`ForecastEngine`) -
  runs in-process, no external sidecar needed
- **Charts**: native JavaFX `PieChart`, `LineChart`, `BarChart`
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
    ├── Main.java                  # JavaFX Application entry point
    ├── db/
    │   └── DatabaseManager.java    # all SQLite access - single source of truth for queries
    ├── model/                     # plain POJOs: User, Transaction, SalarySegmentation,
    │                               # GamificationState, CategoryTotal, DateTotal
    ├── controller/
    │   ├── ForecastEngine.java     # allowance, projection, MoM growth, segmentation presets
    │   ├── GamificationEngine.java # XP / streak / badge rules
    │   ├── MissionGenerator.java   # weekly micro-mission generator
    │   └── GeminiClient.java       # Gemini REST client + offline fallback parser
    └── view/
        ├── MainWindow.java          # Folio sidebar (logo, nav, live XP card, profile footer)
        │                             # + header + tab stack + floating chat host
        ├── DashboardView.java       # "Overview" tab
        ├── TransactionsView.java    # "Transactions" tab
        ├── PlannerView.java         # "Budget planner" tab
        ├── AnalyticsView.java       # "Analytics" tab
        ├── GamificationView.java    # "Missions" tab
        ├── ReportsView.java         # "Reports" tab
        ├── SettingsView.java        # "Security" tab (profile, password, backup, Gemini key)
        └── AIChatWidget.java        # Omnipresent draggable floating Gemini chatbot
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
The floating chatbot (bottom-right, draggable by its grip handle) works out of the box:
- Natural-language expense logging ("Spent 150 on auto ride") is parsed with a local
  keyword/regex model (`GeminiClient.localParseExpense`).
- Chat questions ("Am I on track today?", "How can I save ₹2,000?") get deterministic,
  context-aware canned answers computed from your real data.

To upgrade to real Gemini responses, get a free key from https://aistudio.google.com/apikey and
paste it into **Security → Gemini API configuration**. No restart needed - the key is stored in
`config.properties` next to the jar/working directory.

## Notes on this build
- `Ctrl+N` from anywhere focuses the Overview tab's Amount field for fast logging; `Enter` submits.
- Category ceilings, badge eligibility (Budget Master / Saver Ninja / Impulse Control Hero) and
  the spending-pulse/forecast numbers are all computed live from your active salary segmentation -
  set one on the Overview or Budget planner tab first.
- CSV import (Transactions tab) accepts headers `date/amount/category/type/payment/description`
  (case-insensitive, some aliases supported) and handles basic quoted fields.
- The segmentation and daily-adherence "rings" use JavaFX shapes (no punched-out center hole via
  native chart support); functionally identical data, close visual approximation of a donut.
- Student/Roll No/Semester fields have been fully removed from the app (model, database writes,
  and PDF reports) - only a name and an optional profile picture are stored, editable from the
  Security tab, matching your updated design.
- This is a single-user local app - the `users` table supports multiple rows in the schema for
  future multi-profile support, but the UI currently drives one profile at a time.

## A note on how this was built
This code was written and reviewed carefully, cross-checked line-by-line, and verified consistent
(method signatures, argument counts, brace/paren balance) - but it was **not compiled or run**,
because this authoring environment has only a JRE (no `javac`) and no internet access to install a
full JDK, Maven, or the JavaFX/SQLite/Gson/PDFBox dependencies. Please run `mvn clean javafx:run`
as your first step and send me the exact error if anything fails to compile - I'll fix it
immediately.

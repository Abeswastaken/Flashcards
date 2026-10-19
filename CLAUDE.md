# Study Trainer: context for Claude

jake's flashcard and quiz app for UA apprenticeship courses. Read `README.md` for features and `docs/DECISION-MAP.md` for the original requirements and the decisions that are already settled.

## Rules
- **Match the chat trainer.** jake liked its UI. Colors are in `ui/Theme.kt` (from the artifact's CSS), and copy and behavior follow the artifact. The settled decisions are in DECISION-MAP §6, so don't relitigate them: definition on the front, seeded shuffle, marked cards leave filtered decks, auto-advance, 100-run cap, Quit discards.
- **Persistence is priority 1.** Write on every change through `Writer.scope` (process-wide, ordered), never only in memory. Everything is scoped by `courseId`.
- **Course content is data and is never edited.** Add a course by dropping a JSON into `app/src/main/assets/courses/` (or importing it in the app). Never rewrite existing cards or questions.
- `core/` is pure Kotlin and testable with `./gradlew -p core test`. `Deck.seededShuffle` and `Explain.find` are pinned to the artifact's JavaScript by tests, so keep them bit-for-bit.
- Room is at version 1. Before changing an entity, commit `app/schemas/` (CI prints it in "Print the Room schema"), bump the version, and add a Migration with a test.
- Updates must install over the old APK: keep `app/debug.keystore` and never change `applicationId`.
- Not in v1 (ask before adding): spaced repetition (SM-2), study reminders, sync with the artifact.

## Layout
- `core/`: `Course.kt` (file format, parse, dedupe), `Deck.kt` (filters, shuffle, positions), `Explain.kt`, `Quiz.kt` (ActiveQuiz, QuizRun), `Stats.kt`, `Backup.kt` (export format).
- `app/`: `data/Db.kt` (Room: card_status, position, quiz_run, active_quiz), `data/Stores.kt` (Prefs, CourseStore), and `ui/` (one ViewModel plus one file per tab).

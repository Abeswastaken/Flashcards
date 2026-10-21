# Study Trainer: context for Claude

jake's flashcard and quiz app for UA apprenticeship courses. Read `README.md` for features and `docs/DECISION-MAP.md` for the original requirements and the decisions that are already settled.

## Rules
- **Match the chat trainer.** jake liked its UI. Colors are in `ui/Theme.kt` (from the artifact's CSS), and copy and behavior follow the artifact. The settled decisions are in DECISION-MAP §6, so don't relitigate them: definition on the front, seeded shuffle, marked cards leave filtered decks, auto-advance, 100-run cap, Quit discards.
- **Persistence is priority 1.** Write on every change through `Writer.scope` (process-wide, ordered), never only in memory. Everything is scoped by `courseId`.
- **Course content is data and is never edited.** Add a course by dropping a JSON into `app/src/main/assets/courses/` (or importing it in the app). Never rewrite existing cards or questions.
- `core/` is pure Kotlin and testable with `./gradlew -p core test`. `Deck.seededShuffle` and `Explain.find` are pinned to the artifact's JavaScript by tests, so keep them bit-for-bit.
- Room is at version 2 (`MIGRATION_1_2`, tested in `MigrationTest`). Before changing an entity, commit the current `app/schemas/` file (CI prints it in "Print the Room schema"), bump the version, and add a Migration with a test.
- Course content comes from the textbook itself, never from note files. Figures are `image` (data URI or base64) on a card or question.
- Updates must install over the old APK: keep `app/debug.keystore` and never change `applicationId`.
- Spaced repetition was approved by jake (2026-10-21): `Srs.kt`, graded by every Got it / Still learning. Still ask before adding study reminders or sync with the artifact.

## Layout
- `core/`: `Course.kt` (file format, parse, dedupe), `Deck.kt` (filters, shuffle, positions), `Explain.kt`, `Quiz.kt` (ActiveQuiz incl. exam and mixed runs, QuizRun), `Srs.kt`, `Weak.kt`, `Drills.kt` (electrical math, rebuilt from `drill:<kind>:<seed>` keys), `ChapterScope.kt`, `Stats.kt`, `Backup.kt` (export format).
- `app/`: `data/Db.kt` (Room: card_status, position, quiz_run, active_quiz, card_review, note), `data/Stores.kt` (Prefs, CourseStore), and `ui/` (one ViewModel plus one file per tab; the app opens on `HomeTab`; `Extras.kt` has figures, notes and copy).

# Study Trainer

Flashcards and quizzes for UA apprenticeship coursework, as a sideloaded Android app. It's a native port of the Steam Systems Trainer built in claude.ai chat, with the same look and behavior.

First course: **Y3-C1 Steam Systems** (461 cards, 243 questions, 12 chapters).

## Features
- **Flashcards:** the definition is on the front and the term on the back. Tap or press Flip to turn a card, and swipe to move between cards. Filter by chapter and by All / Still learning / Not reviewed / Known. Random order uses a stored seed, so the order stays the same until you press Reshuffle. ✓ Got it and ✗ Still learning advance to the next card. Reset clears marks for the current deck.
- **Quiz:** choose a chapter and a length (10/20/30/50/All). Multiple choice with instant feedback. A wrong answer shows the correct answer, the textbook reference and the matching flashcard. At the end you can choose Review wrong (not logged) or New quiz.
- **History:** the last 100 runs per course. Tap a run to see what you missed, then retake just those questions.
- **Stats:** known and learning counts, quiz average, and progress per chapter.
- **Resume:** every tap is saved right away. The app reopens on the same tab and card, and an unfinished quiz survives the app being closed. Quit (after a confirm) throws the quiz away.
- **Theme:** ◐ cycles Auto → Light → Dark. This setting is per device.
- **⋮ menu:**
  - *Import course…* adds a course .json without a rebuild.
  - *Export progress…* and *Import progress…* write and read one backup file.
  - Android's own backup also keeps the progress database.

## Courses are data
A course is one JSON file (format in `docs/DECISION-MAP.md` §4). Bundled courses live in `app/src/main/assets/courses/`. Progress is stored per course id and keyed by `chapter|term` for cards and `chapter|question` for quiz questions. Adding a course never touches another course's progress, and a newer `version` of the same course keeps it. Course content is copied verbatim from the textbook, so it is never edited.

## Build
Builds run only in GitHub Actions. Each run does JVM tests, emulator tests, then signed debug and release APKs. Every push to `main` publishes a Release `v1.0.<run>`. The signing key is `app/debug.keystore`, committed on purpose so updates install over the old app. Don't change `applicationId`.

Local check of the pure-Kotlin logic: `./gradlew -p core test`.

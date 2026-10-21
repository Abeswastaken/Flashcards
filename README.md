# Study Trainer

Flashcards and quizzes for UA apprenticeship coursework, as a sideloaded Android app. It's a native port of the Steam Systems Trainer built in claude.ai chat, with the same look and behavior.

First course: **Y3-C1 Steam Systems** (461 cards, 243 questions, 12 chapters).

## Features
- **Home:** where the app opens. Continue (your flashcard place or the quiz in progress), Due today, Weak spots, Quiz, Practice exam, Math drills (electrical courses only), and a snapshot: cards known, quiz average, weakest chapter, last quiz.
- **Spaced repetition:** every ✓ Got it / ✗ Still learning, in the Cards tab or inside a quiz, schedules that card's next review (1 day, then 3, then growing). Still learning brings it back today. *⏰ Due today* (Home tile, or the Cards "Show" filter) shows the cards due.
- **Weak spots:** a run of your most-missed questions (from History) alternating with your Still learning cards.
- **Practice exam:** questions only, 1 minute per question, no feedback until the end (picks can be changed before Next). Unanswered questions count as wrong when time runs out. The end screen shows the score by chapter and every miss with the right answer and page. Logged in History as "Exam · …".
- **Math drills:** Ohm's law, power (P = E × I, I² × R, E² ÷ R) and series/parallel resistance with new numbers each time, using the book's letters (E, I, R, P). Wrong answers show the formula. Practice only, not logged.
- **Figures:** a card or question can carry a figure from the book (`image`). Tap it to open full screen and pinch to zoom.
- **Copy and notes:** question, answer and card text can be long-pressed to select, and ⧉ Copy text copies the whole card or question. ✎ Add note keeps your own note on a card or question (the course text is never changed). Notes and review schedules are in the progress backup.
- **Flashcards:** the definition is on the front and the term on the back. Tap or press Flip to turn a card, and swipe to move between cards. Filter by chapter (one, all, or a range such as Ch. 1–4) and by All / Still learning / Not reviewed / Known. Random order uses a stored seed, so the order stays the same until you press Reshuffle. ✓ Got it and ✗ Still learning advance to the next card. Reset clears marks for the current deck.
- **Quiz:** choose a chapter or chapter range and a length (10/20/30/50/All). *Mix in flashcards* makes half the items flashcards between the questions: reveal the term, then ✓ Got it / ✗ Still learning, which marks the card just like the Flashcards tab. Only questions count toward the score and History. Multiple choice with instant feedback. A wrong answer shows the correct answer, the textbook reference and the matching flashcard. At the end you can choose Review wrong (not logged) or New quiz.
- **History:** the last 100 runs per course. Tap a run to see what you missed, then retake just those questions.
- **Stats:** known and learning counts, quiz average, quiz accuracy per chapter with the weakest chapter called out (needs 5+ answers, from runs since this version), and progress per chapter.
- **Resume:** every tap is saved right away. The app reopens on Home, with Continue going back to the same card, and an unfinished quiz survives the app being closed. Quit (after a confirm) throws the quiz away.
- **Theme:** ◐ cycles Auto → Light → Dark. This setting is per device.
- **⋮ menu:**
  - *Import course…* adds a course .json without a rebuild. Tapping a course .json in Files or Drive, or sharing it to the app, does the same. A newer `version` of the same course replaces its cards and keeps progress, which is how new chapters get added.
  - *Export progress…* and *Import progress…* write and read one backup file.
  - Android's own backup also keeps the progress database.

## Courses are data
A course is one JSON file (format in `docs/DECISION-MAP.md` §4). Cards and questions may also carry an optional `ref` (textbook page) and `image` (a figure from the book, as a data URI or base64). Everything in a course comes from the textbook itself, not from note files. Bundled courses live in `app/src/main/assets/courses/`. Progress is stored per course id and keyed by `chapter|term` for cards and `chapter|question` for quiz questions. Adding a course never touches another course's progress, and a newer `version` of the same course keeps it. Course content is copied verbatim from the textbook, so it is never edited.

## Build
Builds run only in GitHub Actions. Each run does JVM tests, emulator tests, then signed debug and release APKs. Every push to `main` publishes a Release `v1.0.<run>`. The signing key is `app/debug.keystore`, committed on purpose so updates install over the old app. Don't change `applicationId`.

Local check of the pure-Kotlin logic: `./gradlew -p core test`.

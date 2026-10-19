# Steam Systems Trainer — Decision Map

Context handoff. No code assumptions. The implementing project picks the stack.

---

## 1. What this is

A study trainer for UA apprenticeship coursework. First course loaded: **Y3-C1 Steam Systems** (462 flashcards, 243 quiz questions, 10 chapters, source: ATP/IPTJTC *Steam Systems* 2019 textbook).

Current form: single HTML page running as a claude.ai Artifact with the artifact DB capability for persistence.

Target form: standalone Android app for sideload. Implementation stack left open.

---

## 2. User intent (ordered by priority)

1. **Persistence that survives**. The reason this project started: the standalone HTML file lost progress every time it was closed and reopened. Any solution MUST persist across app kills, device reboots, and (ideally) device changes.
2. **Study-while-working**. Jake is a field tech. The app opens, he studies 5 minutes between service calls, closes it, picks up where he left off. Resume-where-you-left-off is not a nice-to-have.
3. **Accountability on wrong quiz answers**. The old file just marked wrong/right. He wants to know *why* the answer was wrong — show the correct answer in full, show the term definition where applicable.
4. **Review the pile of "still learning" cards**. Not just mark them — go back and drill them specifically.
5. **Historic quiz log**. See which quizzes have been taken, how he did, which questions he missed. Retake just the misses.
6. **Multi-course future**. Y3-C1 is first. Y3-C2, Y3-C3, future UA years, future topics all need to drop in cleanly. Course content is data, not code.

---

## 3. Feature inventory (what the working artifact does today)

### Flashcards
- Shows **definition** on the front, **term/word** on the back. Flip on tap. This direction is deliberate — Jake wants to recognize terms from their meaning (practical field use), not recite memorized definitions.
- Chapter filter: "All" or any single chapter.
- Status filter: All / Still learning / Not reviewed / Known. Marking a card in a filtered view drops it from the deck.
- Random order toggle with a **stored seed** so shuffle is stable across reloads. A separate "Reshuffle" generates a new seed.
- Three nav buttons: Prev / Flip / Next.
- Two status buttons: ✓ Got it / ✗ Still learning. These advance to the next card automatically.
- Reset-progress for the current chapter.
- Progress bar (position in deck), counter (N known · N learning), per-card status badge visible on the front face.
- Swipe left/right advances/goes back.

### Quiz
- Chapter filter + length (10 / 20 / 30 / 50 / all).
- One question at a time, multiple choice (A/B/C/D), tap to answer.
- Immediate feedback: correct answer highlighted green, wrong pick red if applicable.
- **Explanation panel** on wrong: shows the correct answer letter+text, the textbook reference (page/section), and the matching flashcard term+definition if findable by heuristic.
- Score + progress bar during quiz.
- "Review wrong" button at end spins up a mini-quiz of only the misses.
- "Quit" with confirmation (does not save a partial run).

### History tab
- List of past quiz runs: date/time, chapter, score, percent.
- Tap any run to expand misses: question, what Jake picked, correct answer, reference.
- "Retake these N questions" button per past run — only the misses.
- Keeps last 100 runs.

### Stats tab
- Totals: known count, learning count, quiz average %.
- Per-chapter progress bar (green = known, amber = learning) with counts.

### Cross-cutting
- Light / Dark / Auto theme toggle (per-device setting, not synced).
- Resume banner on the flashcards tab when position doesn't match last session.
- Sync status dot by the title (green = synced, amber = local-only, red = error).

---

## 4. Data model (course file shape)

A course is one JSON file. Dropping a new file in = a new course available.

```
{
  "course": {
    "id": "y3-c1-steam-systems",      // slug, stable
    "title": "Y3-C1: Steam Systems",  // UI label
    "program": "UA Apprenticeship Year 3",
    "source": "Steam Systems (2019, ATP/IPTJTC)",
    "version": 1
  },
  "flashcards": [
    { "chapter": "Chapter 1: ...", "term": "...", "definition": "..." },
    ...
  ],
  "quizzes": [
    {
      "chapter": "Chapter 1: ...",
      "section": "optional subsection label",
      "question": "...",
      "options": ["A text", "B text", "C text", "D text"],
      "correct": 2,                   // 0-indexed
      "ref": "p.47" or "§3.2"         // optional textbook citation
    },
    ...
  ]
}
```

- `chapter` strings double as the chapter filter values. Keep them exact and consistent within a course.
- Flashcard uniqueness within a course = (chapter, term).
- Quiz uniqueness = (chapter, question).

Y3-C1 is attached as `y3-c1-steam-systems.json`.

---

## 5. State shape (what needs to persist)

Scoped **per course** — don't mix progress across courses.

- **Card status map**: `{ "<chapter>|<term>" : "known" | "learning" }`. Absence = not yet reviewed.
- **Position**: `{ chapter, filter, idx, random: bool, seed }` for the flashcard tab. Enables resume.
- **Quiz runs log**: append-only list of `{ date, chapter, score, total, wrong: [{ question, correctText, pickedText, ref, chapter }] }`.
- **Theme**: light / dark / auto. Per-device, does NOT need to sync.

The artifact version uses the Claude DB; an Android app will need its own local store. Separation of courses is important — adding Y3-C2 should not reset Y3-C1 progress.

---

## 6. Design decisions already settled (don't relitigate)

- **Flashcards: definition on front, term on back**. Not the other way around.
- **Random shuffle uses a seed**. Reload the app, same random order. Only "Reshuffle" changes it.
- **Marking a card in a filtered view removes it from the current deck immediately**. Next card slides into same index.
- **Theme is per-device**. Not synced to the course progress store.
- **Quiz history caps at 100 runs**. Older runs get pruned.
- **"Still learning" and "Got it" buttons advance automatically**. No separate tap required.
- **Quit mid-quiz discards the run**. Confirmed by prompt.
- **One course at a time in the UI**. A course picker if there's more than one; otherwise auto-select the only course.
- **The HTML artifact stays live in this chat**. The Android app is a separate client against the same mental model. They don't have to share progress automatically; export/import JSON is a reasonable bridge if Jake ever wants that.

---

## 7. Open questions for the Android implementation

Let the implementer answer these; don't assume:

- UI framework (Compose vs Views; themes; animations)
- Local persistence (Room / SQLite / DataStore / files — pick what fits the state shape above)
- Course file packaging (ship bundled in assets? download from a Jake-controlled source? import from device storage?)
- Multi-course picker UX (list screen? drawer? bottom nav?)
- Whether to add spaced repetition (SM-2) scheduling, or keep the three-state model (known / learning / unseen). Current UI assumes the latter.
- Export/import for backup
- App icon, splash
- Min / target SDK
- Whether to add study-reminder notifications

---

## 8. Attached files

- `steam-trainer-source.html` — the full working artifact page. Reference it for exact UI behavior, color choices, interaction details, copy, edge cases. The data (`const DATA = {...}`) is embedded in the file; it has already been extracted to the course JSON.
- `y3-c1-steam-systems.json` — the Y3-C1 course content in the shape documented in §4. Use this as the data source going forward.

---

## 9. Content provenance

Flashcard terms, definitions, quiz questions, and answers are copied verbatim from Jake's apprenticeship coursework: *Steam Systems* (2019), published by ATP for IPTJTC. This is his own course material. Don't modify the content; add new courses, don't rewrite the ones you have.

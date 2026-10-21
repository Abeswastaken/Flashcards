package com.abeswastaken.studytrainer.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.abeswastaken.studytrainer.core.ActiveQuiz
import com.abeswastaken.studytrainer.core.CardFilter
import com.abeswastaken.studytrainer.core.CardStatus
import com.abeswastaken.studytrainer.core.ChapterScore
import com.abeswastaken.studytrainer.core.ChapterScope
import com.abeswastaken.studytrainer.core.Course
import com.abeswastaken.studytrainer.core.CourseProgress
import com.abeswastaken.studytrainer.core.Deck
import com.abeswastaken.studytrainer.core.Flashcard
import com.abeswastaken.studytrainer.core.HISTORY_CAP
import com.abeswastaken.studytrainer.core.Miss
import com.abeswastaken.studytrainer.core.Position
import com.abeswastaken.studytrainer.core.ProgressExport
import com.abeswastaken.studytrainer.core.QuizRun
import com.abeswastaken.studytrainer.core.Review
import com.abeswastaken.studytrainer.core.Srs
import com.abeswastaken.studytrainer.core.Stats
import com.abeswastaken.studytrainer.data.ActiveQuizEntity
import com.abeswastaken.studytrainer.data.AppDatabase
import com.abeswastaken.studytrainer.data.CardReviewEntity
import com.abeswastaken.studytrainer.data.CardStatusEntity
import com.abeswastaken.studytrainer.data.NoteEntity
import com.abeswastaken.studytrainer.data.CourseStore
import com.abeswastaken.studytrainer.data.PositionEntity
import com.abeswastaken.studytrainer.data.Prefs
import com.abeswastaken.studytrainer.data.QuizRunEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** One writer, in order, for the whole process: a later tap can never be overwritten by an earlier one. */
@OptIn(ExperimentalCoroutinesApi::class)
object Writer {
    val io = Dispatchers.IO.limitedParallelism(1)
    val scope = CoroutineScope(SupervisorJob() + io)
}

enum class Tab(val label: String) { HOME("Home"), FLASH("Cards"), QUIZ("Quiz"), HISTORY("History"), STATS("Stats") }

/** A run as shown in History (the core run plus its row id). */
data class RunRow(val id: Long, val run: QuizRun)

/**
 * All screen state. Every change is written to the database straight away (no save button),
 * so killing the app between service calls loses nothing.
 */
class TrainerViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).dao()
    private val prefs = Prefs(app)
    private val store = CourseStore(app)
    private val io = Writer.io
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val missList = ListSerializer(Miss.serializer())
    private val chapterMap = MapSerializer(String.serializer(), ChapterScore.serializer())

    var loading by mutableStateOf(true); private set
    var courses by mutableStateOf<List<Course>>(emptyList()); private set
    var course by mutableStateOf<Course?>(null); private set
    var statuses by mutableStateOf<Map<String, CardStatus>>(emptyMap()); private set
    var position by mutableStateOf(Position()); private set
    var runs by mutableStateOf<List<RunRow>>(emptyList()); private set
    var quiz by mutableStateOf<ActiveQuiz?>(null); private set
    var reviews by mutableStateOf<Map<String, Review>>(emptyMap()); private set
    var notes by mutableStateOf<Map<String, String>>(emptyMap()); private set
    /** The app opens on Home, the jumping-off point for every way to study. */
    var tab by mutableStateOf(Tab.HOME); private set
    var theme by mutableStateOf(prefs.theme); private set
    var quizChapter by mutableStateOf(prefs.quizChapter); private set
    var quizLength by mutableStateOf(prefs.quizLength); private set
    var quizMixed by mutableStateOf(prefs.quizMixed); private set
    var quizExam by mutableStateOf(prefs.quizExam); private set
    /** One-line notice shown in a snackbar. */
    var notice by mutableStateOf<String?>(null)

    /** Today as an epoch day, for spaced repetition. */
    fun today(): Long = java.time.LocalDate.now().toEpochDay()
    val dueKeys: Set<String> by derivedStateOf { course?.let { Srs.due(it, reviews, today()) } ?: emptySet() }
    val deck: List<Flashcard> by derivedStateOf { buildDeck(statuses, dueKeys) }
    /** Current card index, kept inside the deck. */
    val idx: Int by derivedStateOf { if (deck.isEmpty()) 0 else position.idx.coerceIn(0, deck.size - 1) }
    val stats by derivedStateOf { course?.let { c -> Stats.of(c, statuses, runs.map { it.run }) } }

    // One load at a time: a course file opened at cold start must not be undone by the startup load.
    private val loadLock = Mutex()

    init {
        viewModelScope.launch { loadCourses(prefs.courseId) }
    }

    private fun buildDeck(st: Map<String, CardStatus>, due: Set<String>): List<Flashcard> {
        val c = course ?: return emptyList()
        val chapter = if (ChapterScope.isValid(c, position.chapter)) position.chapter else com.abeswastaken.studytrainer.core.ALL_CHAPTERS
        return Deck.build(c, chapter, CardFilter.of(position.filter), st, position.random, position.seed, due)
    }

    private suspend fun loadCourses(preferId: String?) = loadLock.withLock {
        val list = withContext(Dispatchers.IO) { store.loadAll() }
        courses = list
        val pick = list.firstOrNull { it.id == preferId } ?: list.firstOrNull()
        if (pick != null) loadCourse(pick)
        loading = false
    }

    private suspend fun loadCourse(c: Course) {
        val (st, pos, rs, aq) = withContext(io) {
            Quad(dao.statuses(c.id), dao.position(c.id), dao.runs(c.id), dao.activeQuiz(c.id))
        }
        val (rv, nt) = withContext(io) { dao.reviews(c.id) to dao.notes(c.id) }
        reviews = rv.associate { it.cardKey to Review(it.ease, it.interval, it.due, it.reps) }
        notes = nt.associate { it.itemKey to it.text }
        course = c
        prefs.courseId = c.id
        statuses = st.mapNotNull { e -> CardStatus.of(e.status)?.let { e.cardKey to it } }.toMap()
        position = pos?.let { Position(it.chapter, it.filter, it.idx, it.random, it.seed) } ?: Position()
        runs = rs.map { it.toRow() }
        quiz = aq?.let { runCatching { json.decodeFromString(ActiveQuiz.serializer(), it.json) }.getOrNull() }
            ?.takeIf { q -> q.fits(c) }
        if (!ChapterScope.isValid(c, quizChapter)) {
            chooseQuizChapter(com.abeswastaken.studytrainer.core.ALL_CHAPTERS)
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private fun QuizRunEntity.toRow() = RunRow(
        id,
        QuizRun(
            date, chapter, score, total, runCatching { json.decodeFromString(missList, wrongJson) }.getOrDefault(emptyList()), retake,
            exam = exam,
            byChapter = chaptersJson?.let { runCatching { json.decodeFromString(chapterMap, it) }.getOrNull() } ?: emptyMap(),
        ),
    )

    private fun QuizRun.toEntity(courseId: String) = QuizRunEntity(
        courseId = courseId, date = date, chapter = chapter, score = score, total = total,
        retake = retake, wrongJson = json.encodeToString(missList, wrong),
        exam = exam, chaptersJson = if (byChapter.isEmpty()) null else json.encodeToString(chapterMap, byChapter),
    )

    /** Every Got it / Still learning, anywhere, also schedules the card's next review. */
    private fun grade(courseId: String, cardKey: String, known: Boolean) {
        val r = Srs.grade(reviews[cardKey], known, today())
        reviews = reviews + (cardKey to r)
        write { dao.putReview(CardReviewEntity(courseId, cardKey, r.ease, r.interval, r.due, r.reps)) }
    }

    /** Writes outlive the screen: backing out right after a tap still saves it. */
    private fun write(block: suspend () -> Unit) {
        Writer.scope.launch { block() }
    }

    // ---- Header ----

    fun selectTab(t: Tab) { tab = t; prefs.tab = t.ordinal }

    /** Auto -> Light -> Dark -> Auto, like the artifact's ◐ button. */
    fun cycleTheme() {
        theme = when (theme) { "auto" -> "light"; "light" -> "dark"; else -> "auto" }
        prefs.theme = theme
    }

    fun selectCourse(c: Course) {
        if (c.id == course?.id) return
        viewModelScope.launch { loadCourse(c) }
    }

    // ---- Flashcards ----

    private fun savePosition(p: Position) {
        position = p
        val id = course?.id ?: return
        write { dao.putPosition(PositionEntity(id, p.chapter, p.filter, p.idx, p.random, p.seed)) }
    }

    fun setChapter(ch: String) = savePosition(position.copy(chapter = ch, idx = 0))
    fun setFilter(f: CardFilter) = savePosition(position.copy(filter = f.stored, idx = 0))
    fun setRandom(on: Boolean) =
        savePosition(position.copy(random = on, seed = if (on && position.seed == 0L) Deck.newSeed() else position.seed, idx = 0))
    fun reshuffle() = savePosition(position.copy(random = true, seed = Deck.newSeed(), idx = 0))

    fun next() { if (deck.isNotEmpty()) savePosition(position.copy(idx = (idx + 1) % deck.size)) }
    fun prev() { if (deck.isNotEmpty()) savePosition(position.copy(idx = (idx - 1 + deck.size) % deck.size)) }

    /** ✓ Got it / ✗ Still learning: mark and move on. */
    fun mark(status: CardStatus) {
        val c = course ?: return
        val old = deck
        if (old.isEmpty()) return
        val oldIdx = idx
        val card = old[oldIdx]
        val newStatuses = statuses + (card.key to status)
        statuses = newStatuses
        write { dao.putStatus(CardStatusEntity(c.id, card.key, status.stored)) }
        grade(c.id, card.key, status == CardStatus.KNOWN)
        val newSize = buildDeck(newStatuses, Srs.due(c, reviews, today())).size
        val filter = CardFilter.of(position.filter)
        val newIdx = if (filter == CardFilter.DUE && newSize == old.size) {
            (oldIdx + 1) % newSize // Still learning stays due today; move on rather than show it again at once
        } else {
            Deck.indexAfterMark(oldIdx, old.size, newSize, filter != CardFilter.ALL)
        }
        savePosition(position.copy(idx = newIdx))
    }

    /** Clears known/learning for the cards in the current deck. */
    fun resetDeck() {
        val c = course ?: return
        val keys = deck.map { it.key }
        statuses = statuses - keys.toSet()
        write { dao.deleteStatuses(c.id, keys) }
    }

    // ---- Quiz ----

    fun chooseQuizChapter(ch: String) { quizChapter = ch; prefs.quizChapter = ch }
    fun chooseQuizLength(n: Int) { quizLength = n; prefs.quizLength = n }
    fun chooseQuizMixed(on: Boolean) { quizMixed = on; prefs.quizMixed = on }
    fun chooseQuizExam(on: Boolean) { quizExam = on; prefs.quizExam = on }

    /** Home's Due today tile: the Cards tab showing only the cards due. */
    fun openDue() {
        savePosition(position.copy(filter = CardFilter.DUE.stored, chapter = com.abeswastaken.studytrainer.core.ALL_CHAPTERS, idx = 0))
        selectTab(Tab.FLASH)
    }

    fun setNote(key: String, text: String) {
        val c = course ?: return
        val t = text.trim()
        notes = if (t.isEmpty()) notes - key else notes + (key to t)
        write { if (t.isEmpty()) dao.deleteNote(c.id, key) else dao.putNote(NoteEntity(c.id, key, t)) }
    }

    private fun saveQuiz(q: ActiveQuiz?) {
        quiz = q
        val id = course?.id ?: return
        write {
            if (q == null) dao.clearActiveQuiz(id)
            else dao.putActiveQuiz(ActiveQuizEntity(id, json.encodeToString(ActiveQuiz.serializer(), q)))
        }
    }

    fun startQuiz() {
        val c = course ?: return
        val q = ActiveQuiz.start(c, quizChapter, quizLength, mixed = quizMixed && !quizExam, exam = quizExam, now = System.currentTimeMillis())
        if (q == null) { notice = if (quizMixed && !quizExam) "No cards or questions for this chapter." else "No questions for this chapter."; return }
        saveQuiz(q)
    }

    /** Home: an exam over the chosen chapters with the chosen length. */
    fun startExam() {
        chooseQuizExam(true)
        startQuiz()
        if (quiz != null) selectTab(Tab.QUIZ)
    }

    fun startWeak() {
        val c = course ?: return
        val q = ActiveQuiz.weak(c, runs.map { it.run }, statuses)
        if (q == null) { notice = "No weak spots yet. Missed questions and Still learning cards show up here."; return }
        saveQuiz(q)
        selectTab(Tab.QUIZ)
    }

    fun startDrills() {
        saveQuiz(ActiveQuiz.drills())
        selectTab(Tab.QUIZ)
    }

    /** The exam clock ran out: finish and log it, unanswered questions counting as wrong. */
    fun examTimeUp() {
        val c = course ?: return
        val q = quiz ?: return
        if (!q.exam || q.finished) return
        val done = q.timeUp()
        saveQuiz(done)
        log(c, done)
    }

    fun pick(option: Int) {
        val c = course ?: return
        val q = quiz ?: return
        saveQuiz(q.pick(c, option))
    }

    /** Got it / Still learning on a card in a mixed run: marks the card like the Flashcards tab, then moves on. */
    fun markQuizCard(known: Boolean) {
        val c = course ?: return
        val q = quiz ?: return
        if (!q.currentIsCard || q.pickedNow != null) return
        val key = ActiveQuiz.cardKey(q.pool[q.idx])
        val status = if (known) CardStatus.KNOWN else CardStatus.LEARNING
        statuses = statuses + (key to status)
        write { dao.putStatus(CardStatusEntity(c.id, key, status.stored)) }
        grade(c.id, key, known)
        advance(c, q.markCard(known))
    }

    fun nextQuestion() {
        val c = course ?: return
        val q = quiz ?: return
        if (q.pickedNow == null) return
        advance(c, q)
    }

    private fun advance(c: Course, q: ActiveQuiz) {
        val n = q.next()
        saveQuiz(n)
        if (n.finished) log(c, n)
    }

    private fun log(c: Course, n: ActiveQuiz) {
        if (n.logged) {
            val e = n.toRun(c, System.currentTimeMillis()).toEntity(c.id)
            Writer.scope.launch {
                dao.addRun(e, HISTORY_CAP)
                val rows = dao.runs(c.id).map { it.toRow() }
                withContext(Dispatchers.Main) { if (course?.id == c.id) runs = rows }
            }
        }
    }

    /** Quit (after the confirm) or New quiz: drop the quiz without logging it. */
    fun endQuiz() = saveQuiz(null)

    fun reviewWrong() {
        val q = quiz ?: return
        val wrong = q.wrongKeys()
        if (wrong.isEmpty()) { notice = "Nothing to review — all correct!"; return }
        saveQuiz(ActiveQuiz(q.chapter, wrong, reviewing = true))
    }

    fun retake(row: RunRow) {
        val c = course ?: return
        val q = ActiveQuiz.retake(c, row.run)
        if (q == null) { notice = "Could not find those questions."; return }
        saveQuiz(q)
        selectTab(Tab.QUIZ)
    }

    // ---- Courses and backup ----

    /** A progress file waiting for the user to confirm it may replace what's on the phone. */
    var pendingProgress by mutableStateOf<Uri?>(null)

    fun importCourse(uri: Uri) = openFile(uri)

    /**
     * Opens a .json from the ⋮ menu, from tapping it in Files/Drive, or from Share.
     * A course file is added straight away; a progress backup asks first.
     */
    fun openFile(uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) { runCatching { readText(uri) } }.getOrElse {
            notice = "Couldn't open that file: ${it.message}"
            return@launch
        }
        if (runCatching { ProgressExport.decode(text) }.isSuccess) {
            pendingProgress = uri
            return@launch
        }
        val result = withContext(Dispatchers.IO) { runCatching { store.import(text) } }
        result.onSuccess { c ->
            notice = "Added ${c.meta.title}: ${c.chapters.size} chapters, ${c.flashcards.size} cards, ${c.quizzes.size} questions."
            loadCourses(c.id)
        }.onFailure { notice = "Couldn't import that course: ${it.message}" }
    }

    fun exportProgress(uri: Uri) = viewModelScope.launch {
        val result = withContext(io) {
            runCatching {
                val ids = (dao.courseIdsWithProgress() + listOfNotNull(course?.id)).distinct()
                val all = ids.associateWith { id ->
                    val pos = dao.position(id)
                    CourseProgress(
                        cards = dao.statuses(id).associate { it.cardKey to it.status },
                        position = pos?.let { Position(it.chapter, it.filter, it.idx, it.random, it.seed) },
                        runs = dao.runs(id).map { it.toRow().run },
                        reviews = dao.reviews(id).associate { it.cardKey to Review(it.ease, it.interval, it.due, it.reps) },
                        notes = dao.notes(id).associate { it.itemKey to it.text },
                    )
                }
                val text = ProgressExport(exportedAt = System.currentTimeMillis(), courses = all).encode()
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                all.size
            }
        }
        notice = result.fold({ "Progress saved." }, { "Couldn't save the file: ${it.message}" })
    }

    fun importProgress(uri: Uri) = viewModelScope.launch {
        val result = withContext(io) {
            runCatching {
                val p = ProgressExport.decode(readText(uri))
                p.courses.forEach { (id, cp) ->
                    dao.replaceProgress(
                        id,
                        cp.cards.mapNotNull { (k, v) -> CardStatus.of(v)?.let { CardStatusEntity(id, k, it.stored) } },
                        cp.position?.let { PositionEntity(id, it.chapter, it.filter, it.idx, it.random, it.seed) },
                        cp.runs.sortedByDescending { it.date }.take(HISTORY_CAP).map { it.toEntity(id) },
                        cp.reviews.map { (k, r) -> CardReviewEntity(id, k, r.ease, r.interval, r.due, r.reps) },
                        cp.notes.map { (k, t) -> NoteEntity(id, k, t) },
                    )
                }
                p.courses.size
            }
        }
        result.onSuccess { n ->
            notice = "Progress restored for $n course${if (n == 1) "" else "s"}."
            course?.let { loadCourse(it) }
        }.onFailure { notice = "Couldn't read that file: ${it.message}" }
    }

    private fun readText(uri: Uri): String =
        getApplication<Application>().contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
}

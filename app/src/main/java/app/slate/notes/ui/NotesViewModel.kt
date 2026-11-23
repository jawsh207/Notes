package app.slate.notes.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.slate.notes.data.Library
import app.slate.notes.data.cleanNotebookName
import app.slate.notes.data.ATTACHMENTS
import app.slate.notes.data.DEFAULT_NOTEBOOK
import app.slate.notes.data.Note
import app.slate.notes.data.NoteInfo
import app.slate.notes.data.NoteMeta
import app.slate.notes.data.NoteStore
import app.slate.notes.data.ScheduledReminder
import app.slate.notes.data.Settings
import app.slate.notes.data.SortOrder
import app.slate.notes.data.TrashEntry
import app.slate.notes.data.UNTITLED
import app.slate.notes.data.WELCOME_TEXT
import app.slate.notes.data.WELCOME_TITLE
import app.slate.notes.data.cleanLabel
import app.slate.notes.data.noteInfoOf
import app.slate.notes.data.readMeta
import app.slate.notes.data.splitFrontMatter
import app.slate.notes.data.trashExpired
import app.slate.notes.data.writeMeta
import app.slate.notes.reminders.ReminderAlarms
import app.slate.notes.ui.editor.NoteSession
import java.text.Collator
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Where the app stands with the notes folder. */
sealed interface FolderState {
    /** Still reading the settings. */
    data object Loading : FolderState

    /** No folder yet, or [lost] access to the one chosen before (it was moved, or access was revoked). */
    data class Needed(val lost: Boolean) : FolderState

    /** A folder is chosen and readable. */
    data object Ready : FolderState
}

/** What an Undo bar is offering to undo. */
enum class UndoKind {
    /** Notes moved to the trash. */
    Trashed,

    /** Notes archived. */
    Archived,

    /** Notes taken out of the archive. */
    Unarchived,
}

/** Something just done that the Undo bar offers to take back. [title] is set when it was one note. */
class UndoAction(val kind: UndoKind, val count: Int, val title: String?, val undo: () -> Unit)

/**
 * The app's state: the notes folder, what's in it, and the open note.
 *
 * The folder is looked at again whenever the phone says something in it changed, and every few
 * seconds while the app is on screen (not every storage provider says), so notes edited on
 * another device and synced into the folder show up by themselves. Texts are read once and then
 * again only when a file's date or size changes; what the lists need from each (pin, colour,
 * labels, preview, checklist, picture) is worked out then.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application

    /** The app's settings. */
    val settings = Settings(application)

    private val _folder = MutableStateFlow<FolderState>(FolderState.Loading)

    /** Whether a notes folder is chosen and reachable. */
    val folder: StateFlow<FolderState> = _folder.asStateFlow()

    private val _library = MutableStateFlow<Library?>(null)

    /** Everything in the notes folder; null until the first look. */
    val library: StateFlow<Library?> = _library.asStateFlow()

    private val _infos = MutableStateFlow<Map<String, NoteInfo>>(emptyMap())

    /** What each note's text says, by note id. Fills in shortly after the list appears. */
    val infos: StateFlow<Map<String, NoteInfo>> = _infos.asStateFlow()

    /**
     * Every label used by a note outside the trash, A to Z. Notes in the trash don't count, so a
     * label taken off every note goes away even if a trashed note still has it.
     */
    val labels: StateFlow<List<String>> = combine(_library, _infos) { library, infos ->
        val collator = Collator.getInstance().apply { strength = Collator.SECONDARY }
        library?.notes.orEmpty().flatMap { infos[it.id]?.meta?.tags.orEmpty() }.distinctBy { it.lowercase() }.sortedWith(collator)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** How lists are sorted. */
    val sortOrder: StateFlow<SortOrder> = settings.sortOrder.stateIn(viewModelScope, SharingStarted.Eagerly, SortOrder.Modified)

    /** Notes open formatted rather than as text. */
    val openFormatted: StateFlow<Boolean> = settings.openFormatted.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The editor uses a fixed-width font. */
    val monospace: StateFlow<Boolean> = settings.monospace.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Notes show as a grid of cards. */
    val grid: StateFlow<Boolean> = settings.grid.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    /** Ticked checklist items go to the bottom. */
    val tickedToBottom: StateFlow<Boolean> = settings.tickedToBottom.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _refreshing = MutableStateFlow(false)

    /** A look at the folder asked for by pulling down the list is under way. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _undo = MutableStateFlow<UndoAction?>(null)

    /** What the Undo bar is offering, if anything. */
    val undo: StateFlow<UndoAction?> = _undo.asStateFlow()

    private val _session = MutableStateFlow<NoteSession?>(null)

    /** The note open in the editor, if any. */
    val session: StateFlow<NoteSession?> = _session.asStateFlow()

    private val _openRequest = MutableStateFlow<String?>(null)

    /** A note to open that was asked for from outside the app (a reminder notification), by id. */
    val openRequest: StateFlow<String?> = _openRequest.asStateFlow()

    private val _notebookColors = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Each notebook's colour (0xAARRGGBB) by notebook name, for those that have one. */
    val notebookColors: StateFlow<Map<String, Int>> = _notebookColors.asStateFlow()

    // Which notebooks' colours were last read, so they're only read again when that changes.
    private var colorsReadFor: Set<String>? = null
    private var store: NoteStore? = null
    private val foreground = MutableStateFlow(false)
    private val pokes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    // What each note's info was worked out from, so unchanged files aren't read again.
    private val readStamps = HashMap<String, Pair<Long, Long>>()
    // One look at the folder at a time: a pull to refresh can land during a scheduled one.
    private val scanLock = Mutex()

    init {
        viewModelScope.launch {
            settings.folder.distinctUntilChanged().collectLatest { uri ->
                store = null
                _library.value = null
                _infos.value = emptyMap()
                _notebookColors.value = emptyMap()
                colorsReadFor = null
                readStamps.clear()
                if (uri == null) {
                    _folder.value = FolderState.Needed(lost = false)
                    return@collectLatest
                }
                if (!hasAccess(uri)) {
                    _folder.value = FolderState.Needed(lost = true)
                    return@collectLatest
                }
                val opened = NoteStore(app, Uri.parse(uri))
                store = opened
                _folder.value = FolderState.Ready
                watch(opened)
            }
        }
    }

    private fun hasAccess(uri: String): Boolean = app.contentResolver.persistedUriPermissions.any {
        it.uri.toString() == uri && it.isReadPermission && it.isWritePermission
    }

    /**
     * Keeps [opened] up to date until the folder changes: looks again on every change the phone
     * reports, every few seconds while on screen, and when asked ([refresh]). The open note is
     * checked more often, so it updates almost as soon as its file does.
     */
    private suspend fun watch(opened: NoteStore) = coroutineScope {
        val watched = MutableStateFlow(listOf(opened.rootId))
        fun watchAll() {
            _library.value?.let { library -> watched.value = listOf(library.rootId) + library.folders.map { it.id } }
        }
        if (rescan(opened)) {
            tidyLooseNotes(opened)
            welcome(opened)
            watchAll()
        }
        launch {
            merge(
                pokes,
                watched.flatMapLatest { opened.changes(it) },
                foreground.flatMapLatest { if (it) ticks(SCAN_EVERY_MS) else emptyFlow() },
            ).conflate().collect {
                if (!rescan(opened)) return@collect
                tidyLooseNotes(opened)
                watchAll()
                _session.value?.checkDisk()
            }
        }
        launch {
            foreground.flatMapLatest { if (it) ticks(CHECK_OPEN_EVERY_MS) else emptyFlow() }.collect {
                _session.value?.checkDisk()
            }
        }
    }

    /**
     * The first time this phone opens a folder, makes the welcome note in Unsorted, whether or not
     * it already has notes. If the folder already holds one (made on another phone that shares
     * it), no second copy is added.
     */
    private suspend fun welcome(opened: NoteStore) {
        if (settings.welcomed.first()) return
        val library = _library.value ?: return
        settings.setWelcomed()
        if (library.notes.any { it.title.equals(WELCOME_TITLE, ignoreCase = true) }) return
        val note = runCatching {
            withContext(Dispatchers.IO) {
                opened.create(opened.ensureFolder(opened.rootId, DEFAULT_NOTEBOOK), DEFAULT_NOTEBOOK, WELCOME_TITLE, WELCOME_TEXT)
            }
        }.getOrNull() ?: return
        addToLibrary(note)
        _infos.value = _infos.value + (note.id to noteInfoOf(WELCOME_TEXT, note.title))
        poke()
    }

    /**
     * Moves notes left loose at the top of the folder (made by an older version, or synced in
     * from another app) into the Unsorted notebook, because Folio only shows notes that are in a
     * notebook. Pictures in a loose "attachments" folder go with them, so their links still work.
     * The open note is left until it's closed.
     */
    private suspend fun tidyLooseNotes(opened: NoteStore) {
        val library = _library.value ?: return
        val session = _session.value
        val loose = library.notes.filter { it.folder.isEmpty() && it.id != session?.note?.id && it.id != session?.openedAs }
        if (loose.isEmpty()) return
        val moved = runCatching {
            withContext(Dispatchers.IO) {
                val target = opened.ensureFolder(opened.rootId, DEFAULT_NOTEBOOK)
                val result = loose.map { it.id to opened.move(it, target, DEFAULT_NOTEBOOK) }
                opened.childId(opened.rootId, ATTACHMENTS)?.let { pictures ->
                    val into = opened.ensureFolder(target, ATTACHMENTS)
                    val taken = opened.namesIn(into).mapTo(HashSet()) { it.lowercase() }
                    opened.children(pictures).filter { !it.isFolder && it.name.lowercase() !in taken }.forEach { opened.moveChild(it.id, pictures, into) }
                    if (opened.children(pictures).isEmpty()) opened.delete(pictures)
                }
                result
            }
        }.getOrNull() ?: return
        val ids = moved.mapTo(HashSet()) { it.first }
        _library.value = _library.value?.let { current -> current.copy(notes = current.notes.filter { it.id !in ids } + moved.map { it.second }) }
        poke()
    }

    /** Reads each notebook's colour (Folio's ".config/color") when the set of notebooks changes. */
    private suspend fun readNotebookColors(opened: NoteStore, library: Library) {
        val notebooks = library.notebooks
        if (notebooks.map { it.id }.toSet() == colorsReadFor) return
        colorsReadFor = notebooks.map { it.id }.toSet()
        _notebookColors.value = withContext(Dispatchers.IO) {
            notebooks.mapNotNull { notebook -> opened.notebookColor(notebook.id)?.let { notebook.name to it } }.toMap()
        }
    }

    private fun ticks(every: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(every)
        }
    }

    /** Looks at the folder once. Returns false if access was lost. */
    private suspend fun rescan(opened: NoteStore): Boolean = scanLock.withLock {
        val scanned = try {
            withContext(Dispatchers.IO) { opened.scan() }
        } catch (_: SecurityException) {
            _folder.value = FolderState.Needed(lost = true)
            return@withLock false
        } catch (_: Exception) {
            // A provider that's briefly unavailable (an SD card being mounted, say): try next time.
            return@withLock true
        }
        if (scanned != _library.value) _library.value = scanned
        readInfos(opened, scanned)
        readNotebookColors(opened, scanned)
        emptyOldTrash(opened, scanned)
        syncReminders()
        true
    }

    /** Reads the text of every note (trash included) that's new or changed since it was last read. */
    private suspend fun readInfos(opened: NoteStore, library: Library) {
        val all = library.notes + library.trash
        val ids = all.mapTo(HashSet()) { it.id }
        val stale = all.filter { readStamps[it.id] != (it.modified to it.size) }
        val gone = readStamps.keys.filter { it !in ids }
        if (stale.isEmpty() && gone.isEmpty()) return
        gone.forEach { readStamps.remove(it) }
        val infos = _infos.value.filterKeys { it in ids }.toMutableMap()
        // Shown in batches, so a big folder's first cards fill in without waiting for the rest.
        stale.chunked(READ_BATCH).forEach { batch ->
            withContext(Dispatchers.IO) {
                for (note in batch) {
                    val text = runCatching { opened.read(note.id) }.getOrNull() ?: continue
                    infos[note.id] = noteInfoOf(text, note.title)
                    readStamps[note.id] = note.modified to note.size
                }
            }
            _infos.value = infos.toMap()
        }
        if (stale.isEmpty()) _infos.value = infos.toMap()
    }

    /** Deletes notes this phone put in the trash more than a week ago, as Google Keep does. */
    private suspend fun emptyOldTrash(opened: NoteStore, library: Library) {
        val now = System.currentTimeMillis()
        val log = settings.trashLog.first()
        val expired = log.filter { trashExpired(it.trashedAt, now) }
        if (expired.isEmpty()) return
        withContext(Dispatchers.IO) {
            library.trash.filter { note -> expired.any { it.name == note.name && it.folder == note.folder } }
                .forEach { runCatching { opened.delete(it.id) } }
        }
        settings.editTrashLog { entries -> entries.filter { it !in expired } }
    }

    /** Sets an alarm for every future reminder in the notes, and cancels alarms no note asks for any more. */
    private suspend fun syncReminders() {
        val library = _library.value ?: return
        val infos = _infos.value
        val zone = ZoneId.systemDefault()
        val wanted = library.notes.mapNotNull { note ->
            val reminder = infos[note.id]?.meta?.reminder ?: return@mapNotNull null
            ScheduledReminder(note.id, reminder.atZone(zone).toInstant().toEpochMilli(), note.title)
        }
        ReminderAlarms.sync(app, wanted)
    }

    /** Tells the view model whether the app is on screen, which is when it checks for changes. */
    fun setForeground(on: Boolean) {
        foreground.value = on
        if (!on) viewModelScope.launch { _session.value?.save() }
    }

    /** Looks at the folder now (pull to refresh). */
    fun refresh() {
        val opened = store ?: return
        viewModelScope.launch {
            _refreshing.value = true
            rescan(opened)
            // Long enough for the indicator to be seen, so the pull feels answered.
            delay(400)
            _refreshing.value = false
        }
    }

    private fun poke() {
        pokes.tryEmit(Unit)
    }

    /**
     * Uses the folder [uri] the system folder picker returned: keeps access to it across restarts
     * and lets go of the previous folder's.
     */
    fun chooseFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val resolver = app.contentResolver
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
        resolver.persistedUriPermissions.filter { it.uri != uri }.forEach {
            runCatching { resolver.releasePersistableUriPermission(it.uri, flags) }
        }
        viewModelScope.launch {
            closeSession()
            // Forget first so choosing the same folder again (after access was lost) reloads it.
            settings.setFolder(null)
            settings.setFolder(uri.toString())
        }
    }

    /** Asks for the note [id] to be opened once the folder has been read (from a reminder notification). */
    fun requestOpen(id: String) {
        _openRequest.value = id
    }

    /** Marks the open request as handled. */
    fun openRequestHandled() {
        _openRequest.value = null
    }

    /**
     * Makes a new note in [notebook] (Unsorted when none is given, made if it isn't there) and
     * opens it: an empty note, or with [list] a checklist with one empty item. [label] is given to
     * it when made from a label's page. Calls [onMade] with its id.
     */
    fun createNote(notebook: String?, list: Boolean = false, label: String? = null, onMade: (String) -> Unit) {
        val opened = store ?: return
        val name = notebook?.takeIf { it.isNotEmpty() } ?: DEFAULT_NOTEBOOK
        val front = label?.let { writeMeta("", NoteMeta(tags = listOf(it))) }.orEmpty()
        val text = front + if (list) "- [ ] \n" else ""
        viewModelScope.launch {
            val note = runCatching {
                withContext(Dispatchers.IO) {
                    val folder = _library.value?.notebook(name)
                    val parentId = folder?.id ?: opened.ensureFolder(opened.rootId, name)
                    opened.create(parentId, folder?.name ?: name, UNTITLED, text)
                }
            }.getOrNull() ?: return@launch
            addToLibrary(note)
            _infos.value = _infos.value + (note.id to noteInfoOf(text, note.title))
            closeSession()
            _session.value = NoteSession(opened, note, viewModelScope, isNew = true, onSaved = ::onSaved)
            onMade(note.id)
            poke()
        }
    }

    /**
     * Adds a note the app just made to the list straight away. Making the file can already have
     * set off a fresh look at the folder that found it, so any copy of it is replaced, not doubled.
     */
    private fun addToLibrary(note: Note) {
        _library.value = _library.value?.let { library -> library.copy(notes = library.notes.filter { it.id != note.id } + note) }
    }

    /**
     * Makes a notebook called [name] with [color] (written as Folio does), then calls [onMade]
     * with its name. Returns false if the name can't be used or is taken.
     */
    fun createNotebook(name: String, color: Int?, onMade: (String) -> Unit = {}): Boolean {
        val opened = store ?: return false
        val clean = cleanNotebookName(name) ?: return false
        if (_library.value?.notebook(clean) != null) return false
        if (color != null) _notebookColors.value = _notebookColors.value + (clean to color)
        viewModelScope.launch {
            val made = runCatching {
                withContext(Dispatchers.IO) {
                    val id = opened.ensureFolder(opened.rootId, clean)
                    if (color != null) opened.setNotebookColor(id, color)
                }
            }.isSuccess
            colorsReadFor = null
            poke()
            if (made) onMade(clean)
        }
        return true
    }

    /**
     * Renames the notebook [old] to [new] and gives it [color]. Every note in it gets a new id, so
     * a note open from it is closed first. Returns false if the new name can't be used or is taken.
     */
    fun renameNotebook(old: String, new: String, color: Int): Boolean {
        val opened = store ?: return false
        val clean = cleanNotebookName(new) ?: return false
        val folder = _library.value?.notebook(old) ?: return false
        if (_library.value?.notebook(clean)?.takeIf { it.id != folder.id } != null) return false
        _notebookColors.value = _notebookColors.value - folder.name + (clean to color)
        viewModelScope.launch {
            if (clean != folder.name && _session.value?.note?.notebook.equals(folder.name, ignoreCase = true)) closeSession()
            runCatching {
                withContext(Dispatchers.IO) {
                    val id = if (clean == folder.name) folder.id else opened.renameFolder(folder.id, clean)
                    opened.setNotebookColor(id, color)
                }
            }
            colorsReadFor = null
            poke()
        }
        return true
    }

    /** Changes a notebook's colour. */
    fun setNotebookColor(name: String, color: Int) {
        val opened = store ?: return
        val folder = _library.value?.notebook(name) ?: return
        _notebookColors.value = _notebookColors.value + (folder.name to color)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { opened.setNotebookColor(folder.id, color) } }
        }
    }

    /**
     * Moves a whole notebook to the trash as Folio does: its notes go to ".trash/<name>/", from
     * where each can be put back (into a notebook of that name, made again if needed).
     */
    fun deleteNotebook(name: String) {
        val opened = store ?: return
        val library = _library.value ?: return
        val folder = library.notebook(name) ?: return
        viewModelScope.launch {
            if (_session.value?.note?.notebook.equals(folder.name, ignoreCase = true)) closeSession()
            val inside = library.notes.filter { it.folder == folder.name }
            val moved = runCatching { withContext(Dispatchers.IO) { opened.trashNotebook(folder, inside) } }.getOrNull().orEmpty()
            val now = System.currentTimeMillis()
            settings.editTrashLog { log -> log + moved.map { TrashEntry(it.name, it.folder, now) } }
            colorsReadFor = null
            poke()
        }
    }

    /** Moves [notes] into [notebook] (made if it isn't there). The open note keeps going under its new id. */
    fun moveToNotebook(notes: List<Note>, notebook: String) {
        val opened = store ?: return
        val clean = cleanNotebookName(notebook) ?: return
        viewModelScope.launch {
            val folder = _library.value?.notebook(clean)
            val targetId = folder?.id ?: runCatching { withContext(Dispatchers.IO) { opened.ensureFolder(opened.rootId, clean) } }.getOrNull() ?: return@launch
            val targetName = folder?.name ?: clean
            for (note in notes) {
                if (note.folder == targetName) continue
                val session = _session.value?.takeIf { it.note.id == note.id || it.openedAs == note.id }
                if (session != null) {
                    session.moveTo(targetId, targetName)
                    continue
                }
                val moved = runCatching { withContext(Dispatchers.IO) { opened.move(note, targetId, targetName) } }.getOrNull() ?: continue
                _library.value = _library.value?.let { library -> library.copy(notes = library.notes.filter { it.id != note.id } + moved) }
                _infos.value[note.id]?.let { info -> _infos.value = _infos.value - note.id + (moved.id to info) }
            }
            poke()
        }
    }

    /**
     * The session for the note [id], opening it if needed. Returns null while the folder is still
     * being read, or if there's no such note.
     */
    fun openNote(id: String): NoteSession? {
        val current = _session.value
        if (current != null && (current.note.id == id || current.openedAs == id)) return current
        val opened = store ?: return null
        val note = _library.value?.notes?.firstOrNull { it.id == id } ?: return null
        val session = NoteSession(opened, note, viewModelScope, isNew = false, onSaved = ::onSaved)
        viewModelScope.launch { current?.close() }
        _session.value = session
        return session
    }

    /** Closes the open note: saves it and drops it if it was new and left empty. */
    fun closeNote() {
        viewModelScope.launch { closeSession() }
    }

    private suspend fun closeSession() {
        val session = _session.value ?: return
        _session.value = null
        if (session.close()) {
            _library.value = _library.value?.let { library -> library.copy(notes = library.notes.filter { it.id != session.note.id }) }
        }
        poke()
    }

    /** Shows a just-saved note's new text in the lists straight away. */
    private fun onSaved(note: Note, text: String) {
        val library = _library.value ?: return
        val opened = _session.value?.openedAs
        val others = library.notes.filter { it.id != note.id && it.id != opened }
        _library.value = library.copy(notes = others + note)
        _infos.value = _infos.value + (note.id to noteInfoOf(text, note.title))
        readStamps[note.id] = note.modified to note.size
        viewModelScope.launch { syncReminders() }
    }

    /**
     * Changes the front matter of [notes] (pin, colour, labels, archive, reminder). The open note
     * goes through its session so its unsaved typing isn't lost; others are read, changed and
     * written straight away.
     */
    fun updateMeta(notes: List<Note>, change: (NoteMeta) -> NoteMeta) {
        val opened = store ?: return
        viewModelScope.launch {
            for (note in notes) {
                val session = _session.value
                if (session != null && (session.note.id == note.id || session.openedAs == note.id)) {
                    session.updateMeta(change)
                    continue
                }
                val written = runCatching {
                    withContext(Dispatchers.IO) {
                        val text = opened.read(note.id)
                        val parts = splitFrontMatter(text)
                        val updated = writeMeta(parts.front, change(readMeta(parts.front))) + parts.body
                        if (updated != text) opened.write(note.id, updated)
                        updated to (opened.modified(note.id) ?: note.modified)
                    }
                }.getOrNull() ?: continue
                onSaved(note.copy(modified = written.second, size = written.first.toByteArray().size.toLong()), written.first)
            }
            poke()
        }
    }

    /** Pins or unpins [notes]. */
    fun setPinned(notes: List<Note>, pinned: Boolean) = updateMeta(notes) { it.copy(pinned = pinned) }

    /** Archives [notes] (or brings them back), offering Undo. Pinned notes lose their pin when archived, as in Keep. */
    fun setArchived(notes: List<Note>, archived: Boolean) {
        updateMeta(notes) { it.copy(archived = archived, pinned = if (archived) false else it.pinned) }
        val kind = if (archived) UndoKind.Archived else UndoKind.Unarchived
        _undo.value = UndoAction(kind, notes.size, notes.singleOrNull()?.title) {
            // The ids may have changed if a note was renamed meanwhile; match by what's in the list now.
            val ids = notes.mapTo(HashSet()) { it.id }
            updateMeta(_library.value?.notes.orEmpty().filter { it.id in ids }) { it.copy(archived = !archived) }
        }
    }

    /** Adds the label [label] to all of [notes], or with [add] false takes it off them. */
    fun setLabel(notes: List<Note>, label: String, add: Boolean) {
        val clean = cleanLabel(label)
        if (clean.isEmpty()) return
        updateMeta(notes) { meta ->
            val without = meta.tags.filterNot { it.equals(clean, ignoreCase = true) }
            meta.copy(tags = if (add) without + clean else without)
        }
    }

    /** Renames a label on every note that has it. */
    fun renameLabel(old: String, new: String) {
        val clean = cleanLabel(new)
        if (clean.isEmpty()) return
        updateMeta(notesWithLabel(old)) { meta ->
            meta.copy(tags = meta.tags.map { if (it.equals(old, ignoreCase = true)) clean else it }.distinctBy { it.lowercase() })
        }
    }

    /** Takes a label off every note that has it. The notes themselves stay. */
    fun deleteLabel(label: String) {
        updateMeta(notesWithLabel(label)) { meta -> meta.copy(tags = meta.tags.filterNot { it.equals(label, ignoreCase = true) }) }
    }

    private fun notesWithLabel(label: String): List<Note> =
        _library.value?.notes.orEmpty().filter { note -> _infos.value[note.id]?.meta?.tags?.any { it.equals(label, ignoreCase = true) } == true }

    /** Moves [notes] to the trash, offering Undo. A note that's open is closed first (without its empty-note clean-up). */
    fun trash(notes: List<Note>) {
        val opened = store ?: return
        viewModelScope.launch {
            val session = _session.value
            if (session != null && notes.any { it.id == session.note.id || it.id == session.openedAs }) {
                _session.value = null
                session.save()
            }
            val now = System.currentTimeMillis()
            val moved = mutableListOf<Pair<Note, Note>>()
            for (note in notes) {
                val current = if (session != null && note.id == session.openedAs) session.note else note
                val inTrash = runCatching { withContext(Dispatchers.IO) { opened.trash(current) } }.getOrNull() ?: continue
                moved += current to inTrash
            }
            if (moved.isEmpty()) return@launch
            settings.editTrashLog { log ->
                log.filter { entry -> moved.none { it.second.name == entry.name && it.second.folder == entry.folder } } +
                    moved.map { (_, to) -> TrashEntry(to.name, to.folder, now) }
            }
            val movedIds = moved.mapTo(HashSet()) { it.first.id }
            _library.value = _library.value?.let { library ->
                val trashed = moved.map { it.second }
                val trashedIds = trashed.mapTo(HashSet()) { it.id }
                library.copy(notes = library.notes.filter { it.id !in movedIds }, trash = library.trash.filter { it.id !in trashedIds } + trashed)
            }
            _undo.value = UndoAction(UndoKind.Trashed, moved.size, moved.singleOrNull()?.first?.title) { restore(moved.map { it.second }) }
            poke()
        }
    }

    /**
     * Puts [notes] back from the trash into the notebooks they came from (their folder in the
     * trash says which), making the notebook again if it's gone. Notes trashed by this app's
     * first version, loose in the trash, go to Unsorted.
     */
    fun restore(notes: List<Note>) {
        val opened = store ?: return
        viewModelScope.launch {
            val restored = mutableListOf<Note>()
            for (note in notes) {
                val name = note.folder.ifEmpty { DEFAULT_NOTEBOOK }
                val done = runCatching {
                    withContext(Dispatchers.IO) {
                        val folder = _library.value?.notebook(name)
                        val id = folder?.id ?: opened.ensureFolder(opened.rootId, name)
                        opened.move(note, id, folder?.name ?: name)
                        // Folio removes a notebook's trash folder once it's empty; so does this.
                        if (note.parentId != _library.value?.trashId && opened.children(note.parentId).none { !it.name.startsWith(".") }) {
                            opened.delete(note.parentId)
                        }
                    }
                }.isSuccess
                if (done) restored += note
            }
            settings.editTrashLog { entries -> entries.filter { entry -> restored.none { it.name == entry.name && it.folder == entry.folder } } }
            poke()
        }
    }

    /** Deletes [notes] from the trash for good. */
    fun deleteForever(notes: List<Note>) {
        val opened = store ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { notes.forEach { runCatching { opened.delete(it.id) } } }
            settings.editTrashLog { entries -> entries.filter { entry -> notes.none { it.name == entry.name && it.folder == entry.folder } } }
            val ids = notes.mapTo(HashSet()) { it.id }
            _library.value = _library.value?.let { library -> library.copy(trash = library.trash.filter { it.id !in ids }) }
            poke()
        }
    }

    /** Deletes everything in the trash for good. */
    fun emptyTrash() = deleteForever(_library.value?.trash.orEmpty())

    /** Makes a copy of [note] next to it (Keep's "Make a copy") and calls [onMade] with the copy's id. */
    fun copy(note: Note, onMade: (String) -> Unit) {
        val opened = store ?: return
        viewModelScope.launch {
            _session.value?.takeIf { it.note.id == note.id }?.save()
            val made = runCatching {
                withContext(Dispatchers.IO) { opened.create(note.parentId, note.folder, note.title, opened.read(note.id)) }
            }.getOrNull() ?: return@launch
            addToLibrary(made)
            poke()
            onMade(made.id)
        }
    }

    /**
     * Copies the picture at [uri] (from the photo picker or camera) into the open note's
     * attachments folder and adds it to the top of the note.
     */
    fun addImage(session: NoteSession, uri: Uri) {
        val opened = store ?: return
        val resolver = app.contentResolver
        viewModelScope.launch {
            val extension = resolver.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } ?: "jpg"
            val path = runCatching {
                withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { opened.saveImage(session.note.parentId, it, extension) }
                }
            }.getOrNull() ?: return@launch
            session.addImage(path)
            poke()
        }
    }

    /** The open folder's storage, for drawing pictures; null until a folder is open. */
    fun store(): NoteStore? = store

    /** Dismisses the Undo bar. */
    fun undoShown() {
        _undo.value = null
    }

    /** Changes how lists are sorted. */
    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { settings.setSortOrder(order) }
    }

    /** Changes whether notes open formatted. */
    fun setOpenFormatted(on: Boolean) {
        viewModelScope.launch { settings.setOpenFormatted(on) }
    }

    /** Changes the editor font. */
    fun setMonospace(on: Boolean) {
        viewModelScope.launch { settings.setMonospace(on) }
    }

    /** Changes between grid and list. */
    fun setGrid(on: Boolean) {
        viewModelScope.launch { settings.setGrid(on) }
    }

    /** Changes whether ticked items move to the bottom. */
    fun setTickedToBottom(on: Boolean) {
        viewModelScope.launch { settings.setTickedToBottom(on) }
    }

    private companion object {
        const val SCAN_EVERY_MS = 4_000L
        const val CHECK_OPEN_EVERY_MS = 1_500L
        const val READ_BATCH = 40
    }
}

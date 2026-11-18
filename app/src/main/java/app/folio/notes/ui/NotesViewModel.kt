package app.folio.notes.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.folio.notes.data.Library
import app.folio.notes.data.Note
import app.folio.notes.data.NoteStore
import app.folio.notes.data.Settings
import app.folio.notes.data.SortOrder
import app.folio.notes.data.UNTITLED
import app.folio.notes.data.previewOf
import app.folio.notes.ui.editor.NoteSession
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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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

/** A note just deleted, kept so Undo can write it back. */
data class Deleted(val note: Note, val text: String)

/**
 * The app's state: the notes folder, what's in it, and the open note.
 *
 * The folder is looked at again whenever the phone says something in it changed, and every few
 * seconds while the app is on screen (not every storage provider says), so notes edited on
 * another device and synced into the folder show up by themselves. Texts are read once and then
 * again only when a file's date or size changes.
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

    private val _texts = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Each note's text, by id, for search. Fills in shortly after the list appears. */
    val texts: StateFlow<Map<String, String>> = _texts.asStateFlow()

    private val _previews = MutableStateFlow<Map<String, String>>(emptyMap())

    /** Each note's preview line for the lists, by id. */
    val previews: StateFlow<Map<String, String>> = _previews.asStateFlow()

    /** How lists are sorted. */
    val sortOrder: StateFlow<SortOrder> = settings.sortOrder.stateIn(viewModelScope, SharingStarted.Eagerly, SortOrder.Modified)

    /** Notes open formatted rather than as text. */
    val openFormatted: StateFlow<Boolean> = settings.openFormatted.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** The editor uses a fixed-width font. */
    val monospace: StateFlow<Boolean> = settings.monospace.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _refreshing = MutableStateFlow(false)

    /** A look at the folder asked for by pulling down the list is under way. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _deleted = MutableStateFlow<Deleted?>(null)

    /** The note just deleted, while Undo is offered. */
    val deleted: StateFlow<Deleted?> = _deleted.asStateFlow()

    private val _session = MutableStateFlow<NoteSession?>(null)

    /** The note open in the editor, if any. */
    val session: StateFlow<NoteSession?> = _session.asStateFlow()

    private var store: NoteStore? = null
    private val foreground = MutableStateFlow(false)
    private val pokes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    // What each cached text was read from, so unchanged files aren't read again.
    private val readStamps = HashMap<String, Pair<Long, Long>>()
    // One look at the folder at a time: a pull to refresh can land during a scheduled one.
    private val scanLock = Mutex()

    init {
        viewModelScope.launch {
            settings.folder.distinctUntilChanged().collectLatest { uri ->
                store = null
                _library.value = null
                _texts.value = emptyMap()
                _previews.value = emptyMap()
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
        if (rescan(opened)) _library.value?.let { library -> watched.value = listOf(library.rootId) + library.folders.map { it.id } }
        launch {
            merge(
                pokes,
                watched.flatMapLatest { opened.changes(it) },
                foreground.flatMapLatest { if (it) ticks(SCAN_EVERY_MS) else emptyFlow() },
            ).conflate().collect {
                if (!rescan(opened)) return@collect
                _library.value?.let { library -> watched.value = listOf(library.rootId) + library.folders.map { it.id } }
                _session.value?.checkDisk()
            }
        }
        launch {
            foreground.flatMapLatest { if (it) ticks(CHECK_OPEN_EVERY_MS) else emptyFlow() }.collect {
                _session.value?.checkDisk()
            }
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
        readTexts(opened, scanned)
        true
    }

    /** Reads the text of every note that's new or changed since it was last read. */
    private suspend fun readTexts(opened: NoteStore, library: Library) {
        val ids = library.notes.mapTo(HashSet()) { it.id }
        val stale = library.notes.filter { readStamps[it.id] != (it.modified to it.size) }
        val gone = readStamps.keys.filter { it !in ids }
        if (stale.isEmpty() && gone.isEmpty()) return
        gone.forEach { readStamps.remove(it) }
        val texts = _texts.value.filterKeys { it in ids }.toMutableMap()
        val previews = _previews.value.filterKeys { it in ids }.toMutableMap()
        // Shown in batches, so a big folder's first previews appear without waiting for the rest.
        stale.chunked(READ_BATCH).forEach { batch ->
            withContext(Dispatchers.IO) {
                for (note in batch) {
                    val text = runCatching { opened.read(note.id) }.getOrNull() ?: continue
                    texts[note.id] = text
                    previews[note.id] = previewOf(text, note.title)
                    readStamps[note.id] = note.modified to note.size
                }
            }
            _texts.value = texts.toMap()
            _previews.value = previews.toMap()
        }
        if (stale.isEmpty()) {
            _texts.value = texts.toMap()
            _previews.value = previews.toMap()
        }
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

    /** Makes a new, empty note in the folder at [folderPath] and opens it. Calls [onMade] with its id. */
    fun createNote(folderPath: String, onMade: (String) -> Unit) {
        val opened = store ?: return
        val parentId = _library.value?.folderId(folderPath) ?: opened.rootId
        viewModelScope.launch {
            val note = runCatching { withContext(Dispatchers.IO) { opened.create(parentId, folderPath, UNTITLED) } }.getOrNull()
                ?: return@launch
            _library.value = _library.value?.let { it.copy(notes = it.notes + note) }
            closeSession()
            _session.value = NoteSession(opened, note, viewModelScope, isNew = true, onSaved = ::onSaved)
            onMade(note.id)
            poke()
        }
    }

    /** Makes a folder called [name] inside the one at [parentPath]. */
    fun createFolder(parentPath: String, name: String) {
        val opened = store ?: return
        val parentId = _library.value?.folderId(parentPath) ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { opened.createFolder(parentId, name) } }
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
        val others = library.notes.filter { it.id != note.id && it.id != _session.value?.openedAs }
        _library.value = library.copy(notes = others + note)
        _texts.value = _texts.value + (note.id to text)
        _previews.value = _previews.value + (note.id to previewOf(text, note.title))
        readStamps[note.id] = note.modified to note.size
    }

    /** Deletes [note], keeping its text for a while so Undo can bring it back. */
    fun delete(note: Note) {
        val opened = store ?: return
        viewModelScope.launch {
            val session = _session.value
            if (session != null && session.note.id == note.id) {
                _session.value = null
                session.save()
            }
            val text = runCatching { withContext(Dispatchers.IO) { opened.read(note.id) } }.getOrNull()
                ?: _texts.value[note.id].orEmpty()
            val removed = runCatching { withContext(Dispatchers.IO) { opened.delete(note.id) } }.getOrDefault(false)
            if (!removed) return@launch
            _library.value = _library.value?.let { library -> library.copy(notes = library.notes.filter { it.id != note.id }) }
            _deleted.value = Deleted(note, text)
            poke()
        }
    }

    /** Writes the last deleted note back, under the same name in the same folder. */
    fun undoDelete() {
        val opened = store ?: return
        val deleted = _deleted.value ?: return
        _deleted.value = null
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { opened.create(deleted.note.parentId, deleted.note.folder, deleted.note.title, deleted.text) }
            }
            poke()
        }
    }

    /** Stops offering Undo for the last deletion. */
    fun forgetDeleted() {
        _deleted.value = null
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

    private companion object {
        const val SCAN_EVERY_MS = 4_000L
        const val CHECK_OPEN_EVERY_MS = 1_500L
        const val READ_BATCH = 40
    }
}

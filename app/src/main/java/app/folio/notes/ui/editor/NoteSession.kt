package app.folio.notes.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.folio.notes.data.DiskChange
import app.folio.notes.data.Note
import app.folio.notes.data.NoteStore
import app.folio.notes.data.UNTITLED
import app.folio.notes.data.cleanTitle
import app.folio.notes.data.diskChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One open note: its text as it's being edited, saving a moment after typing stops, and keeping
 * up with the file when it changes underneath (synced in from another device).
 *
 * It lives in the view model rather than the screen, so a save that's due still happens after
 * the screen closes, and rotating the phone doesn't reload the note.
 *
 * [onSaved] is told after each save, so the lists show the new text without reading it again.
 */
class NoteSession(
    private val store: NoteStore,
    initial: Note,
    private val scope: CoroutineScope,
    /** Whether the note was just made with the + button, so closing it untouched removes it. */
    val isNew: Boolean,
    private val onSaved: (Note, String) -> Unit,
) {
    /** The note as it is now. Its id changes when it's renamed. */
    var note by mutableStateOf(initial)
        private set

    /** The id the note had when it was opened, so the screen can still find this session after a rename. */
    val openedAs: String = initial.id

    /** The text being edited, with the cursor; null while the file is first read. */
    var text by mutableStateOf<TextFieldValue?>(null)
        private set

    /** The title as typed; it becomes the file name when [commitTitle] runs. */
    var title by mutableStateOf(if (isNew) "" else initial.title)

    /** The file's newer text from elsewhere, while there are also unsaved edits here to choose between. */
    var conflict by mutableStateOf<String?>(null)
        private set

    /** The file was deleted, or renamed or moved by something else, while open. */
    var gone by mutableStateOf(false)
        private set

    /** The file couldn't be read at all. */
    var failed by mutableStateOf(false)
        private set

    /** Edits are waiting to be written. */
    var saving by mutableStateOf(false)
        private set

    /** Goes up each time newer text from elsewhere is shown, so the screen can say so. */
    var reloads by mutableIntStateOf(0)
        private set

    private val lock = Mutex()
    private var saveJob: Job? = null
    // What the file is known to hold, and its date when it held it.
    private var saved = ""
    private var savedModified = 0L
    // The file not being found once can be a passing provider hiccup; twice in a row, it's gone.
    private var missedChecks = 0

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        val id = note.id
        val loaded = runCatching { withContext(Dispatchers.IO) { store.read(id) to store.modified(id) } }.getOrNull()
        if (loaded == null) {
            failed = true
            return
        }
        saved = loaded.first
        savedModified = loaded.second ?: 0L
        text = TextFieldValue(loaded.first)
    }

    /** Takes what the user typed; it's saved once typing pauses. */
    fun edit(value: TextFieldValue) {
        val changed = value.text != text?.text
        text = value
        if (changed) scheduleSave()
    }

    /** Swaps the whole text (ticking a checkbox in the formatted view), keeping the cursor where it can. */
    fun replaceText(newText: String) {
        val current = text ?: return
        val at = current.selection.start.coerceAtMost(newText.length)
        text = TextFieldValue(newText, TextRange(at))
        scheduleSave()
    }

    private fun scheduleSave() {
        saving = true
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            save()
        }
    }

    /** Writes any unsaved edits now. Does nothing while there's a conflict or the file is gone. */
    suspend fun save() {
        lock.withLock {
            val current = text?.text
            if (current == null || conflict != null || gone || current == saved) {
                saving = false
                return
            }
            val id = note.id
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    store.write(id, current)
                    store.modified(id)
                }
            }
            ok.onSuccess { modified ->
                saved = current
                savedModified = modified ?: savedModified
                onSaved(note.copy(modified = savedModified, size = current.length.toLong()), current)
            }
            // A failed write leaves the text marked unsaved; the next edit or check tries again.
            saving = text?.text != saved
        }
    }

    /**
     * Looks at the file and, if it changed since it was last read or written here, takes in the
     * new text: straight away if there's no unsaved typing, otherwise by offering a choice.
     */
    suspend fun checkDisk() {
        if (text == null || failed) return
        lock.withLock {
            val id = note.id
            val modified = withContext(Dispatchers.IO) { store.modified(id) }
            if (modified == null) {
                missedChecks++
                if (missedChecks >= 2) gone = true
                return
            }
            missedChecks = 0
            gone = false
            if (modified == savedModified) return
            val disk = runCatching { withContext(Dispatchers.IO) { store.read(id) } }.getOrNull() ?: return
            val current = text?.text ?: return
            when (diskChange(disk = disk, saved = saved, screen = current)) {
                DiskChange.InSync -> saved = disk
                DiskChange.Unchanged -> Unit
                DiskChange.Reload -> {
                    showText(disk)
                    saved = disk
                    reloads++
                }
                DiskChange.Conflict -> conflict = disk
            }
            savedModified = modified
        }
    }

    private fun showText(newText: String) {
        val current = text ?: TextFieldValue()
        val at = current.selection.start.coerceAtMost(newText.length)
        text = TextFieldValue(newText, TextRange(at))
    }

    /** Resolves a conflict by keeping what's on screen and writing it over the file. */
    fun keepMine() {
        val theirs = conflict ?: return
        conflict = null
        // The file now holds their version; this makes the next save write ours over it.
        saved = theirs
        scheduleSave()
    }

    /** Resolves a conflict by showing the file's newer text and dropping the edits made here. */
    fun useTheirs() {
        val theirs = conflict ?: return
        conflict = null
        saveJob?.cancel()
        saving = false
        showText(theirs)
        saved = theirs
        reloads++
    }

    /** Saves the text as a new note in the same folder, after the file vanished from under it. */
    fun saveAsNew() {
        val current = text?.text ?: return
        scope.launch {
            lock.withLock {
                val made = runCatching {
                    withContext(Dispatchers.IO) { store.create(note.parentId, note.folder, title.ifBlank { note.title }, current) }
                }.getOrNull() ?: return@launch
                note = made
                title = made.title
                saved = current
                savedModified = made.modified
                gone = false
                missedChecks = 0
                onSaved(made, current)
            }
        }
    }

    /**
     * Renames the file to the typed title, if it changed. A blank title puts the old one back,
     * except on a new note still called "Untitled", which is named after its first line.
     */
    suspend fun commitTitle() {
        lock.withLock {
            if (gone) return
            val firstLine = text?.text?.lineSequence()?.map { it.trim().trimStart('#', '>', '-', '*', ' ') }?.firstOrNull { it.isNotBlank() }
            val wanted = when {
                title.isNotBlank() -> cleanTitle(title)
                isNew && note.title.startsWith(UNTITLED) && firstLine != null -> cleanTitle(firstLine.take(60))
                else -> note.title
            }
            if (wanted == note.title) {
                if (!isNew || title.isNotBlank()) title = note.title
                return
            }
            val renamed = runCatching { withContext(Dispatchers.IO) { store.rename(note, wanted) } }.getOrNull()
            if (renamed != null) {
                note = renamed
                text?.text?.let { onSaved(renamed, it) }
            }
            title = note.title
        }
    }

    /**
     * Finishes with the note: saves, renames to the typed title, and removes a note made with
     * the + button that was left completely empty, so no stray "Untitled.md" files pile up.
     * Returns true if it removed the note.
     */
    suspend fun close(): Boolean {
        saveJob?.cancel()
        save()
        val empty = text?.text?.isBlank() == true && title.isBlank() && note.title.startsWith(UNTITLED)
        if (isNew && empty && !gone) {
            val id = note.id
            return runCatching { withContext(Dispatchers.IO) { store.delete(id) } }.getOrDefault(false)
        }
        commitTitle()
        return false
    }

    private companion object {
        const val SAVE_DELAY_MS = 600L
    }
}

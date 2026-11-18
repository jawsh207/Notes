package app.slate.notes.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import app.slate.notes.data.CheckItem
import app.slate.notes.data.DiskChange
import app.slate.notes.data.ImageRef
import app.slate.notes.data.Note
import app.slate.notes.data.NoteMeta
import app.slate.notes.data.NoteStore
import app.slate.notes.data.UNTITLED
import app.slate.notes.data.addLeadingImage
import app.slate.notes.data.checklistText
import app.slate.notes.data.checklistToText
import app.slate.notes.data.cleanTitle
import app.slate.notes.data.diskChange
import app.slate.notes.data.isChecklist
import app.slate.notes.data.parseChecklist
import app.slate.notes.data.readMeta
import app.slate.notes.data.removeLeadingImage
import app.slate.notes.data.splitFrontMatter
import app.slate.notes.data.splitLeadingImages
import app.slate.notes.data.textToChecklist
import app.slate.notes.data.writeMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One checklist row while it's being edited. [key] stays the same as its text changes, so focus and animations follow it. */
data class EditItem(val key: Long, val text: TextFieldValue, val checked: Boolean, val depth: Int)

/**
 * One open note: its text as it's being edited, saving a moment after typing stops, and keeping
 * up with the file when it changes underneath (synced in from another device).
 *
 * The file is held in three parts, which always join back into exactly the file: the front
 * matter (pin, colour, labels, archive, reminder; never shown as text), the pictures at the top
 * (shown as a strip), and the rest, which is what's typed in. When the rest is a checklist it's
 * edited as rows instead, and written back as Markdown tasks.
 *
 * It lives in the view model rather than the screen, so a save that's due still happens after
 * the screen closes, and rotating the phone doesn't reload the note. [onSaved] is told after
 * each save, so the lists show the new text without reading it again.
 */
class NoteSession(
    private val store: NoteStore,
    initial: Note,
    private val scope: CoroutineScope,
    /** Whether the note was just made with a New button, so closing it untouched removes it. */
    val isNew: Boolean,
    private val onSaved: (Note, String) -> Unit,
) {
    /** The note as it is now. Its id changes when it's renamed. */
    var note by mutableStateOf(initial)
        private set

    /** The id the note had when it was opened, so the screen can still find this session after a rename. */
    val openedAs: String = initial.id

    /** The text being typed (everything after the front matter and top pictures); null while the file is first read. */
    var text by mutableStateOf<TextFieldValue?>(null)
        private set

    /** Pin, colour, labels, archive and reminder. */
    var meta by mutableStateOf(NoteMeta())
        private set

    /** The pictures at the top of the note. */
    var images by mutableStateOf<List<ImageRef>>(emptyList())
        private set

    /** Whether the note is a checklist, edited as rows. */
    var listMode by mutableStateOf(false)
        private set

    /** The checklist rows, while [listMode] is on. */
    val items = mutableStateListOf<EditItem>()

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

    private var front = ""
    private var head = ""
    private var nextKey = 0L
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

    /** The whole file as it would be written now. */
    private fun full(): String = front + head + (text?.text ?: "")

    private suspend fun load() {
        val id = note.id
        val loaded = runCatching { withContext(Dispatchers.IO) { store.read(id) to store.modified(id) } }.getOrNull()
        if (loaded == null) {
            failed = true
            return
        }
        saved = loaded.first
        savedModified = loaded.second ?: 0L
        show(loaded.first, keepCursor = false)
    }

    /** Splits a whole file into the parts the screen shows. */
    private fun show(whole: String, keepCursor: Boolean) {
        val parts = splitFrontMatter(whole)
        val split = splitLeadingImages(parts.body)
        front = parts.front
        head = split.head
        meta = readMeta(parts.front)
        images = split.images
        val at = if (keepCursor) (text?.selection?.start ?: 0).coerceAtMost(split.rest.length) else split.rest.length
        text = TextFieldValue(split.rest, TextRange(at))
        listMode = isChecklist(split.rest)
        if (listMode) rebuildItems(split.rest) else items.clear()
    }

    /** Makes rows from checklist text, reusing row keys by position so focus stays where it was. */
    private fun rebuildItems(rest: String) {
        val parsed = parseChecklist(rest)
        val old = items.toList()
        items.clear()
        parsed.forEachIndexed { index, item ->
            val key = old.getOrNull(index)?.key ?: nextKey++
            val previous = old.getOrNull(index)?.text
            val value = if (previous != null && previous.text == item.text) previous else TextFieldValue(item.text, TextRange(item.text.length))
            items += EditItem(key, value, item.checked, item.depth)
        }
        nextKey = maxOf(nextKey, (items.maxOfOrNull { it.key } ?: -1) + 1)
    }

    /** Writes the rows back into the text and saves soon. */
    private fun itemsChanged() {
        val rest = checklistText(items.map { CheckItem(it.text.text, it.checked, it.depth) })
        text = TextFieldValue(rest)
        scheduleSave()
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
        text = TextFieldValue(newText, TextRange(current.selection.start.coerceAtMost(newText.length)))
        scheduleSave()
    }

    /** Changes a checklist row's text. */
    fun editItem(key: Long, value: TextFieldValue) {
        val index = items.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: return
        val changed = items[index].text.text != value.text
        items[index] = items[index].copy(text = value)
        if (changed) itemsChanged()
    }

    /** Ticks or unticks a checklist row. */
    fun toggleItem(key: Long) {
        val index = items.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: return
        items[index] = items[index].copy(checked = !items[index].checked)
        itemsChanged()
    }

    /**
     * Adds an empty row after the row [after] (at the end when null, or among the unticked rows
     * when ticked ones are kept at the bottom) and returns its key, for the screen to focus.
     */
    fun addItem(after: Long? = null, tickedToBottom: Boolean = true): Long {
        val key = nextKey++
        val index = items.indexOfFirst { it.key == after }
        val at = when {
            index >= 0 -> index + 1
            tickedToBottom -> items.indexOfLast { !it.checked } + 1
            else -> items.size
        }
        val depth = if (index >= 0) items[index].depth else 0
        items.add(at, EditItem(key, TextFieldValue(""), checked = false, depth = depth))
        itemsChanged()
        return key
    }

    /** Removes a checklist row and returns the key of the row before it (to move focus there), if any. */
    fun removeItem(key: Long): Long? {
        val index = items.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: return null
        items.removeAt(index)
        if (items.isEmpty()) items += EditItem(nextKey++, TextFieldValue(""), false, 0)
        itemsChanged()
        return items.getOrNull(index - 1)?.key
    }

    /** Indents a row under the one above it, or brings it back out. */
    fun setDepth(key: Long, depth: Int) {
        val index = items.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: return
        items[index] = items[index].copy(depth = depth.coerceIn(0, 1))
        itemsChanged()
    }

    /** Unticks every row (Keep's "Untick all"). */
    fun untickAll() {
        for (i in items.indices) items[i] = items[i].copy(checked = false)
        itemsChanged()
    }

    /** Removes every ticked row (Keep's "Delete ticked items"). */
    fun deleteTicked() {
        items.removeAll { it.checked }
        if (items.isEmpty()) items += EditItem(nextKey++, TextFieldValue(""), false, 0)
        itemsChanged()
    }

    /** Switches between a checklist and plain text (Keep's "Show/Hide tick boxes"). */
    fun toggleListMode() {
        val rest = text?.text ?: return
        if (listMode) {
            val plain = checklistToText(rest)
            listMode = false
            items.clear()
            text = TextFieldValue(plain, TextRange(plain.length))
        } else {
            val list = textToChecklist(rest)
            listMode = true
            text = TextFieldValue(list)
            rebuildItems(list)
        }
        scheduleSave()
    }

    /** Changes the pin, colour, labels, archive or reminder. Saved straight away. */
    fun updateMeta(change: (NoteMeta) -> NoteMeta) {
        if (text == null) return
        val updated = change(meta)
        if (updated == meta) return
        meta = updated
        front = writeMeta(front, updated)
        scheduleSave(immediately = true)
    }

    /** Adds the picture saved at [path] to the strip at the top. */
    fun addImage(path: String) {
        val rest = text?.text ?: return
        val body = addLeadingImage(head + rest, path)
        val split = splitLeadingImages(body)
        head = split.head
        images = split.images
        scheduleSave(immediately = true)
    }

    /** Takes the picture at [path] out of the note (the image file itself is left where it is). */
    fun removeImage(path: String) {
        val rest = text?.text ?: return
        val body = removeLeadingImage(head + rest, path)
        val split = splitLeadingImages(body)
        head = split.head
        images = split.images
        // Removing the last picture can also drop the blank lines that separated it from the text.
        if (split.rest != rest) text = TextFieldValue(split.rest)
        scheduleSave(immediately = true)
    }

    private fun scheduleSave(immediately: Boolean = false) {
        saving = true
        saveJob?.cancel()
        saveJob = scope.launch {
            if (!immediately) delay(SAVE_DELAY_MS)
            save()
        }
    }

    /** Writes any unsaved edits now. Does nothing while there's a conflict or the file is gone. */
    suspend fun save() {
        lock.withLock {
            if (text == null || conflict != null || gone) {
                saving = false
                return
            }
            val current = full()
            if (current == saved) {
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
                onSaved(note.copy(modified = savedModified, size = current.toByteArray().size.toLong()), current)
            }
            // A failed write leaves the text marked unsaved; the next edit or check tries again.
            saving = full() != saved
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
            when (diskChange(disk = disk, saved = saved, screen = full())) {
                DiskChange.InSync -> saved = disk
                DiskChange.Unchanged -> Unit
                DiskChange.Reload -> {
                    show(disk, keepCursor = true)
                    saved = disk
                    reloads++
                }
                DiskChange.Conflict -> conflict = disk
            }
            savedModified = modified
        }
    }

    /** Resolves a conflict by keeping what's on screen and writing it over the file. */
    fun keepMine() {
        val theirs = conflict ?: return
        conflict = null
        // The file now holds their version; this makes the next save write ours over it.
        saved = theirs
        scheduleSave(immediately = true)
    }

    /** Resolves a conflict by showing the file's newer text and dropping the edits made here. */
    fun useTheirs() {
        val theirs = conflict ?: return
        conflict = null
        saveJob?.cancel()
        saving = false
        show(theirs, keepCursor = true)
        saved = theirs
        reloads++
    }

    /** Saves the text as a new note in the same folder, after the file vanished from under it. */
    fun saveAsNew() {
        if (text == null) return
        val current = full()
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

    /** The first words of the note, for naming a new note that was left without a title. */
    private fun firstLine(): String? {
        val rest = text?.text ?: return null
        val lines = if (listMode) items.map { it.text.text } else rest.lineSequence().toList()
        return lines.map { it.trim().trimStart('#', '>', '-', '*', ' ') }.firstOrNull { it.isNotBlank() }
    }

    /**
     * Renames the file to the typed title, if it changed. A blank title puts the old one back,
     * except on a new note still called "Untitled", which is named after its first line.
     */
    suspend fun commitTitle() {
        lock.withLock {
            if (gone) return
            val first = firstLine()
            val wanted = when {
                title.isNotBlank() -> cleanTitle(title)
                isNew && note.title.startsWith(UNTITLED) && first != null -> cleanTitle(first.take(60))
                else -> note.title
            }
            if (wanted == note.title) {
                if (!isNew || title.isNotBlank()) title = note.title
                return
            }
            val renamed = runCatching { withContext(Dispatchers.IO) { store.rename(note, wanted) } }.getOrNull()
            if (renamed != null) {
                note = renamed
                onSaved(renamed, saved)
            }
            title = note.title
        }
    }

    /** Whether nothing at all has been put in the note. */
    private fun isEmpty(): Boolean {
        val rest = text?.text ?: return false
        val noText = if (listMode) items.all { it.text.text.isBlank() } else rest.isBlank()
        return noText && images.isEmpty() && title.isBlank() && note.title.startsWith(UNTITLED)
    }

    /**
     * Finishes with the note: saves, renames to the typed title, and removes a note made with a
     * New button that was left completely empty, so no stray "Untitled.md" files pile up.
     * Returns true if it removed the note.
     */
    suspend fun close(): Boolean {
        saveJob?.cancel()
        if (isNew && isEmpty() && !gone) {
            val id = note.id
            return runCatching { withContext(Dispatchers.IO) { store.delete(id) } }.getOrDefault(false)
        }
        save()
        commitTitle()
        return false
    }

    private companion object {
        const val SAVE_DELAY_MS = 600L
    }
}

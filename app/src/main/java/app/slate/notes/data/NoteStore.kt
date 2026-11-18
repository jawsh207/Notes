package app.slate.notes.data

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Reads and writes the notes folder. The folder comes from the system folder picker, which
 * hands back a "tree" address and lasting access to everything under it, and nothing else: the
 * app needs no storage permission. Every call here touches storage, so call it off the main
 * thread.
 */
class NoteStore(context: Context, private val tree: Uri) {
    private val resolver: ContentResolver = context.contentResolver

    /** The notes folder's own document id. */
    val rootId: String = DocumentsContract.getTreeDocumentId(tree)

    private fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private fun childrenUri(id: String): Uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)

    /**
     * Looks through the whole folder, sub-folders included (but not hidden ones such as .git
     * or .obsidian), and lists every note. Throws SecurityException if access was taken away.
     */
    fun scan(): Library {
        val notes = mutableListOf<Note>()
        val folders = mutableListOf<Folder>()
        // Breadth-first, with a depth limit so a link loop on odd storage can't run forever.
        val queue = ArrayDeque(listOf(Triple(rootId, "", 0)))
        while (queue.isNotEmpty()) {
            val (parentId, path, depth) = queue.removeFirst()
            resolver.query(childrenUri(parentId), CHILD_COLUMNS, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(2)
                    if (mime == Document.MIME_TYPE_DIR) {
                        if (isHiddenFolder(name) || depth >= MAX_DEPTH) continue
                        val childPath = if (path.isEmpty()) name else "$path/$name"
                        folders += Folder(id, childPath)
                        queue.addLast(Triple(id, childPath, depth + 1))
                    } else if (isNoteFile(name)) {
                        notes += Note(id, name, parentId, path, cursor.getLong(3), cursor.getLong(4))
                    }
                }
            }
        }
        return Library(rootName(), rootId, notes, folders)
    }

    private fun rootName(): String = runCatching {
        resolver.query(documentUri(rootId), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: rootId.substringAfterLast(':').substringAfterLast('/').ifEmpty { rootId }

    /** The note's text. Files are read as UTF-8, which is what every Markdown tool writes. */
    fun read(id: String): String =
        resolver.openInputStream(documentUri(id))?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Couldn't open $id")

    /**
     * Replaces the note's text. "wt" truncates first: plain "w" can leave the end of a longer
     * old version behind on some providers.
     */
    fun write(id: String, text: String) {
        resolver.openOutputStream(documentUri(id), "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Couldn't write $id")
    }

    /** When the file last changed, or null if it's gone (deleted, renamed or moved elsewhere). */
    fun modified(id: String): Long? = runCatching {
        resolver.query(documentUri(id), arrayOf(Document.COLUMN_LAST_MODIFIED), null, null, null)?.use {
            if (it.moveToFirst()) it.getLong(0) else null
        }
    }.getOrNull()

    /** The file names directly inside the folder [parentId], to pick a name that doesn't clash. */
    fun namesIn(parentId: String): List<String> =
        resolver.query(childrenUri(parentId), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.getString(0)?.let(::add) }
        }.orEmpty()

    /**
     * Makes a new note in the folder [parentId] (at [folderPath]) with [text], named after
     * [title] (made unique). It's created as a generic file because some providers add their
     * own extension to a known text type ("Untitled.md.txt").
     */
    fun create(parentId: String, folderPath: String, title: String, text: String = ""): Note {
        val name = fileNameFor(title, namesIn(parentId))
        val uri = DocumentsContract.createDocument(resolver, documentUri(parentId), "application/octet-stream", name)
            ?: error("Couldn't create $name")
        val id = DocumentsContract.getDocumentId(uri)
        if (text.isNotEmpty()) write(id, text)
        return Note(id, name, parentId, folderPath, modified(id) ?: System.currentTimeMillis(), text.length.toLong())
    }

    /** Makes a sub-folder of [parentId] called [name] (made unique). */
    fun createFolder(parentId: String, name: String): String {
        val unique = folderNameFor(name, namesIn(parentId))
        val uri = DocumentsContract.createDocument(resolver, documentUri(parentId), Document.MIME_TYPE_DIR, unique)
            ?: error("Couldn't create $unique")
        return DocumentsContract.getDocumentId(uri)
    }

    /**
     * Gives the note the file name for [title] and returns it as it is now (renaming changes the
     * id on most providers). Leaves it alone when the name wouldn't change.
     */
    fun rename(note: Note, title: String): Note {
        val wanted = cleanTitle(title)
        if (wanted == note.title) return note
        val others = namesIn(note.parentId).filter { !it.equals(note.name, ignoreCase = true) }
        val name = fileNameFor(wanted, others)
        val uri = DocumentsContract.renameDocument(resolver, documentUri(note.id), name) ?: return note
        return note.copy(id = DocumentsContract.getDocumentId(uri), name = name)
    }

    /** Deletes the file. (Undo in the app writes it back from the copy it kept.) */
    fun delete(id: String): Boolean = DocumentsContract.deleteDocument(resolver, documentUri(id))

    /**
     * Emits whenever anything in [folderIds] changes on disk, as soon as it happens.
     *
     * The phone's own storage watches a folder only while someone has its listing open, and
     * tells that listing when a file in it is added, removed or saved. So this keeps a listing
     * of each folder open and listens to it; closing the flow closes them. Providers that don't
     * announce changes stay silent here, which is why the app also checks every few seconds.
     */
    fun changes(folderIds: List<String>): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val cursors = mutableListOf<Cursor>()
        for (id in folderIds.take(MAX_WATCHED)) {
            val cursor = runCatching {
                resolver.query(childrenUri(id), arrayOf(Document.COLUMN_DOCUMENT_ID), null, null, null)
            }.getOrNull() ?: continue
            cursor.registerContentObserver(observer)
            cursors += cursor
        }
        awaitClose {
            cursors.forEach {
                runCatching { it.unregisterContentObserver(observer) }
                it.close()
            }
        }
    }.conflate()

    private companion object {
        val CHILD_COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )
        const val MAX_DEPTH = 12
        const val MAX_WATCHED = 64
    }
}

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
    /** The content resolver every call goes through; pictures are decoded through it too. */
    val resolver: ContentResolver = context.contentResolver

    /** The notes folder's own document id. */
    val rootId: String = DocumentsContract.getTreeDocumentId(tree)

    /** The address of the file or folder [id], to open or decode it. */
    fun documentUri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

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
        // Folio keeps deleted notes in .trash/<notebook>/; loose ones there came from this app's first version.
        val trashId = childId(rootId, TRASH)
        val trash = trashId?.let { id ->
            listNotes(id, "") + children(id).filter { it.isFolder }.flatMap { listNotes(it.id, it.name) }
        }.orEmpty()
        return Library(rootName(), rootId, notes, folders, trash, trashId)
    }

    /** The notes directly inside the folder [parentId] (at [path]), sub-folders left out. */
    private fun listNotes(parentId: String, path: String): List<Note> =
        resolver.query(childrenUri(parentId), CHILD_COLUMNS, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0) ?: continue
                    val name = cursor.getString(1) ?: continue
                    if (cursor.getString(2) != Document.MIME_TYPE_DIR && isNoteFile(name)) {
                        add(Note(id, name, parentId, path, cursor.getLong(3), cursor.getLong(4)))
                    }
                }
            }
        }.orEmpty()

    /** The id of the file or folder called [name] directly inside [parentId], if there is one. */
    fun childId(parentId: String, name: String): String? = runCatching {
        resolver.query(childrenUri(parentId), arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            var found: String? = null
            while (found == null && cursor.moveToNext()) if (cursor.getString(1) == name) found = cursor.getString(0)
            found
        }
    }.getOrNull()

    /** The folder called [name] inside [parentId], made if it isn't there yet. */
    fun ensureFolder(parentId: String, name: String): String = childId(parentId, name)
        ?: DocumentsContract.createDocument(resolver, documentUri(parentId), Document.MIME_TYPE_DIR, name)
            ?.let(DocumentsContract::getDocumentId)
        ?: error("Couldn't make $name")

    /** The id of the file at [path] (relative to the notes folder, "Work/attachments/a.jpg"), if it exists. */
    fun findPath(path: String): String? {
        var id = rootId
        for (part in path.split('/').filter { it.isNotEmpty() }) id = childId(id, part) ?: return null
        return id
    }

    /**
     * Moves [note] into the folder [targetId] (at [targetPath]) and returns it as it is there.
     * Moving keeps the file's date. Providers that can't move, or a name already taken in the
     * target, fall back to writing a copy (under a free name) and deleting the original.
     */
    fun move(note: Note, targetId: String, targetPath: String): Note {
        val moved = if (namesIn(targetId).none { it.equals(note.name, ignoreCase = true) }) {
            runCatching {
                DocumentsContract.moveDocument(resolver, documentUri(note.id), documentUri(note.parentId), documentUri(targetId))
            }.getOrNull()
        } else {
            null
        }
        if (moved != null) {
            return note.copy(id = DocumentsContract.getDocumentId(moved), parentId = targetId, folder = targetPath)
        }
        val copy = create(targetId, targetPath, note.title, read(note.id))
        delete(note.id)
        return copy
    }

    /**
     * Moves [note] to the trash as Folio does, into ".trash/<its notebook>/" (made if needed),
     * and returns it as it is there. Its folder then names the notebook it came from.
     */
    fun trash(note: Note): Note {
        val notebook = note.notebook.ifEmpty { DEFAULT_NOTEBOOK }
        return move(note, ensureFolder(ensureFolder(rootId, TRASH), notebook), notebook)
    }

    /** One thing directly inside a folder. */
    data class Child(val id: String, val name: String, val isFolder: Boolean)

    /** Everything directly inside the folder [parentId]. */
    fun children(parentId: String): List<Child> =
        resolver.query(childrenUri(parentId), arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE), null, null, null)
            ?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val id = cursor.getString(0) ?: continue
                        add(Child(id, cursor.getString(1) ?: continue, cursor.getString(2) == Document.MIME_TYPE_DIR))
                    }
                }
            }.orEmpty()

    /** Moves the file or folder [id] from [fromId] into [toId]. Returns false if the provider can't. */
    fun moveChild(id: String, fromId: String, toId: String): Boolean = runCatching {
        DocumentsContract.moveDocument(resolver, documentUri(id), documentUri(fromId), documentUri(toId)) != null
    }.getOrDefault(false)

    /** The notebook [notebookId]'s colour, from the ".config/color" file Folio keeps in it (0xAARRGGBB), if it has one. */
    fun notebookColor(notebookId: String): Int? = runCatching {
        val config = childId(notebookId, CONFIG) ?: return null
        val file = childId(config, "color") ?: return null
        parseNotebookColor(read(file))
    }.getOrNull()

    /** Gives the notebook [notebookId] a colour, written where and how Folio writes it. */
    fun setNotebookColor(notebookId: String, color: Int) {
        val config = ensureFolder(notebookId, CONFIG)
        val file = childId(config, "color")
            ?: DocumentsContract.createDocument(resolver, documentUri(config), "application/octet-stream", "color")
                ?.let(DocumentsContract::getDocumentId)
            ?: error("Couldn't create the colour file")
        write(file, formatNotebookColor(color))
    }

    /** Renames the notebook folder [notebookId] to [name] and returns its new id. Every note in it gets a new id too. */
    fun renameFolder(notebookId: String, name: String): String {
        val uri = DocumentsContract.renameDocument(resolver, documentUri(notebookId), name) ?: return notebookId
        return DocumentsContract.getDocumentId(uri)
    }

    /**
     * Moves a whole notebook to the trash as Folio does: everything in it goes into
     * ".trash/<name>/", then the emptied folder is removed. Notes clash-checked by [move]; if
     * anything else can't be moved, the notebook folder is kept rather than deleted with it.
     * Returns the notes as they are in the trash.
     */
    fun trashNotebook(notebook: Folder, notes: List<Note>): List<Note> {
        val target = ensureFolder(ensureFolder(rootId, TRASH), notebook.name)
        val moved = notes.filter { it.parentId == notebook.id }.map { move(it, target, notebook.name) }
        var everything = true
        for (child in children(notebook.id)) {
            if (child.name == CONFIG) childId(target, CONFIG)?.let { delete(it) }
            if (!moveChild(child.id, notebook.id, target)) everything = false
        }
        if (everything) delete(notebook.id)
        return moved
    }

    /**
     * Saves a picture from [input] into the "attachments" folder next to notes in [parentId],
     * under a name made from the time, and returns its path as a note writes it
     * ("attachments/image-20261118-031200.jpg").
     */
    fun saveImage(parentId: String, input: java.io.InputStream, extension: String): String {
        val folderId = ensureFolder(parentId, ATTACHMENTS)
        val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val taken = namesIn(folderId).mapTo(HashSet()) { it.lowercase() }
        var name = "image-$stamp.$extension"
        var n = 2
        while (name.lowercase() in taken) name = "image-$stamp-${n++}.$extension"
        val uri = DocumentsContract.createDocument(resolver, documentUri(folderId), "application/octet-stream", name)
            ?: error("Couldn't create $name")
        resolver.openOutputStream(uri, "wt")?.use { input.copyTo(it) } ?: error("Couldn't write $name")
        return "$ATTACHMENTS/$name"
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
        const val TRASH = ".trash"
        const val CONFIG = ".config"
        const val MAX_DEPTH = 12
        const val MAX_WATCHED = 64
    }
}

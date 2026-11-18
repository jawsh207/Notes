package app.slate.notes.data

/**
 * One note: a Markdown file somewhere in the notes folder.
 *
 * [id] is the file's document id from the system's storage provider. It's what every read and
 * write goes through, and it changes when the file is renamed or moved.
 */
data class Note(
    val id: String,
    /** The file name, extension included ("Shopping list.md"). */
    val name: String,
    /** The document id of the folder it's in. */
    val parentId: String,
    /** The folder it's in, relative to the notes folder ("" at the top, "Work/Ideas" deeper). */
    val folder: String,
    /** When the file last changed, in milliseconds since 1970. */
    val modified: Long,
    /** The file's size in bytes. */
    val size: Long,
) {
    /** The name shown for the note: the file name without ".md". */
    val title: String get() = titleFromFileName(name)
}

/** A folder inside the notes folder. [path] is relative to the notes folder ("Work/Ideas"). */
data class Folder(val id: String, val path: String) {
    /** The folder's own name, the last part of its path. */
    val name: String get() = path.substringAfterLast('/')

    /** The path of the folder it's in ("" for one at the top). */
    val parentPath: String get() = path.substringBeforeLast('/', "")
}

/** Everything found in the notes folder in one look: the notes and the folders they're in. */
data class Library(
    /** The notes folder's own name, as shown by the system. */
    val rootName: String,
    /** The notes folder's document id. */
    val rootId: String,
    val notes: List<Note>,
    val folders: List<Folder>,
    /** Notes in the trash (the hidden ".trash" folder at the top, as Obsidian uses). */
    val trash: List<Note> = emptyList(),
    /** The trash folder's document id, once it exists. */
    val trashId: String? = null,
) {
    /** The document id of the folder at [path] ("" is the notes folder itself). */
    fun folderId(path: String): String? = if (path.isEmpty()) rootId else folders.firstOrNull { it.path == path }?.id
}

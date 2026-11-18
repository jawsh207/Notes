package app.slate.notes.data

// File-name rules. A note's title is its file name, so these decide what a title may contain
// and which files in the folder count as notes.

/** The extensions that make a file a note. */
private val NOTE_EXTENSIONS = listOf(".md", ".markdown")

/** The name a new note gets until it's renamed. */
const val UNTITLED = "Untitled"

/** Whether a file is a note: a visible file ending in .md or .markdown. */
fun isNoteFile(name: String): Boolean =
    !name.startsWith('.') && NOTE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) && name.length > it.length }

/**
 * Whether a folder is left out of the lists. Hidden folders (".git", ".obsidian", ".trash",
 * ".stfolder") belong to the tools that sync or manage the folder, not to the user's notes.
 */
fun isHiddenFolder(name: String): Boolean = name.startsWith('.')

/** A note's title: its file name without the extension. */
fun titleFromFileName(name: String): String {
    val extension = NOTE_EXTENSIONS.firstOrNull { name.endsWith(it, ignoreCase = true) } ?: return name
    return name.dropLast(extension.length)
}

/**
 * Makes [title] safe as a file name on every system the folder may be synced to. Windows forbids
 * \ / : * ? " < > | and names ending in a dot or space, and most systems choke on control
 * characters, so those become spaces or are trimmed. Never empty: falls back to [UNTITLED].
 */
fun cleanTitle(title: String): String {
    val replaced = buildString {
        for (c in title) append(if (c in "\\/:*?\"<>|" || c.isISOControl()) ' ' else c)
    }
    val cleaned = replaced.replace(Regex("\\s+"), " ").trim().trimEnd('.', ' ').trimStart('.').trim()
    // 120 leaves room for " 99.md" under the 255-byte limit even with multi-byte letters.
    return cleaned.take(120).trim().ifEmpty { UNTITLED }
}

/**
 * The file name for a note called [title] that doesn't clash with [taken] (other file names in
 * the same folder, compared ignoring case, as on Windows and macOS): "Title.md", then
 * "Title 2.md", "Title 3.md" and so on.
 */
fun fileNameFor(title: String, taken: Collection<String>): String {
    val base = cleanTitle(title)
    val lower = taken.mapTo(HashSet()) { it.lowercase() }
    if ("$base.md".lowercase() !in lower) return "$base.md"
    var n = 2
    while ("$base $n.md".lowercase() in lower) n++
    return "$base $n.md"
}

/** The name for a new folder called [name] that doesn't clash with [taken], as for notes. */
fun folderNameFor(name: String, taken: Collection<String>): String {
    val base = cleanTitle(name)
    val lower = taken.mapTo(HashSet()) { it.lowercase() }
    if (base.lowercase() !in lower) return base
    var n = 2
    while ("$base $n".lowercase() in lower) n++
    return "$base $n"
}

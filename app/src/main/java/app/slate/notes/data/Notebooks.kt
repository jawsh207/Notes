package app.slate.notes.data

// Notebooks, laid out the way Folio (the GNOME notes app) keeps them, so the same folder works
// in both: every note is in a notebook, a notebook is a folder at the top of the notes folder
// (one level only), its colour is in a hidden ".config/color" file inside it, and a deleted
// note goes to ".trash/<notebook>/". Kept free of Android so it can be unit-tested.

/** The notebook new notes go to when none is chosen, and where notes left loose at the top are moved. */
const val DEFAULT_NOTEBOOK = "Unsorted"

/** Folder names a notebook can't have: Folio skips them, as its trash. */
private val RESERVED = setOf("trash")

/** Whether a folder at the top of the notes folder is a notebook (not hidden, not a pictures folder, not Folio's trash). */
fun isNotebookFolder(name: String): Boolean =
    !isHiddenFolder(name) && !name.equals(ATTACHMENTS, ignoreCase = true) && name.lowercase() !in RESERVED

/** The notebook a note at [folder] (its path in the notes folder) belongs to: the path's first part ("" for a loose note). */
fun notebookOf(folder: String): String = folder.substringBefore('/')

/**
 * A safe notebook name made from what was typed, as for note titles. Null when nothing usable
 * is left, or the name is one a notebook can't have.
 */
fun cleanNotebookName(name: String): String? {
    if (name.isBlank()) return null
    val clean = cleanTitle(name)
    return clean.takeIf { isNotebookFolder(it) }
}

/** The colours offered for a new notebook, as 0xAARRGGBB. Folio accepts any colour; these suit both its themes and ours. */
val NOTEBOOK_COLORS: List<Int> = listOf(
    0xFF3584E4.toInt(), // blue
    0xFF2190A4.toInt(), // teal
    0xFF3A944A.toInt(), // green
    0xFFC88800.toInt(), // yellow
    0xFFED5B00.toInt(), // orange
    0xFFE62D42.toInt(), // red
    0xFFD56199.toInt(), // pink
    0xFF9141AC.toInt(), // purple
    0xFF986A44.toInt(), // brown
    0xFF6F8396.toInt(), // slate
)

/**
 * Reads a notebook colour as Folio writes it ("rgb(53,132,228)", "rgba(53,132,228,1)") or as
 * people type it ("#3584e4"). Returns 0xAARRGGBB, or null for anything else.
 */
fun parseNotebookColor(text: String): Int? {
    val t = text.trim().lowercase()
    Regex("^#([0-9a-f]{6})([0-9a-f]{2})?$").matchEntire(t)?.let {
        val rgb = it.groupValues[1].toLong(16)
        val alpha = it.groupValues[2].ifEmpty { "ff" }.toLong(16)
        return ((alpha shl 24) or rgb).toInt()
    }
    Regex("^#([0-9a-f])([0-9a-f])([0-9a-f])$").matchEntire(t)?.let { m ->
        val (r, g, b) = m.destructured
        return parseNotebookColor("#$r$r$g$g$b$b")
    }
    val call = Regex("^rgba?\\(([^)]*)\\)$").matchEntire(t) ?: return null
    val parts = call.groupValues[1].split(',').map { it.trim() }
    if (parts.size !in 3..4) return null
    val channels = parts.take(3).map { it.toDoubleOrNull()?.toInt()?.coerceIn(0, 255) ?: return null }
    val alpha = parts.getOrNull(3)?.let { ((it.toDoubleOrNull() ?: return null).coerceIn(0.0, 1.0) * 255 + 0.5).toInt() } ?: 255
    return (alpha shl 24) or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
}

/** Writes a colour (0xAARRGGBB) the way Folio does: "rgb(53,132,228)". */
fun formatNotebookColor(color: Int): String =
    "rgb(${(color shr 16) and 0xFF},${(color shr 8) and 0xFF},${color and 0xFF})"

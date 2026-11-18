package app.slate.notes.data

import java.text.Normalizer

// Finding notes by words in their title or text. Kept free of Android so it can be unit-tested.

/** One note found by a search, with the bit of text that matched. */
data class SearchHit(
    val note: Note,
    /** A stretch of the note's text around the first match, or null if only the title matched. */
    val excerpt: String?,
    /** Whether every word matched in the title, which puts the note at the top. */
    val inTitle: Boolean,
)

/** Lower case with accents removed, so "Cafe" finds "café". */
fun foldForSearch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

/**
 * The notes where every word of [query] appears in the title or the text ([texts], by note
 * id; notes not read yet match on title only). Title matches come first, then the rest in the
 * order given.
 */
fun searchNotes(notes: List<Note>, texts: Map<String, String>, query: String): List<SearchHit> {
    val words = foldForSearch(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val hits = notes.mapNotNull { note ->
        val title = foldForSearch(note.title)
        val text = texts[note.id].orEmpty()
        val folded = foldForSearch(text)
        if (!words.all { it in title || it in folded }) return@mapNotNull null
        val inTitle = words.all { it in title }
        val first = words.map { folded.indexOf(it) }.filter { it >= 0 }.minOrNull()
        SearchHit(note, first?.let { excerptAround(text, folded, it) }, inTitle)
    }
    return hits.sortedByDescending { it.inTitle }
}

/**
 * About a line of [text] around [at] (an index in [folded], its search form). Folding can change
 * the length of a few letters, so the cut is mapped back by proportion and kept within bounds.
 */
private fun excerptAround(text: String, folded: String, at: Int): String {
    val position = if (folded.length == text.length) at else (at.toLong() * text.length / folded.length.coerceAtLeast(1)).toInt()
    val start = (position - 40).coerceAtLeast(0)
    val end = (position + 120).coerceAtMost(text.length)
    val piece = text.substring(start, end).replace(Regex("\\s+"), " ").trim()
    return (if (start > 0) "…" else "") + piece + (if (end < text.length) "…" else "")
}

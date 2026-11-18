package app.slate.notes.data

import java.text.Collator
import java.util.Locale

// How lists are ordered. Kept free of Android so it can be unit-tested.

/**
 * Sorts [notes] by [order]. Title order compares the way people read (ignoring case and
 * accents) and puts "Note 2" before "Note 10".
 */
fun sortNotes(notes: List<Note>, order: SortOrder, locale: Locale = Locale.getDefault()): List<Note> = when (order) {
    SortOrder.Modified -> notes.sortedWith(compareByDescending<Note> { it.modified }.thenBy { it.title.lowercase() })
    SortOrder.Title -> notes.sortedWith(titleComparator(locale) { it.title })
}

/** Folders in reading order by name, as for note titles. */
fun sortFolders(folders: List<Folder>, locale: Locale = Locale.getDefault()): List<Folder> =
    folders.sortedWith(titleComparator(locale) { it.name })

/** Compares by [key] ignoring case and accents, with runs of digits compared as numbers. */
private fun <T> titleComparator(locale: Locale, key: (T) -> String): Comparator<T> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
    return Comparator { a, b -> compareNatural(key(a), key(b), collator) }
}

/** "Note 2" < "Note 10": digit runs compare by value, the rest through [collator]. */
private fun compareNatural(a: String, b: String, collator: Collator): Int {
    val partsA = Regex("\\d+|\\D+").findAll(a).map { it.value }.toList()
    val partsB = Regex("\\d+|\\D+").findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(partsA.size, partsB.size)) {
        val x = partsA[i]
        val y = partsB[i]
        val result = if (x[0].isDigit() && y[0].isDigit()) {
            x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 }
                ?: x.trimStart('0').compareTo(y.trimStart('0'))
        } else {
            collator.compare(x, y)
        }
        if (result != 0) return result
    }
    return partsA.size.compareTo(partsB.size)
}

package app.slate.notes.data

import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

// How lists are ordered and grouped. Kept free of Android so it can be unit-tested.

/** When a note last changed, as the lists group it under a heading when sorted by date. */
sealed interface DateGroup {
    /** Changed today. */
    data object Today : DateGroup

    /** Changed yesterday. */
    data object Yesterday : DateGroup

    /** Changed in the 7 days before yesterday. */
    data object ThisWeek : DateGroup

    /** Changed in the 30 days before that. */
    data object ThisMonth : DateGroup

    /** Changed longer ago, grouped by calendar month. */
    data class Month(val month: YearMonth) : DateGroup
}

/** Which [DateGroup] a note changed at [modified] falls in, seen from [today]. */
fun dateGroupOf(modified: Long, today: LocalDate, zone: ZoneId): DateGroup {
    val day = Instant.ofEpochMilli(modified).atZone(zone).toLocalDate()
    return when {
        // A clock that's ahead on another device can stamp a file in the future; it's still new.
        !day.isBefore(today) -> DateGroup.Today
        day == today.minusDays(1) -> DateGroup.Yesterday
        day.isAfter(today.minusDays(8)) -> DateGroup.ThisWeek
        day.isAfter(today.minusDays(31)) -> DateGroup.ThisMonth
        else -> DateGroup.Month(YearMonth.from(day))
    }
}

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

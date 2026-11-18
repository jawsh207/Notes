package app.slate.notes.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 18)
    private fun at(date: LocalDate) = date.atTime(12, 0).toInstant(zone).toEpochMilli()

    @Test
    fun groups() {
        assertEquals(DateGroup.Today, dateGroupOf(at(today), today, zone))
        assertEquals(DateGroup.Today, dateGroupOf(at(today.plusDays(2)), today, zone))
        assertEquals(DateGroup.Yesterday, dateGroupOf(at(today.minusDays(1)), today, zone))
        assertEquals(DateGroup.ThisWeek, dateGroupOf(at(today.minusDays(7)), today, zone))
        assertEquals(DateGroup.ThisMonth, dateGroupOf(at(today.minusDays(8)), today, zone))
        assertEquals(DateGroup.ThisMonth, dateGroupOf(at(today.minusDays(30)), today, zone))
        assertEquals(DateGroup.Month(YearMonth.of(2026, 10)), dateGroupOf(at(today.minusDays(31)), today, zone))
    }

    @Test
    fun newestFirst() {
        val notes = listOf(testNote("a.md", 1), testNote("b.md", 3), testNote("c.md", 2))
        assertEquals(listOf("b", "c", "a"), sortNotes(notes, SortOrder.Modified).map { it.title })
    }

    @Test
    fun titlesReadNaturally() {
        val notes = listOf("Note 10.md", "note 2.md", "Éclair.md", "apple.md", "Note 1.md").map { testNote(it) }
        assertEquals(
            listOf("apple", "Éclair", "Note 1", "note 2", "Note 10"),
            sortNotes(notes, SortOrder.Title, Locale.ENGLISH).map { it.title },
        )
    }

    @Test
    fun folders() {
        val folders = listOf(Folder("1", "b/Zed"), Folder("2", "Alpha"), Folder("3", "x/beta"))
        assertEquals(listOf("Alpha", "beta", "Zed"), sortFolders(folders, Locale.ENGLISH).map { it.name })
    }
}

package app.slate.notes.data

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SortingTest {
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

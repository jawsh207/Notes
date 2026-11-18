package app.folio.notes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamesTest {
    @Test
    fun noteFiles() {
        assertTrue(isNoteFile("a.md"))
        assertTrue(isNoteFile("A.MD"))
        assertTrue(isNoteFile("long.markdown"))
        assertFalse(isNoteFile(".md"))
        assertFalse(isNoteFile(".hidden.md"))
        assertFalse(isNoteFile("notes.txt"))
        assertFalse(isNoteFile("image.png"))
    }

    @Test
    fun titles() {
        assertEquals("Shopping list", titleFromFileName("Shopping list.md"))
        assertEquals("x", titleFromFileName("x.markdown"))
        assertEquals("plain", titleFromFileName("plain"))
    }

    @Test
    fun cleaning() {
        assertEquals("a b c", cleanTitle("a/b:c"))
        assertEquals("Ends", cleanTitle("Ends..."))
        assertEquals("hidden", cleanTitle(".hidden"))
        assertEquals(UNTITLED, cleanTitle("  ?? "))
        assertEquals("one two", cleanTitle("one\n\ttwo"))
        assertEquals(120, cleanTitle("x".repeat(300)).length)
    }

    @Test
    fun uniqueNames() {
        assertEquals("Idea.md", fileNameFor("Idea", listOf("other.md")))
        assertEquals("Idea 2.md", fileNameFor("Idea", listOf("idea.md")))
        assertEquals("Idea 3.md", fileNameFor("Idea", listOf("Idea.md", "Idea 2.md")))
        assertEquals("Work 2", folderNameFor("Work", listOf("work")))
    }
}

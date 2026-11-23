package app.slate.notes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotebooksTest {
    @Test
    fun whichFoldersAreNotebooks() {
        assertTrue(isNotebookFolder("Work"))
        assertTrue(isNotebookFolder(DEFAULT_NOTEBOOK))
        assertFalse(isNotebookFolder(".trash"))
        assertFalse(isNotebookFolder(".config"))
        assertFalse(isNotebookFolder("Trash"))
        assertFalse(isNotebookFolder("attachments"))
    }

    @Test
    fun notebookOfAPath() {
        assertEquals("Work", notebookOf("Work"))
        assertEquals("Work", notebookOf("Work/Old"))
        assertEquals("", notebookOf(""))
    }

    @Test
    fun names() {
        assertEquals("Work stuff", cleanNotebookName("  Work/stuff "))
        assertNull(cleanNotebookName("   "))
        assertNull(cleanNotebookName("Trash"))
        assertNull(cleanNotebookName("attachments"))
        assertEquals("hidden", cleanNotebookName(".hidden"))
    }

    @Test
    fun colorsAsFolioWritesThem() {
        assertEquals(0xFF3584E4.toInt(), parseNotebookColor("rgb(53,132,228)"))
        assertEquals(0xFF3584E4.toInt(), parseNotebookColor(" rgba(53, 132, 228, 1)\n"))
        assertEquals(0x803584E4.toInt(), parseNotebookColor("rgba(53,132,228,0.5)"))
        assertEquals(0xFF3584E4.toInt(), parseNotebookColor("#3584E4"))
        assertEquals(0xFFAABBCC.toInt(), parseNotebookColor("#abc"))
        assertNull(parseNotebookColor("blue"))
        assertNull(parseNotebookColor("rgb(1,2)"))
        assertEquals("rgb(53,132,228)", formatNotebookColor(0xFF3584E4.toInt()))
        NOTEBOOK_COLORS.forEach { assertEquals(it, parseNotebookColor(formatNotebookColor(it))) }
    }
}

package app.slate.notes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistTest {
    @Test
    fun detection() {
        assertTrue(isChecklist("- [ ] milk\n- [x] eggs\n\n  - [ ] free range\n"))
        assertTrue(isChecklist("- [ ] "))
        assertFalse(isChecklist("- [ ] milk\nand bread"))
        assertFalse(isChecklist(""))
        assertFalse(isChecklist("- milk"))
    }

    @Test
    fun parseAndWrite() {
        val items = parseChecklist("- [ ] milk\r\n* [X] eggs\n    - [ ] free range")
        assertEquals(listOf(CheckItem("milk", false), CheckItem("eggs", true), CheckItem("free range", false, 1)), items)
        assertEquals("- [ ] milk\n- [x] eggs\n  - [ ] free range\n", checklistText(items))
        assertEquals("", checklistText(emptyList()))
    }

    @Test
    fun convertBothWays() {
        assertEquals("- [ ] milk\n- [ ] eggs\n- [ ] bread\n", textToChecklist("milk\n\n- eggs\n1. bread"))
        assertEquals("- [ ] \n", textToChecklist(""))
        assertEquals("milk\neggs\n", checklistToText("- [ ] milk\n- [x] eggs\n- [ ] "))
    }

    @Test
    fun emptiness() {
        assertTrue(isEmptyChecklist(listOf(CheckItem("", false), CheckItem(" ", true))))
        assertFalse(isEmptyChecklist(listOf(CheckItem("a", false))))
    }
}

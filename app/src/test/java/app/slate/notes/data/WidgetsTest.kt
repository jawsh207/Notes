package app.slate.notes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetsTest {
    private fun note(id: String, name: String, folder: String) = Note(id, name, "parent", folder, 0, 0)

    @Test
    fun encodeAndDecode() {
        val shown = WidgetNote("primary:Notes/Shop\tping.md", "Unsorted/Shopping.md")
        assertEquals(12 to shown, decodeWidgetNote(encodeWidgetNote(12, shown)))
        assertEquals("Shopping", shown.title)
        assertNull(decodeWidgetNote("not a widget"))
        assertNull(decodeWidgetNote("x\tUnsorted/a.md\tid"))
    }

    @Test
    fun pathsAndFollowing() {
        val moved = note("new", "Milk.md", "Home")
        assertEquals("Home/Milk.md", widgetPathOf(moved))
        assertEquals("Milk.md", widgetPathOf(note("x", "Milk.md", "")))
        val widgets = mapOf(1 to WidgetNote("old", "Unsorted/Milk.md"), 2 to WidgetNote("other", "Work/Plan.md"))
        val followed = followNote(widgets, "old", moved)
        assertEquals(WidgetNote("new", "Home/Milk.md"), followed[1])
        assertEquals(widgets[2], followed[2])
    }

    @Test
    fun notebookRename() {
        val widgets = mapOf(1 to WidgetNote("a", "work/Plan.md"), 2 to WidgetNote("b", "Workshop/Tools.md"), 3 to WidgetNote("c", "Loose.md"))
        val renamed = followNotebook(widgets, "Work", "Job")
        assertEquals("Job/Plan.md", renamed[1]?.path)
        assertEquals("Workshop/Tools.md", renamed[2]?.path)
        assertEquals("Loose.md", renamed[3]?.path)
    }

    @Test
    fun checklistLinesInTheFile() {
        val file = "---\npinned: true\n---\n![](attachments/a.jpg)\n\n- [ ] milk\n  - [x] eggs\n"
        val items = fileChecklist(file)!!
        assertEquals(listOf(5, 6), items.map { it.index })
        assertEquals(CheckItem("eggs", true, 1), items[1].value)
        assertNull(fileChecklist("Just words"))
    }

    @Test
    fun tickingOneItem() {
        val file = "---\ncolor: red\n---\n- [ ] milk\n- [x] eggs\n"
        assertEquals("---\ncolor: red\n---\n- [x] milk\n- [x] eggs\n", tickInFile(file, 3, "milk", false))
        assertEquals("---\ncolor: red\n---\n- [ ] milk\n- [ ] eggs\n", tickInFile(file, 4, "eggs", true))
        // The file changed since the widget drew it: nothing is written.
        assertNull(tickInFile(file, 3, "bread", false))
        assertNull(tickInFile(file, 3, "milk", true))
        assertNull(tickInFile(file, 1, "color: red", false))
    }

    @Test
    fun noteLines() {
        val lines = widgetLines("# Plan\nSome **bold** words\nand more\n\n- one\n  - two\n1. first\n- [x] done\n> quoted\n\n```\ncode\n```\n---\n")
        assertEquals(
            listOf(
                WidgetLine("Plan", WidgetLineStyle.Heading),
                WidgetLine("Some bold words", WidgetLineStyle.Text),
                WidgetLine("and more", WidgetLineStyle.Text),
                WidgetLine("• one", WidgetLineStyle.Text),
                WidgetLine("• two", WidgetLineStyle.Text, 1),
                WidgetLine("1. first", WidgetLineStyle.Text),
                WidgetLine("☑ done", WidgetLineStyle.Text),
                WidgetLine("│ quoted", WidgetLineStyle.Text),
                WidgetLine("code", WidgetLineStyle.Code),
            ),
            lines,
        )
        assertEquals(2, widgetLines("a\n\nb\n\nc\n\nd", max = 2).size)
    }

    @Test
    fun reminderList() {
        val list = listOf(
            ScheduledReminder("b", 300, "B"),
            ScheduledReminder("a", 200, "A"),
            ScheduledReminder("past", 50, "Past"),
            ScheduledReminder("a", 400, "A again"),
        )
        assertEquals(listOf("a", "b"), upcomingReminders(list, now = 100).map { it.noteId })
        assertEquals(1, upcomingReminders(list, now = 100, max = 1).size)
    }
}

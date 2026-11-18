package app.slate.notes.data

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class FrontMatterTest {
    @Test
    fun splitIsLossless() {
        val source = "---\ntitle: x\n---\nBody\n"
        val parts = splitFrontMatter(source)
        assertEquals("---\ntitle: x\n---\n", parts.front)
        assertEquals("Body\n", parts.body)
        assertEquals(NoteParts("", "No front\n---\n"), splitFrontMatter("No front\n---\n"))
        assertEquals(NoteParts("", "---\nnever closed"), splitFrontMatter("---\nnever closed"))
        assertEquals(NoteParts("---\na: 1\n---", ""), splitFrontMatter("---\na: 1\n---"))
    }

    @Test
    fun readsEverything() {
        val meta = readMeta("---\npinned: true\narchived: yes\ncolor: Yellow\ntags: [work, \"big, ideas\", #later]\nreminder: 2026-11-18T09:30\n---\n")
        assertEquals(
            NoteMeta(true, true, NoteColor.Yellow, listOf("work", "big, ideas", "later"), LocalDateTime.of(2026, 11, 18, 9, 30)),
            meta,
        )
    }

    @Test
    fun readsBlockTagLists() {
        assertEquals(listOf("a", "b c"), readMeta("---\ntags:\n  - a\n  - \"b c\"\nother: 1\n---\n").tags)
        assertEquals(listOf("one", "two"), readMeta("---\ntags: one, two\n---\n").tags)
    }

    @Test
    fun unknownColourIsDefault() {
        assertEquals(NoteColor.Default, readMeta("---\ncolor: chartreuse\n---\n").color)
        assertEquals(NoteColor.Gray, NoteColor.of("grey"))
    }

    @Test
    fun writingKeepsOtherKeys() {
        val front = "---\ntitle: Trip\npinned: true\ntags:\n  - old\nauthor: me\n---\n"
        val written = writeMeta(front, NoteMeta(color = NoteColor.Blue, tags = listOf("trip", "summer 26")))
        assertEquals("---\ntitle: Trip\nauthor: me\ncolor: blue\ntags: [trip, summer 26]\n---\n", written)
    }

    @Test
    fun writingNothingRemovesTheBlock() {
        assertEquals("", writeMeta("---\npinned: true\n---\n", NoteMeta()))
        assertEquals("", writeMeta("", NoteMeta()))
        assertEquals("---\npinned: true\n---\n", writeMeta("", NoteMeta(pinned = true)))
    }

    @Test
    fun roundTrip() {
        val meta = NoteMeta(true, false, NoteColor.Teal, listOf("a \"quoted\" one", "plain"), LocalDateTime.of(2027, 1, 2, 8, 0))
        assertEquals(meta, readMeta(writeMeta("", meta)))
    }

    @Test
    fun windowsLineEndingsStay() {
        assertEquals("---\r\ntitle: x\r\npinned: true\r\n---\r\n", writeMeta("---\r\ntitle: x\r\n---\r\n", NoteMeta(pinned = true)))
    }

    @Test
    fun labelsAreCleaned() {
        assertEquals("big idea", cleanLabel("  #big   idea "))
    }
}

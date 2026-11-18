package app.folio.notes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test
    fun headingsListsAndParagraphs() {
        val blocks = parseBlocks("# Title\n\nFirst line\nsecond line\n\n- one\n  - two\n1. first\n- [ ] todo\n- [x] done\n---")
        assertEquals(
            listOf(
                Block.Heading(1, "Title", 0),
                Block.Paragraph("First line\nsecond line", 2),
                Block.Item(Marker.Bullet, "one", 0, 5),
                Block.Item(Marker.Bullet, "two", 1, 6),
                Block.Item(Marker.Number(1), "first", 0, 7),
                Block.Item(Marker.Task(false), "todo", 0, 8),
                Block.Item(Marker.Task(true), "done", 0, 9),
                Block.Rule(10),
            ),
            blocks,
        )
    }

    @Test
    fun headingKeepsHashInsideText() {
        assertEquals(listOf(Block.Heading(2, "Learning C#", 0)), parseBlocks("## Learning C#"))
        assertEquals(listOf(Block.Heading(2, "Closed", 0)), parseBlocks("## Closed ##"))
    }

    @Test
    fun tagsAreNotHeadings() {
        assertEquals(listOf(Block.Paragraph("#idea #later", 0)), parseBlocks("#idea #later"))
    }

    @Test
    fun codeFencesKeepTheirContents() {
        val blocks = parseBlocks("```kotlin\n# not a heading\n- not a list\n```\nafter")
        assertEquals(listOf(Block.Code("# not a heading\n- not a list", 0), Block.Paragraph("after", 4)), blocks)
    }

    @Test
    fun unclosedFenceRunsToTheEnd() {
        assertEquals(listOf(Block.Code("a\nb", 0)), parseBlocks("```\na\nb"))
    }

    @Test
    fun quotesJoin() {
        assertEquals(listOf(Block.Quote("one\ntwo", 0), Block.Paragraph("after", 3)), parseBlocks("> one\n> two\n\nafter"))
    }

    @Test
    fun frontMatterIsSkipped() {
        assertEquals(listOf(Block.Paragraph("Body", 4)), parseBlocks("---\ntags: [a]\ntitle: x\n---\nBody"))
        assertEquals(0, bodyStartLine(listOf("---", "never closed")))
    }

    @Test
    fun windowsLineEndings() {
        assertEquals(listOf(Block.Heading(1, "Hi", 0), Block.Item(Marker.Bullet, "a", 0, 1)), parseBlocks("# Hi\r\n- a\r\n"))
    }

    @Test
    fun inlineStyles() {
        val parsed = parseInline("a **bold** and *it* and `co*de*` ~~gone~~")
        assertEquals("a bold and it and co*de* gone", parsed.text)
        assertTrue(Span(2, 6, SpanStyle.Bold) in parsed.spans)
        assertTrue(Span(11, 13, SpanStyle.Italic) in parsed.spans)
        assertTrue(Span(18, 24, SpanStyle.Code) in parsed.spans)
        assertTrue(Span(25, 29, SpanStyle.Strike) in parsed.spans)
    }

    @Test
    fun nestedStyles() {
        val parsed = parseInline("**bold *both***")
        assertEquals("bold both", parsed.text)
        assertTrue(Span(5, 9, SpanStyle.Italic) in parsed.spans)
        assertTrue(Span(0, 9, SpanStyle.Bold) in parsed.spans)
    }

    @Test
    fun snakeCaseIsNotItalic() {
        assertEquals(InlineText("my_file_name.md", emptyList()), parseInline("my_file_name.md"))
    }

    @Test
    fun unmatchedMarksStay() {
        assertEquals("2 * 3 = 6 and **open", parseInline("2 * 3 = 6 and **open").text)
    }

    @Test
    fun links() {
        val parsed = parseInline("see [the site](https://example.com) or https://a.org/x.")
        assertEquals("see the site or https://a.org/x.", parsed.text)
        assertTrue(Span(4, 12, SpanStyle.Link("https://example.com")) in parsed.spans)
        assertTrue(Span(16, 31, SpanStyle.Link("https://a.org/x")) in parsed.spans)
    }

    @Test
    fun escapes() {
        assertEquals("*not italic*", parseInline("\\*not italic\\*").text)
    }

    @Test
    fun toggleTaskFlipsOnlyTheBox() {
        val source = "Shopping\n- [ ] milk\n  * [x] eggs\n- bread"
        assertEquals("Shopping\n- [x] milk\n  * [x] eggs\n- bread", toggleTask(source, 1))
        assertEquals("Shopping\n- [ ] milk\n  * [ ] eggs\n- bread", toggleTask(source, 2))
        assertEquals(source, toggleTask(source, 3))
        assertEquals(source, toggleTask(source, 99))
    }

    @Test
    fun toggleTaskKeepsWindowsLineEnding() {
        assertEquals("- [x] a\r\nb", toggleTask("- [ ] a\r\nb", 0))
    }

    @Test
    fun previewSkipsRepeatedTitleAndMarks() {
        assertEquals("Buy milk · ☐ eggs", previewOf("# Shopping\nBuy **milk**\n\n- [ ] eggs", "Shopping"))
        assertEquals("Other · text", previewOf("# Other\ntext", "Shopping"))
    }

    @Test
    fun previewIsCut() {
        val preview = previewOf("word ".repeat(100), "x", maxLength = 20)
        assertTrue(preview.endsWith("…"))
        assertTrue(preview.length <= 21)
    }
}

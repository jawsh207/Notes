package app.slate.notes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WelcomeTest {
    private val parts = splitFrontMatter(WELCOME_TEXT)
    private val blocks = parseBlocks(parts.body)

    @Test
    fun isPinnedAndNotAChecklist() {
        assertTrue(readMeta(parts.front).pinned)
        assertFalse(isChecklist(parts.body))
    }

    @Test
    fun showsEveryKindOfFormatting() {
        assertTrue(blocks.any { it is Block.Heading && it.level == 1 })
        assertTrue(blocks.any { it is Block.Heading && it.level == 2 })
        assertTrue(blocks.any { it is Block.Item && it.marker == Marker.Bullet && it.depth == 1 })
        assertTrue(blocks.any { it is Block.Item && it.marker is Marker.Number })
        assertTrue(blocks.any { it is Block.Item && it.marker == Marker.Task(false) })
        assertTrue(blocks.any { it is Block.Item && it.marker == Marker.Task(true) })
        assertTrue(blocks.any { it is Block.Quote })
        assertTrue(blocks.any { it is Block.Code })
        assertTrue(blocks.any { it is Block.Rule })
    }

    @Test
    fun inlineMarksAreUnderstood() {
        val styles = blocks.filterIsInstance<Block.Item>().flatMap { parseInline(it.text).spans.map { span -> span.style } } +
            blocks.filterIsInstance<Block.Paragraph>().flatMap { parseInline(it.text).spans.map { span -> span.style } }
        assertTrue(SpanStyle.Bold in styles)
        assertTrue(SpanStyle.Italic in styles)
        assertTrue(SpanStyle.Strike in styles)
        assertTrue(SpanStyle.Code in styles)
        assertEquals(2, styles.count { it is SpanStyle.Link })
    }
}

package app.slate.notes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImagesTest {
    @Test
    fun imageLines() {
        assertEquals(ImageRef("cat", "attachments/my cat.jpg"), imageOf("![cat](attachments/my%20cat.jpg)"))
        assertEquals(ImageRef("", "a b.png"), imageOf("![](<a b.png>)"))
        assertEquals(ImageRef("", "x.png"), imageOf("  ![](x.png \"title\")  "))
        assertNull(imageOf("text ![](x.png)"))
        assertNull(imageOf("![](x.png) and text"))
    }

    @Test
    fun leadingSplit() {
        val split = splitLeadingImages("![](a.png)\n\n![](b.png)\n\nText\n![](c.png)")
        assertEquals("![](a.png)\n\n![](b.png)\n\n", split.head)
        assertEquals(listOf("a.png", "b.png"), split.images.map { it.path })
        assertEquals("Text\n![](c.png)", split.rest)
        assertEquals(LeadingImages("", emptyList(), "\n\nText"), splitLeadingImages("\n\nText"))
    }

    @Test
    fun adding() {
        assertEquals("![](new.png)\n", addLeadingImage("", "new.png"))
        assertEquals("![](new.png)\n\nText", addLeadingImage("Text", "new.png"))
        assertEquals("![](a.png)\n![](new%20one.png)\n\nText", addLeadingImage("![](a.png)\n\nText", "new one.png"))
        assertEquals("![](a.png)\n![](b.png)\n", addLeadingImage("![](a.png)", "b.png"))
    }

    @Test
    fun removing() {
        assertEquals("![](b.png)\n\nText", removeLeadingImage("![](a.png)\n![](b.png)\n\nText", "a.png"))
        assertEquals("Text", removeLeadingImage("![](a.png)\n\nText", "a.png"))
        assertEquals("Text", removeLeadingImage("Text", "a.png"))
    }

    @Test
    fun resolving() {
        assertEquals("Work/attachments/a.jpg", resolveImagePath("Work", "attachments/a.jpg"))
        assertEquals("attachments/a.jpg", resolveImagePath("Work", "../attachments/a.jpg"))
        assertEquals("pics/a.jpg", resolveImagePath("Work/Deep", "/pics/./a.jpg"))
        assertNull(resolveImagePath("", "../outside.jpg"))
        assertNull(resolveImagePath("", "https://example.com/a.jpg"))
    }

    @Test
    fun markdownShowsImages() {
        assertEquals(listOf(Block.Image(ImageRef("", "a.png"), 0), Block.Paragraph("Hi", 1)), parseBlocks("![](a.png)\nHi"))
    }
}

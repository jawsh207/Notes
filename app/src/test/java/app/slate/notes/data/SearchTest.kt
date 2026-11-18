package app.slate.notes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val shopping = testNote("Shopping.md", id = "1")
    private val trip = testNote("Trip to Lisbon.md", id = "2")
    private val texts = mapOf("1" to "Milk and eggs", "2" to "Visit the café near the shopping street")

    @Test
    fun everyWordMustMatch() {
        assertEquals(listOf(trip), searchNotes(listOf(shopping, trip), texts, "cafe street").map { it.note })
        assertEquals(emptyList<SearchHit>(), searchNotes(listOf(shopping, trip), texts, "milk lisbon"))
    }

    @Test
    fun titleMatchesComeFirst() {
        val hits = searchNotes(listOf(trip, shopping), texts, "shopping")
        assertEquals(listOf(shopping, trip), hits.map { it.note })
        assertNull(hits[0].excerpt)
        assertTrue(hits[1].excerpt!!.contains("shopping street"))
    }

    @Test
    fun blankFindsNothing() {
        assertEquals(emptyList<SearchHit>(), searchNotes(listOf(shopping), texts, "   "))
    }

    @Test
    fun unreadNotesMatchOnTitle() {
        assertEquals(listOf(shopping), searchNotes(listOf(shopping), emptyMap(), "shop").map { it.note })
    }
}

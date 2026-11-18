package app.slate.notes.data

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    @Test
    fun quickChoicesInTheMorning() {
        // Wednesday 18 November 2026, 9 AM.
        val choices = quickReminders(LocalDateTime.of(2026, 11, 18, 9, 0)).toMap()
        assertEquals(LocalDateTime.of(2026, 11, 18, 18, 0), choices[QuickReminder.LaterToday])
        assertEquals(LocalDateTime.of(2026, 11, 19, 8, 0), choices[QuickReminder.Tomorrow])
        assertEquals(LocalDateTime.of(2026, 11, 23, 8, 0), choices[QuickReminder.NextWeek])
    }

    @Test
    fun noLaterTodayInTheEvening() {
        assertNull(quickReminders(LocalDateTime.of(2026, 11, 18, 17, 30)).toMap()[QuickReminder.LaterToday])
    }

    @Test
    fun days() {
        val today = LocalDate.of(2026, 11, 18)
        assertEquals(ReminderDay.Today, reminderDayOf(today.atTime(23, 0), today))
        assertEquals(ReminderDay.Tomorrow, reminderDayOf(today.plusDays(1).atTime(0, 0), today))
        assertEquals(ReminderDay.Yesterday, reminderDayOf(today.minusDays(1).atTime(12, 0), today))
        assertEquals(ReminderDay.Other, reminderDayOf(today.plusDays(2).atTime(12, 0), today))
    }

    @Test
    fun recordsRoundTrip() {
        val reminder = ScheduledReminder("primary:Notes/a\tb.md", 123L, "Call\tmum")
        assertEquals(reminder.copy(title = "Call mum"), decodeReminder(encodeReminder(reminder)))
        assertNull(decodeReminder("garbage"))
        val entry = TrashEntry("x\ty.md", "Work/Deep", 456L)
        assertEquals(entry, decodeTrash(encodeTrash(entry)))
    }

    @Test
    fun trashExpiry() {
        val day = 24L * 60 * 60 * 1000
        assertFalse(trashExpired(0, 7 * day))
        assertTrue(trashExpired(0, 7 * day + 1))
    }
}

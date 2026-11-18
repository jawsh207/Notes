package app.slate.notes.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

// Reminder times, as Google Keep offers them, and the record of which alarms are set. Kept
// free of Android so it can be unit-tested.

/** The quick reminder choices. */
enum class QuickReminder {
    /** 6 PM today, offered until 5 PM. */
    LaterToday,

    /** 8 AM tomorrow. */
    Tomorrow,

    /** 8 AM next Monday. */
    NextWeek,
}

/** The quick choices that make sense at [now], with the time each one means. */
fun quickReminders(now: LocalDateTime): List<Pair<QuickReminder, LocalDateTime>> = buildList {
    if (now.toLocalTime().isBefore(LocalTime.of(17, 0))) add(QuickReminder.LaterToday to now.toLocalDate().atTime(18, 0))
    add(QuickReminder.Tomorrow to now.toLocalDate().plusDays(1).atTime(8, 0))
    add(QuickReminder.NextWeek to now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(8, 0))
}

/** How near a reminder's day is, for its label ("Today, 6:00 PM"). */
enum class ReminderDay {
    /** Today. */
    Today,

    /** Tomorrow. */
    Tomorrow,

    /** Yesterday. */
    Yesterday,

    /** Any other day, shown as a date. */
    Other,
}

/** Which [ReminderDay] [time] falls on, seen from [today]. */
fun reminderDayOf(time: LocalDateTime, today: LocalDate): ReminderDay = when (time.toLocalDate()) {
    today -> ReminderDay.Today
    today.plusDays(1) -> ReminderDay.Tomorrow
    today.minusDays(1) -> ReminderDay.Yesterday
    else -> ReminderDay.Other
}

/** An alarm the app has set: for the note [noteId], at [at] (milliseconds since 1970), titled [title]. */
data class ScheduledReminder(val noteId: String, val at: Long, val title: String)

/** One [ScheduledReminder] as one line of text, to keep in the settings file. */
fun encodeReminder(reminder: ScheduledReminder): String =
    listOf(reminder.at.toString(), reminder.title.replace('\t', ' ').replace('\n', ' '), reminder.noteId).joinToString("\t")

/** Reads a line written by [encodeReminder]; null if it's damaged. Document ids may contain tabs, so the id is last. */
fun decodeReminder(line: String): ScheduledReminder? {
    val parts = line.split('\t', limit = 3)
    if (parts.size != 3) return null
    val at = parts[0].toLongOrNull() ?: return null
    return ScheduledReminder(parts[2], at, parts[1])
}

/** A note moved to the trash: its file name there, the folder it came from, and when (milliseconds). */
data class TrashEntry(val name: String, val folder: String, val trashedAt: Long)

/** How long notes stay in the trash before they're deleted for good, as in Google Keep. */
const val TRASH_DAYS = 7L

/** One [TrashEntry] as one line of text, to keep in the settings file. */
fun encodeTrash(entry: TrashEntry): String = listOf(entry.trashedAt.toString(), entry.folder, entry.name).joinToString("\t")

/** Reads a line written by [encodeTrash]; null if it's damaged. */
fun decodeTrash(line: String): TrashEntry? {
    val parts = line.split('\t', limit = 3)
    if (parts.size != 3) return null
    return TrashEntry(parts[2], parts[1], parts[0].toLongOrNull() ?: return null)
}

/** Whether a note trashed at [trashedAt] has been there longer than [TRASH_DAYS] at [now]. */
fun trashExpired(trashedAt: Long, now: Long): Boolean = now - trashedAt > TRASH_DAYS * 24 * 60 * 60 * 1000

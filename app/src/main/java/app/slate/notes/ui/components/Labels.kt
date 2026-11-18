package app.slate.notes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.slate.notes.R
import app.slate.notes.data.ReminderDay
import app.slate.notes.data.reminderDayOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** A reminder time as people say it: "Today, 6:00 PM", "Tomorrow, 8:00 AM", "Mon 23 Nov, 8:00 AM". */
@Composable
fun reminderLabel(time: LocalDateTime): String {
    val context = LocalContext.current
    // The phone's 12- or 24-hour setting.
    val clock = android.text.format.DateFormat.getTimeFormat(context)
    val timeText = clock.format(java.util.Date.from(time.atZone(java.time.ZoneId.systemDefault()).toInstant()))
    val today = LocalDate.now()
    val day = when (reminderDayOf(time, today)) {
        ReminderDay.Today -> stringResource(R.string.day_today)
        ReminderDay.Tomorrow -> stringResource(R.string.day_tomorrow)
        ReminderDay.Yesterday -> stringResource(R.string.day_yesterday)
        ReminderDay.Other -> time.format(
            if (time.year == today.year) DateTimeFormatter.ofPattern("EEE d MMM") else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
        )
    }
    return stringResource(R.string.reminder_at, day, timeText)
}

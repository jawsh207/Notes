package app.slate.notes.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.slate.notes.data.ScheduledReminder
import app.slate.notes.data.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sets and cancels the alarms behind reminders. A reminder lives in the note's file; an alarm
 * can only be set on the phone, so the app sets one for each future reminder it finds when it
 * reads the folder, and keeps a list of them so they can be cancelled when the reminder is
 * removed and set again after the phone restarts.
 *
 * Alarms go off on the minute when the app is allowed exact alarms (Settings → Apps → Special
 * app access → Alarms & reminders); otherwise Android may hold them for a few minutes to save
 * battery.
 */
object ReminderAlarms {
    /** The broadcast an alarm sends to [ReminderReceiver]. */
    const val ACTION_REMIND = "app.slate.notes.action.REMIND"

    /** The note's document id, in the alarm and in the notification's tap. */
    const val EXTRA_NOTE_ID = "note_id"

    /** The note's title, to show in the notification. */
    const val EXTRA_TITLE = "title"

    private val lock = Mutex()

    /** Makes the phone's alarms match [wanted]: sets new ones, cancels ones no longer wanted. Past times are skipped. */
    suspend fun sync(context: Context, wanted: List<ScheduledReminder>) = lock.withLock {
        val settings = Settings(context)
        val now = System.currentTimeMillis()
        val future = wanted.filter { it.at > now }.distinct()
        val previous = settings.scheduledReminders.first()
        if (previous.toSet() == future.toSet()) return@withLock
        // Cancelling first: an alarm for the same note at a new time shares its identity with the old one.
        previous.filter { it !in future }.forEach { cancel(context, it) }
        future.filter { it !in previous }.forEach { schedule(context, it) }
        settings.setScheduledReminders(future)
    }

    /** Sets every recorded future alarm again (alarms don't survive a restart). */
    suspend fun rescheduleAll(context: Context) = lock.withLock {
        val now = System.currentTimeMillis()
        Settings(context).scheduledReminders.first().filter { it.at > now }.forEach { schedule(context, it) }
    }

    private fun schedule(context: Context, reminder: ScheduledReminder) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = alarmIntent(context, reminder)
        runCatching {
            if (alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, intent)
            } else {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, intent)
            }
        }
    }

    private fun cancel(context: Context, reminder: ScheduledReminder) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context, reminder))
    }

    /** One alarm per note: the note id in the address makes each note's alarm its own. */
    private fun alarmIntent(context: Context, reminder: ScheduledReminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMIND)
            .setData(Uri.fromParts("note", reminder.noteId, null))
            .putExtra(EXTRA_NOTE_ID, reminder.noteId)
            .putExtra(EXTRA_TITLE, reminder.title)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

package app.slate.notes.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.slate.notes.MainActivity
import app.slate.notes.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Shows a reminder's notification when its alarm goes off, and sets the alarms again after the
 * phone restarts or the app is updated (both clear them). Tapping the notification opens the note.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderAlarms.ACTION_REMIND -> notify(context, intent)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Reading the settings file takes a moment; goAsync keeps the receiver alive for it.
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                    try {
                        ReminderAlarms.rescheduleAll(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun notify(context: Context, intent: Intent) {
        val id = intent.getStringExtra(ReminderAlarms.EXTRA_NOTE_ID) ?: return
        val title = intent.getStringExtra(ReminderAlarms.EXTRA_TITLE).orEmpty()
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.reminders), NotificationManager.IMPORTANCE_HIGH),
        )
        val open = Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_NOTE)
            .putExtra(ReminderAlarms.EXTRA_NOTE_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val tap = PendingIntent.getActivity(context, id.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.reminder_notification))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        // Without notification access (Android 13+ asks), this is refused; there's nowhere else to show it.
        runCatching { manager.notify(id.hashCode(), notification) }
    }

    private companion object {
        const val CHANNEL = "reminders"
    }
}

package app.slate.notes.widgets

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * The background refresh: every half hour, while any note, list or reminders widget is on a
 * home screen, the widgets read their notes again. That's how changes synced into the folder
 * while the app is closed reach them, and how "Today" in the reminders widget becomes
 * "Tomorrow" overnight. While the app is open, widgets are refreshed as soon as it sees a change,
 * so this only fills the gaps.
 *
 * Android's WorkManager runs it, spreading it out to save battery; it can't run more often than
 * every 15 minutes.
 */
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!Widgets.anyPlaced(context)) {
            stopIfUnused(context)
            return Result.success()
        }
        Widgets.forgetRemoved(context)
        Widgets.refreshAll(context)
        return Result.success()
    }

    /** Starting and stopping the refresh. */
    companion object {
        private const val NAME = "widget_refresh"
        private const val EVERY_MINUTES = 30L

        /** Starts the refresh, unless it's already running. */
        fun start(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(EVERY_MINUTES, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Stops the refresh once no widget needs it. */
        fun stopIfUnused(context: Context) {
            if (!Widgets.anyPlaced(context)) WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}

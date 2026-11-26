package app.slate.notes.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.slate.notes.R
import app.slate.notes.data.ScheduledReminder
import app.slate.notes.data.Settings
import app.slate.notes.data.upcomingReminders
import app.slate.notes.ui.components.reminderLabel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * The reminders widget: the next reminders, soonest first, each opening its note when tapped.
 * It lists the alarms the app has set, which are every future reminder in the notes as of the
 * app's last look at the folder, so it needs no reading of the folder itself.
 */
class RemindersWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = load(context)
        provideContent {
            // Bumped by Widgets.refresh; also moves past reminders off the list.
            val version = currentState(Widgets.VERSION)
            val reminders by produceState(first, version) { if (version != null) value = load(context) }
            GlanceTheme { RemindersContent(reminders) }
        }
    }

    private suspend fun load(context: Context): List<ScheduledReminder> =
        upcomingReminders(Settings(context).scheduledReminders.first(), System.currentTimeMillis())
}

/** What the reminders widget draws for [reminders]. */
@Composable
private fun RemindersContent(reminders: List<ScheduledReminder>) {
    val context = LocalContext.current
    val openApp = actionStartActivity(Widgets.openAppIntent(context))
    WidgetPanel(null) {
        WidgetHeader(context.getString(R.string.reminders), openApp, R.drawable.ic_widget_reminder)
        if (reminders.isEmpty()) {
            WidgetMessage(context.getString(R.string.widget_no_reminders), null, openApp)
        } else {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(reminders.withIndex().toList(), itemId = { it.index.toLong() }) { (_, reminder) ->
                    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(reminder.at), ZoneId.systemDefault())
                    Column(
                        GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable(actionStartActivity(Widgets.openNoteIntent(context, reminder.noteId))),
                    ) {
                        Text(
                            reminder.title,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                        )
                        Text(
                            reminderLabel(context, time),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

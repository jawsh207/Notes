package app.slate.notes.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

// The receivers Android talks to about each kind of widget: it names them in the widget picker
// (see AndroidManifest.xml and res/xml/widget_*.xml) and tells them when widgets are placed,
// updated and removed. Glance does the drawing; these only say which widget is which.

/**
 * A receiver for a widget that reads the folder. Placing one (or the phone restarting) makes
 * sure the background refresh is running; removing the last one of every kind stops it.
 */
abstract class FolderWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetRefreshWorker.start(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.stopIfUnused(context)
    }
}

/** The note widget's receiver. */
class NoteWidgetReceiver : FolderWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteWidget()
}

/** The list widget's receiver. */
class ChecklistWidgetReceiver : FolderWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChecklistWidget()
}

/** The reminders widget's receiver. */
class RemindersWidgetReceiver : FolderWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RemindersWidget()
}

/** The New note and New list widget's receiver. It shows nothing from the folder, so it needs no refreshing. */
class NewNoteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewNoteWidget()
}

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
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.slate.notes.R
import app.slate.notes.data.WidgetLineStyle
import app.slate.notes.data.widgetLines

/**
 * The note widget: one note on the home screen, in its colour, scrolling if it's long. Tapping
 * it opens the note. Which note is picked when the widget is placed ([WidgetSetupActivity]).
 */
class NoteWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val first = Widgets.loadNote(context, widgetId)
        provideContent {
            // Bumped by Widgets.refresh, which is how a running widget learns its note changed.
            val version = currentState(Widgets.VERSION)
            val shown by produceState(first, version) { if (version != null) value = Widgets.loadNote(context, widgetId) }
            GlanceTheme { NoteWidgetContent(widgetId, shown) }
        }
    }
}

/** What the note widget [widgetId] draws for [shown]. */
@Composable
private fun NoteWidgetContent(widgetId: Int, shown: ShownNote) {
    val context = LocalContext.current
    val ready = shown as? ShownNote.Ready
    WidgetPanel(ready?.let { noteBackground(it.color) }) {
        if (ready == null) {
            NoteProblem(widgetId, shown, lists = false)
        } else {
            val open = actionStartActivity(Widgets.openNoteIntent(context, ready.noteId))
            WidgetHeader(ready.title, open)
            val lines = widgetLines(ready.lines)
            if (lines.isEmpty()) {
                WidgetMessage(context.getString(R.string.widget_empty_note), null, open)
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(lines.withIndex().toList(), itemId = { it.index.toLong() }) { (_, line) ->
                        Text(
                            line.text,
                            GlanceModifier.fillMaxWidth().padding(start = (12 * line.depth).dp, bottom = 3.dp).clickable(open),
                            style = when (line.style) {
                                WidgetLineStyle.Heading -> TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                WidgetLineStyle.Code -> TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                WidgetLineStyle.Text -> TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp)
                            },
                        )
                    }
                }
            }
        }
    }
}

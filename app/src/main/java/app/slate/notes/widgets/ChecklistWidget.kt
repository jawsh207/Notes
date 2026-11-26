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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import app.slate.notes.R
import app.slate.notes.data.tickInFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The list widget: one checklist on the home screen with tick boxes that work there. A tick
 * changes just that item's box in the file ("- [ ]" to "- [x]"), as ticking in the formatted
 * view does, so the app (and any device the folder syncs to) sees it. Tapping the title opens
 * the list. Which list is picked when the widget is placed ([WidgetSetupActivity]).
 */
class ChecklistWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val first = Widgets.loadNote(context, widgetId)
        provideContent {
            // Bumped by Widgets.refresh, which is how a running widget learns its list changed.
            val version = currentState(Widgets.VERSION)
            val shown by produceState(first, version) { if (version != null) value = Widgets.loadNote(context, widgetId) }
            GlanceTheme { ChecklistContent(widgetId, shown) }
        }
    }
}

/** What the list widget [widgetId] draws for [shown]. */
@Composable
private fun ChecklistContent(widgetId: Int, shown: ShownNote) {
    val context = LocalContext.current
    val ready = shown as? ShownNote.Ready
    WidgetPanel(ready?.let { noteBackground(it.color) }) {
        if (ready == null) {
            NoteProblem(widgetId, shown, lists = true)
        } else {
            val open = actionStartActivity(Widgets.openNoteIntent(context, ready.noteId))
            WidgetHeader(ready.title, open)
            val rows = ready.checklist?.filter { it.value.text.isNotBlank() }
            when {
                rows == null -> WidgetMessage(context.getString(R.string.widget_not_a_list), context.getString(R.string.widget_choose_another), actionStartActivity(Widgets.setupIntent(context, widgetId)))
                rows.isEmpty() -> WidgetMessage(context.getString(R.string.widget_empty_list), null, open)
                else -> {
                    // As in the app: ticked items after the others, unless that's turned off in Settings.
                    val ordered = if (ready.tickedToBottom) rows.filter { !it.value.checked } + rows.filter { it.value.checked } else rows
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(ordered, itemId = { it.index.toLong() }) { entry ->
                            val item = entry.value
                            CheckBox(
                                checked = item.checked,
                                onCheckedChange = actionRunCallback<TickItem>(
                                    actionParametersOf(
                                        TickItem.NOTE to ready.noteId,
                                        TickItem.LINE to entry.index,
                                        TickItem.TEXT to item.text,
                                        TickItem.CHECKED to item.checked,
                                    ),
                                ),
                                modifier = GlanceModifier.fillMaxWidth().padding(start = (16 * item.depth).dp),
                                text = item.text,
                                style = TextStyle(
                                    color = if (item.checked) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface,
                                    fontSize = 14.sp,
                                    textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None,
                                ),
                                maxLines = 2,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A tick box tapped in the list widget. Reads the list, and if the line still holds the item
 * the widget showed, flips its box and writes the file; if the file changed in the meantime,
 * nothing is written and the widget just shows the list as it is now.
 */
class TickItem : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val noteId = parameters[NOTE] ?: return
        val line = parameters[LINE] ?: return
        val text = parameters[TEXT] ?: return
        val checked = parameters[CHECKED] ?: return
        Widgets.openStore(context)?.let { store ->
            withContext(Dispatchers.IO) {
                runCatching {
                    tickInFile(store.read(noteId), line, text, checked)?.let { store.write(noteId, it) }
                }
            }
        }
        Widgets.refresh(context, ChecklistWidget(), glanceId)
        // Another widget may show the same note.
        Widgets.refreshSoon(context)
    }

    /** What a tick box tells [TickItem]: which note, which line, and what that line said. */
    companion object {
        /** The note's document id. */
        val NOTE = ActionParameters.Key<String>("note")

        /** The item's line in the file, counting from 0. */
        val LINE = ActionParameters.Key<Int>("line")

        /** The item's words as the widget showed them. */
        val TEXT = ActionParameters.Key<String>("text")

        /** Whether the item was ticked as the widget showed it. */
        val CHECKED = ActionParameters.Key<Boolean>("checked")
    }
}

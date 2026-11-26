package app.slate.notes.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import app.slate.notes.R
import app.slate.notes.data.DEFAULT_NOTEBOOK
import app.slate.notes.data.Note
import app.slate.notes.data.NoteInfo
import app.slate.notes.data.Settings
import app.slate.notes.data.SortOrder
import app.slate.notes.data.noteInfoOf
import app.slate.notes.data.sortFolders
import app.slate.notes.data.sortNotes
import app.slate.notes.data.widgetNoteOf
import app.slate.notes.ui.components.BackTopBar
import app.slate.notes.ui.components.EmptyState
import app.slate.notes.ui.components.ListRow
import app.slate.notes.ui.components.SEGMENT_GAP
import app.slate.notes.ui.components.SectionHeader
import app.slate.notes.ui.components.segmentShape
import app.slate.notes.ui.theme.NotesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Picks the note a note widget shows, or the list a list widget shows. Android opens it when
 * one of those widgets is placed, and again when the widget is reconfigured (a long press on it
 * on most home screens) or its note can't be found. Until a note is picked, Android takes the
 * widget back off the home screen if this is closed.
 */
class WidgetSetupActivity : ComponentActivity() {
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Leaving without a pick cancels placing the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider?.className
        val listsOnly = provider == ChecklistWidgetReceiver::class.java.name
        setContent {
            NotesTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    WidgetSetupScreen(listsOnly, onPick = { pick(it, listsOnly) }, onBack = ::finish)
                }
            }
        }
    }

    private fun pick(note: Note, listsOnly: Boolean) {
        lifecycleScope.launch {
            Settings(this@WidgetSetupActivity).editWidgetNotes { it + (widgetId to widgetNoteOf(note)) }
            runCatching {
                val glanceId = GlanceAppWidgetManager(this@WidgetSetupActivity).getGlanceIdBy(widgetId)
                Widgets.refresh(this@WidgetSetupActivity, if (listsOnly) ChecklistWidget() else NoteWidget(), glanceId)
            }
            WidgetRefreshWorker.start(this@WidgetSetupActivity)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}

/** What the setup screen has found in the folder. */
private sealed interface Choices {
    /** Still reading the folder. */
    data object Loading : Choices

    /** No folder is chosen, or it can't be reached. */
    data object NoFolder : Choices

    /** The notes to pick from, by notebook, with what each says. */
    data class Ready(val notebooks: List<Pair<String, List<Pair<Note, NoteInfo>>>>) : Choices
}

/** The list of notes (or with [listsOnly], checklists) by notebook, pinned first in each. Tapping one calls [onPick]. */
@Composable
private fun WidgetSetupScreen(listsOnly: Boolean, onPick: (Note) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var choices by remember { mutableStateOf<Choices>(Choices.Loading) }
    LaunchedEffect(Unit) {
        val store = Widgets.openStore(context)
        choices = if (store == null) {
            Choices.NoFolder
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val library = store.scan()
                    val notes = sortNotes(library.notes, SortOrder.Title).mapNotNull { note ->
                        val info = runCatching { noteInfoOf(store.read(note.id), note.title) }.getOrNull() ?: return@mapNotNull null
                        if (listsOnly && info.checklist == null) null else note to info
                    }
                    val byNotebook = notes.groupBy { it.first.notebook.ifEmpty { DEFAULT_NOTEBOOK } }
                    Choices.Ready(
                        sortFolders(library.notebooks).map { it.name }.plus(byNotebook.keys).distinct()
                            .mapNotNull { name -> byNotebook[name]?.let { list -> name to list.sortedByDescending { it.second.meta.pinned } } },
                    )
                }.getOrDefault(Choices.NoFolder)
            }
        }
    }
    Scaffold(
        topBar = { BackTopBar(stringResource(if (listsOnly) R.string.widget_choose_list else R.string.widget_choose_note), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when (val current = choices) {
            Choices.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            Choices.NoFolder -> EmptyState(
                Icons.Outlined.Folder,
                stringResource(R.string.widget_no_folder),
                stringResource(R.string.widget_no_folder_body),
                Modifier.padding(padding),
            )
            is Choices.Ready -> if (current.notebooks.isEmpty()) {
                EmptyState(
                    if (listsOnly) Icons.Outlined.Checklist else Icons.Outlined.Description,
                    stringResource(if (listsOnly) R.string.widget_no_lists else R.string.widget_no_notes),
                    stringResource(if (listsOnly) R.string.widget_no_lists_body else R.string.widget_no_notes_body),
                    Modifier.padding(padding),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
                ) {
                    for ((notebook, notes) in current.notebooks) {
                        item(key = "notebook:$notebook") { SectionHeader(notebook) }
                        itemsIndexed(notes, key = { _, (note, _) -> note.id }) { index, (note, info) ->
                            ListRow(
                                icon = if (info.checklist != null) Icons.Outlined.Checklist else Icons.Outlined.Description,
                                title = note.title,
                                shape = segmentShape(index, notes.size),
                                summary = summaryOf(info),
                                onClick = { onPick(note) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            Spacer(Modifier.height(SEGMENT_GAP))
                        }
                    }
                }
            }
        }
    }
}

/** The line under a note in the setup list: how many items a list has, or the start of a note's text. */
@Composable
private fun summaryOf(info: NoteInfo): String? {
    val list = info.checklist
    if (list != null) {
        val count = list.count { it.text.isNotBlank() }
        return pluralStringResource(R.plurals.widget_items, count, count)
    }
    return info.preview.ifBlank { null }
}

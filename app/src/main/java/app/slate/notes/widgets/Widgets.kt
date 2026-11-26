package app.slate.notes.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import app.slate.notes.MainActivity
import app.slate.notes.data.Note
import app.slate.notes.data.NoteColor
import app.slate.notes.data.NoteStore
import app.slate.notes.data.Settings
import app.slate.notes.data.fileChecklist
import app.slate.notes.data.followNote
import app.slate.notes.data.followNotebook
import app.slate.notes.data.readMeta
import app.slate.notes.data.splitFrontMatter
import app.slate.notes.data.splitLeadingImages
import app.slate.notes.data.CheckItem
import app.slate.notes.data.titleFromFileName
import app.slate.notes.reminders.ReminderAlarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a note or checklist widget has to show. */
sealed interface ShownNote {
    /** No notes folder is chosen yet, or the app lost access to it. */
    data object NoFolder : ShownNote

    /** The widget hasn't been given a note yet. */
    data object NotChosen : ShownNote

    /** The note the widget showed, called [title], isn't in the folder any more (deleted, in the trash, or renamed elsewhere). */
    data class Missing(val title: String) : ShownNote

    /**
     * The note [noteId], called [title], in [color]. [lines] is the text after its front matter
     * and top pictures; [checklist] its items with their lines in the file, when it's a
     * checklist. [tickedToBottom] is the app's setting for where ticked items go.
     */
    data class Ready(
        val noteId: String,
        val title: String,
        val color: NoteColor,
        val lines: String,
        val checklist: List<IndexedValue<CheckItem>>?,
        val tickedToBottom: Boolean,
    ) : ShownNote
}

/**
 * Everything the home screen widgets share: reading the note a widget shows, keeping widgets
 * pointed at notes the app renames or moves, refreshing them, and the taps that open the app.
 *
 * Widgets don't run inside the app's screens, so they read the folder themselves, the same way
 * the app does. They're refreshed whenever the app sees a change, after a tick in a widget,
 * and every half hour in the background ([WidgetRefreshWorker]) for changes synced in while the
 * app is closed.
 */
object Widgets {
    /** A number in each widget's own state, changed to make it read its note again. */
    val VERSION = longPreferencesKey("version")

    // Widgets are refreshed from a scope of their own, so a refresh asked for as the app closes still happens.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    /** The notes folder, if one is chosen and the app still has access to it. */
    suspend fun openStore(context: Context): NoteStore? {
        val uri = Settings(context).folder.first() ?: return null
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == uri && it.isReadPermission && it.isWritePermission
        }
        return if (granted) NoteStore(context.applicationContext, Uri.parse(uri)) else null
    }

    /** Reads the note the widget [widgetId] shows. */
    suspend fun loadNote(context: Context, widgetId: Int): ShownNote {
        val settings = Settings(context)
        val chosen = settings.widgetNotes.first()[widgetId] ?: return ShownNote.NotChosen
        val store = openStore(context) ?: return ShownNote.NoFolder
        val found = withContext(Dispatchers.IO) {
            runCatching { chosen.noteId to store.read(chosen.noteId) }.getOrNull()
                // The id stops working when the file is renamed or moved; the same path may still find it.
                ?: store.findPath(chosen.path)?.let { id -> runCatching { id to store.read(id) }.getOrNull() }
        } ?: return ShownNote.Missing(chosen.title)
        val (id, text) = found
        if (id != chosen.noteId) settings.editWidgetNotes { it + (widgetId to chosen.copy(noteId = id)) }
        val parts = splitFrontMatter(text)
        return ShownNote.Ready(
            noteId = id,
            title = titleFromFileName(chosen.path.substringAfterLast('/')),
            color = readMeta(parts.front).color,
            lines = splitLeadingImages(parts.body).rest,
            checklist = fileChecklist(text),
            tickedToBottom = settings.tickedToBottom.first(),
        )
    }

    /** The widgets that read the folder, each with the receiver Android knows it by. */
    private fun dataWidgets(): List<Pair<GlanceAppWidget, Class<*>>> = listOf(
        NoteWidget() to NoteWidgetReceiver::class.java,
        ChecklistWidget() to ChecklistWidgetReceiver::class.java,
        RemindersWidget() to RemindersWidgetReceiver::class.java,
    )

    /** The ids of the placed widgets of the kind [receiver] is for. */
    private fun placed(context: Context, receiver: Class<*>): IntArray =
        AppWidgetManager.getInstance(context)?.getAppWidgetIds(ComponentName(context, receiver)) ?: IntArray(0)

    /** Whether any widget that reads the folder is on a home screen, so the background refresh is needed. */
    fun anyPlaced(context: Context): Boolean = dataWidgets().any { (_, receiver) -> placed(context, receiver).isNotEmpty() }

    /** Forgets which notes removed widgets showed. */
    suspend fun forgetRemoved(context: Context) {
        val kept = (placed(context, NoteWidgetReceiver::class.java) + placed(context, ChecklistWidgetReceiver::class.java)).toSet()
        Settings(context).editWidgetNotes { notes -> notes.filterKeys { it in kept } }
    }

    /** Makes every placed widget read its note (or the reminders) again. */
    suspend fun refreshAll(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        for ((widget, _) in dataWidgets()) {
            for (id in runCatching { manager.getGlanceIds(widget.javaClass) }.getOrDefault(emptyList())) {
                runCatching { refresh(context, widget, id) }
            }
        }
    }

    /** Makes the one widget [id] of kind [widget] read its note again. */
    suspend fun refresh(context: Context, widget: GlanceAppWidget, id: GlanceId) {
        updateAppWidgetState(context, id) { it[VERSION] = System.nanoTime() }
        widget.update(context, id)
    }

    /**
     * Refreshes every widget a moment from now. Changes often come in a burst (typing, a sync
     * landing many files), so each call pushes the refresh back rather than adding another.
     */
    fun refreshSoon(context: Context) {
        val app = context.applicationContext
        synchronized(this) {
            pending?.cancel()
            pending = scope.launch {
                delay(REFRESH_DELAY_MS)
                runCatching { refreshAll(app) }
            }
        }
    }

    /** Points widgets that showed a note in [moves] (its old id, then the note as it is now) at it again. */
    suspend fun notesMoved(context: Context, moves: List<Pair<String, Note>>) {
        val real = moves.filter { (from, to) -> from != to.id }
        if (real.isEmpty()) return
        Settings(context).editWidgetNotes { widgets -> real.fold(widgets) { acc, (from, to) -> followNote(acc, from, to) } }
        refreshSoon(context)
    }

    /** Points widgets that showed notes in the notebook [old] at them under its new name [new]. */
    suspend fun notebookRenamed(context: Context, old: String, new: String) {
        Settings(context).editWidgetNotes { followNotebook(it, old, new) }
        refreshSoon(context)
    }

    /** Opens the note [noteId] in the app. */
    fun openNoteIntent(context: Context, noteId: String): Intent = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_NOTE)
        // A different address for each note keeps their taps apart.
        .setData(Uri.fromParts("note", noteId, null))
        .putExtra(ReminderAlarms.EXTRA_NOTE_ID, noteId)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Opens the app on its main screen. */
    fun openAppIntent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Makes a new note, or with [list] a new list, and opens it. */
    fun newNoteIntent(context: Context, list: Boolean): Intent = Intent(context, MainActivity::class.java)
        .setAction(if (list) MainActivity.ACTION_NEW_LIST else MainActivity.ACTION_NEW_NOTE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    /** Opens the screen that picks the note for the widget [widgetId]. */
    fun setupIntent(context: Context, widgetId: Int): Intent = Intent(context, WidgetSetupActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        .setData(Uri.fromParts("widget", widgetId.toString(), null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private const val REFRESH_DELAY_MS = 1_000L
}

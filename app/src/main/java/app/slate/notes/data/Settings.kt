package app.slate.notes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How the note lists are ordered. */
enum class SortOrder {
    /** Most recently changed first. */
    Modified,

    /** A to Z by title. */
    Title,
}

/**
 * One settings file for the whole app. DataStore must only have one instance per file, and the
 * view model can be recreated, so the instance lives here.
 */
private object SettingsFile {
    @Volatile private var store: DataStore<Preferences>? = null

    fun get(context: Context): DataStore<Preferences> = store ?: synchronized(this) {
        store ?: PreferenceDataStoreFactory.create {
            context.applicationContext.preferencesDataStoreFile("settings")
        }.also { store = it }
    }
}

/** The app's settings, kept on the device (the notes themselves are only ever in the folder). */
class Settings(context: Context) {
    private val store = SettingsFile.get(context)

    /** The notes folder, as the tree address the system folder picker gave; null until one is picked. */
    val folder: Flow<String?> = store.data.map { it[FOLDER] }

    /** Remembers the notes folder (or forgets it, with null). */
    suspend fun setFolder(uri: String?) {
        store.edit { if (uri == null) it.remove(FOLDER) else it[FOLDER] = uri }
    }

    /** How lists are sorted. */
    val sortOrder: Flow<SortOrder> = store.data.map { prefs ->
        SortOrder.entries.firstOrNull { it.name == prefs[SORT] } ?: SortOrder.Modified
    }

    /** Changes how lists are sorted. */
    suspend fun setSortOrder(order: SortOrder) {
        store.edit { it[SORT] = order.name }
    }

    /** Notes open formatted (headings, lists, checkboxes) rather than as plain text to edit. */
    val openFormatted: Flow<Boolean> = store.data.map { it[OPEN_FORMATTED] ?: false }

    /** Changes whether notes open formatted. */
    suspend fun setOpenFormatted(on: Boolean) {
        store.edit { it[OPEN_FORMATTED] = on }
    }

    /** Lines in the editor use a fixed-width font, as in a code editor. */
    val monospace: Flow<Boolean> = store.data.map { it[MONOSPACE] ?: false }

    /** Changes the editor font. */
    suspend fun setMonospace(on: Boolean) {
        store.edit { it[MONOSPACE] = on }
    }

    /** Whether the welcome note has already been offered on this phone, so it's only ever made once. */
    val welcomed: Flow<Boolean> = store.data.map { it[WELCOMED] ?: false }

    /** Records that the welcome note was offered. */
    suspend fun setWelcomed() {
        store.edit { it[WELCOMED] = true }
    }

    /** Notes show as a two-column grid of cards rather than one card per row. */
    val grid: Flow<Boolean> = store.data.map { it[GRID] ?: true }

    /** Changes between grid and list. */
    suspend fun setGrid(on: Boolean) {
        store.edit { it[GRID] = on }
    }

    /** Ticked checklist items move below the others, as in Google Keep. */
    val tickedToBottom: Flow<Boolean> = store.data.map { it[TICKED_TO_BOTTOM] ?: true }

    /** Changes whether ticked items move to the bottom. */
    suspend fun setTickedToBottom(on: Boolean) {
        store.edit { it[TICKED_TO_BOTTOM] = on }
    }

    /**
     * Notes this phone moved to the trash: where each came from (to put it back) and when (to
     * empty it after a week). Notes trashed elsewhere aren't in it; they go back to the top
     * folder and stay until the trash is emptied.
     */
    val trashLog: Flow<List<TrashEntry>> = store.data.map { prefs -> prefs[TRASH_LOG].orEmpty().mapNotNull(::decodeTrash) }

    /** Changes the trash log. */
    suspend fun editTrashLog(change: (List<TrashEntry>) -> List<TrashEntry>) {
        store.edit { prefs ->
            prefs[TRASH_LOG] = change(prefs[TRASH_LOG].orEmpty().mapNotNull(::decodeTrash)).map(::encodeTrash).toSet()
        }
    }

    /** The reminder alarms set on this phone, so they can be set again after a restart and cancelled when removed. */
    val scheduledReminders: Flow<List<ScheduledReminder>> =
        store.data.map { prefs -> prefs[REMINDERS].orEmpty().mapNotNull(::decodeReminder) }

    /** Records which reminder alarms are set. */
    suspend fun setScheduledReminders(reminders: List<ScheduledReminder>) {
        store.edit { it[REMINDERS] = reminders.map(::encodeReminder).toSet() }
    }

    private companion object {
        val FOLDER = stringPreferencesKey("folder")
        val SORT = stringPreferencesKey("sort")
        val OPEN_FORMATTED = booleanPreferencesKey("open_formatted")
        val MONOSPACE = booleanPreferencesKey("monospace")
        val WELCOMED = booleanPreferencesKey("welcomed")
        val GRID = booleanPreferencesKey("grid")
        val TICKED_TO_BOTTOM = booleanPreferencesKey("ticked_to_bottom")
        val TRASH_LOG = stringSetPreferencesKey("trash_log")
        val REMINDERS = stringSetPreferencesKey("reminders")
    }
}

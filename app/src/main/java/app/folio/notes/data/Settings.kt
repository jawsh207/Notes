package app.folio.notes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How the note lists are ordered. */
enum class SortOrder {
    /** Most recently changed first, in Today / Yesterday / … groups. */
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

    private companion object {
        val FOLDER = stringPreferencesKey("folder")
        val SORT = stringPreferencesKey("sort")
        val OPEN_FORMATTED = booleanPreferencesKey("open_formatted")
        val MONOSPACE = booleanPreferencesKey("monospace")
    }
}

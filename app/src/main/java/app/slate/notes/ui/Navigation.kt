package app.slate.notes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** The app's screens. Home holds the bottom tabs; the others cover them. */
sealed interface Screen {
    /** The Notes and Browse tabs. */
    data object Home : Screen

    /** Finding notes by their words. */
    data object Search : Screen

    /** One note, open to read or edit. [id] is its document id. */
    data class Note(val id: String) : Screen

    /** One notebook's notes, by the notebook's name. */
    data class Notebook(val name: String) : Screen

    /** The notes with one label. */
    data class Label(val name: String) : Screen

    /** Notes with a reminder, soonest first. */
    data object Reminders : Screen

    /** Archived notes. */
    data object Archive : Screen

    /** Notes in the trash. */
    data object Trash : Screen

    /** Settings. */
    data object Settings : Screen
}

private fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.Search -> "search"
    is Screen.Note -> "note:$id"
    is Screen.Notebook -> "notebook:$name"
    is Screen.Label -> "label:$name"
    Screen.Reminders -> "reminders"
    Screen.Archive -> "archive"
    Screen.Trash -> "trash"
    Screen.Settings -> "settings"
}

private fun decode(text: String): Screen? = when {
    text == "home" -> Screen.Home
    text == "search" -> Screen.Search
    text == "settings" -> Screen.Settings
    text == "reminders" -> Screen.Reminders
    text == "archive" -> Screen.Archive
    text == "trash" -> Screen.Trash
    text.startsWith("note:") -> Screen.Note(text.removePrefix("note:"))
    text.startsWith("notebook:") -> Screen.Notebook(text.removePrefix("notebook:"))
    text.startsWith("label:") -> Screen.Label(text.removePrefix("label:"))
    else -> null
}

/** The back stack, kept across rotation and process death. Always starts with Home. */
@Composable
fun rememberBackStack(): SnapshotStateList<Screen> = rememberSaveable(
    saver = listSaver(
        save = { stack -> stack.map { it.encode() } },
        restore = { saved -> saved.mapNotNull(::decode).ifEmpty { listOf(Screen.Home) }.toMutableStateList() },
    ),
) { listOf<Screen>(Screen.Home).toMutableStateList() }

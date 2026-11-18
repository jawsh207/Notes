package app.slate.notes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/** The app's screens. Home holds the bottom tabs; the others cover them. */
sealed interface Screen {
    /** The Notes and Folders tabs. */
    data object Home : Screen

    /** Finding notes by their words. */
    data object Search : Screen

    /** One note, open to read or edit. [id] is its document id. */
    data class Note(val id: String) : Screen

    /** What's in one folder; [path] is relative to the notes folder. */
    data class Folder(val path: String) : Screen

    /** Settings. */
    data object Settings : Screen
}

private fun Screen.encode(): String = when (this) {
    Screen.Home -> "home"
    Screen.Search -> "search"
    is Screen.Note -> "note:$id"
    is Screen.Folder -> "folder:$path"
    Screen.Settings -> "settings"
}

private fun decode(text: String): Screen? = when {
    text == "home" -> Screen.Home
    text == "search" -> Screen.Search
    text == "settings" -> Screen.Settings
    text.startsWith("note:") -> Screen.Note(text.removePrefix("note:"))
    text.startsWith("folder:") -> Screen.Folder(text.removePrefix("folder:"))
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

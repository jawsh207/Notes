package app.slate.notes.ui.notes

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.data.Note
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.components.ColorSheet
import app.slate.notes.ui.components.LabelDialog
import app.slate.notes.ui.components.ReminderDialog
import app.slate.notes.data.sortFolders
import app.slate.notes.ui.notebooks.MoveToNotebookSheet
import app.slate.notes.ui.notebooks.NotebookDialog
import app.slate.notes.util.shareNote

/**
 * Replaces the top bar while notes are picked, as in Google Keep: how many, and pin, colour,
 * label, archive and delete for all of them, with select all, move to a notebook, reminder,
 * copy and share in the menu. [visible] are the notes on the screen, for Select all.
 */
@Composable
fun SelectedNotesBar(viewModel: NotesViewModel, selection: Selection, visible: List<Note>, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val infos by viewModel.infos.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val picked = visible.filter { it.id in selection.ids }
    val metas = picked.mapNotNull { infos[it.id]?.meta }
    val allPinned = metas.isNotEmpty() && metas.all { it.pinned }
    val allArchived = metas.isNotEmpty() && metas.all { it.archived }
    var menu by remember { mutableStateOf(false) }
    var coloring by remember { mutableStateOf(false) }
    var labeling by remember { mutableStateOf(false) }
    var reminding by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val library by viewModel.library.collectAsStateWithLifecycle()
    val colors by viewModel.notebookColors.collectAsStateWithLifecycle()

    // As tall as the search bar with its padding, so nothing shifts.
    Row(Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = selection::clear) { Icon(Icons.Rounded.Close, stringResource(R.string.clear_selection)) }
        AnimatedContent(targetState = picked.size, label = "count", modifier = Modifier.weight(1f).padding(start = 8.dp)) { count ->
            Text(pluralStringResource(R.plurals.selected_count, count, count), style = MaterialTheme.typography.titleLarge)
        }
        IconButton(onClick = { viewModel.setPinned(picked, !allPinned) }) {
            Icon(if (allPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, stringResource(if (allPinned) R.string.unpin else R.string.pin))
        }
        IconButton(onClick = { coloring = true }) { Icon(Icons.Outlined.Palette, stringResource(R.string.color)) }
        IconButton(onClick = {
            viewModel.setArchived(picked, !allArchived)
            selection.clear()
        }) {
            Icon(if (allArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, stringResource(if (allArchived) R.string.unarchive else R.string.archive))
        }
        IconButton(onClick = {
            viewModel.trash(picked)
            selection.clear()
        }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.select_all)) },
                    leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                    onClick = { menu = false; selection.selectAll(visible.map { it.id }) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_to_notebook)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                    onClick = { menu = false; moving = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.labels)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                    onClick = { menu = false; labeling = true },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remind_me)) },
                    leadingIcon = { Icon(Icons.Outlined.AddAlert, null) },
                    onClick = { menu = false; reminding = true },
                )
                val single = picked.singleOrNull()
                if (single != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.make_copy)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                        onClick = {
                            menu = false
                            selection.clear()
                            viewModel.copy(single, onOpen)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        leadingIcon = { Icon(Icons.Outlined.Share, null) },
                        onClick = { menu = false; shareNote(context, single.title, infos[single.id]?.body.orEmpty()) },
                    )
                }
            }
        }
    }

    if (coloring) {
        val colors = metas.map { it.color }.distinct()
        ColorSheet(
            current = colors.singleOrNull(),
            onPick = { color ->
                coloring = false
                viewModel.updateMeta(picked) { it.copy(color = color) }
            },
            onDismiss = { coloring = false },
        )
    }
    if (labeling) {
        LabelDialog(
            labels = labels,
            state = { label ->
                val having = metas.count { meta -> meta.tags.any { it.equals(label, ignoreCase = true) } }
                when (having) {
                    0 -> ToggleableState.Off
                    metas.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }
            },
            onToggle = { label, add -> viewModel.setLabel(picked, label, add) },
            onDismiss = { labeling = false },
        )
    }
    if (moving) {
        MoveToNotebookSheet(
            notebooks = sortFolders(library?.notebooks.orEmpty()),
            colors = colors,
            current = picked.map { it.notebook }.distinct().singleOrNull(),
            onPick = { notebook ->
                moving = false
                viewModel.moveToNotebook(picked, notebook)
                selection.clear()
            },
            onNew = {
                moving = false
                creating = true
            },
            onDismiss = { moving = false },
        )
    }
    if (creating) {
        NotebookDialog(
            title = stringResource(R.string.new_notebook),
            confirmLabel = stringResource(R.string.create),
            initialName = "",
            initialColor = null,
            onConfirm = { name, color ->
                viewModel.createNotebook(name, color) { made -> viewModel.moveToNotebook(picked, made) }.also { made ->
                    if (made) {
                        creating = false
                        selection.clear()
                    }
                }
            },
            onDismiss = { creating = false },
        )
    }
    if (reminding) {
        ReminderDialog(
            current = metas.mapNotNull { it.reminder }.distinct().singleOrNull(),
            onSet = { time ->
                reminding = false
                viewModel.updateMeta(picked) { it.copy(reminder = time) }
            },
            onDismiss = { reminding = false },
        )
    }
}

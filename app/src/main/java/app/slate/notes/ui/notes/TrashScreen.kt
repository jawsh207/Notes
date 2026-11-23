package app.slate.notes.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.data.Note
import app.slate.notes.data.TRASH_DAYS
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.components.BackTopBar
import app.slate.notes.ui.components.EmptyState

/**
 * The trash: notes deleted in the app, waiting in the hidden ".trash" folder. Tapping one offers
 * to put it back or delete it for good; picking several does the same for all; Empty trash
 * deletes them all. Notes trashed on this phone are deleted for good after a week.
 */
@Composable
fun TrashScreen(viewModel: NotesViewModel, onBack: () -> Unit) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val infos by viewModel.infos.collectAsStateWithLifecycle()
    val order by viewModel.sortOrder.collectAsStateWithLifecycle()
    val inGrid by viewModel.grid.collectAsStateWithLifecycle()
    val selection = rememberSelection()
    val grid = rememberLazyStaggeredGridState()
    val notes = library?.trash.orEmpty()
    LaunchedEffect(notes) { selection.retain(notes.mapTo(HashSet()) { it.id }) }
    BackHandler(enabled = selection.active) { selection.clear() }
    var asking by remember { mutableStateOf<Note?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<List<Note>?>(null) }

    Scaffold(
        topBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                AnimatedContent(targetState = selection.active, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "bar") { picking ->
                    if (picking) {
                        val picked = notes.filter { it.id in selection.ids }
                        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = selection::clear) { Icon(Icons.Rounded.Close, stringResource(R.string.clear_selection)) }
                            Text(
                                pluralStringResource(R.plurals.selected_count, picked.size, picked.size),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                            )
                            IconButton(onClick = { viewModel.restore(picked); selection.clear() }) {
                                Icon(Icons.Outlined.RestoreFromTrash, stringResource(R.string.restore))
                            }
                            IconButton(onClick = { confirmDelete = picked }) {
                                Icon(Icons.Outlined.DeleteForever, stringResource(R.string.delete_forever))
                            }
                        }
                    } else {
                        BackTopBar(stringResource(R.string.trash), onBack, insetStatusBar = false, actions = {
                            if (notes.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.empty_trash)) }
                        })
                    }
                }
            }
        },
    ) { padding ->
        if (notes.isEmpty()) {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    EmptyState(
                        Icons.Outlined.DeleteOutline,
                        stringResource(R.string.trash_empty_title),
                        stringResource(R.string.trash_empty_body, TRASH_DAYS.toInt()),
                        Modifier.padding(top = 48.dp),
                    )
                }
            }
        } else {
            NoteGrid(
                state = grid,
                inGrid = inGrid,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 48.dp),
            ) {
                item(key = "hint", span = StaggeredGridItemSpan.FullLine) {
                    Text(
                        stringResource(R.string.trash_hint, TRASH_DAYS.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                noteCards(
                    notes, infos, viewModel.store(), order, inGrid, showFolder = false, selection = selection,
                    onOpen = { asking = it }, keyPrefix = "trash", pinSections = false,
                )
            }
        }
    }

    asking?.let { note ->
        AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text(note.title) },
            text = { Text(stringResource(R.string.trash_note_body)) },
            confirmButton = {
                TextButton(onClick = { asking = null; viewModel.restore(listOf(note)) }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = { asking = null; confirmDelete = listOf(note) }) { Text(stringResource(R.string.delete_forever)) }
            },
        )
    }
    confirmDelete?.let { doomed ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(pluralStringResource(R.plurals.delete_forever_title, doomed.size, doomed.size)) },
            text = { Text(stringResource(R.string.delete_forever_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    selection.clear()
                    viewModel.deleteForever(doomed)
                }) { Text(stringResource(R.string.delete_forever)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.empty_trash_title)) },
            text = { Text(stringResource(R.string.delete_forever_body)) },
            confirmButton = {
                TextButton(onClick = { confirmEmpty = false; viewModel.emptyTrash() }) { Text(stringResource(R.string.empty_trash)) }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

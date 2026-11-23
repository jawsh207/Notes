package app.slate.notes.ui.notebooks

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.components.BackTopBar
import app.slate.notes.ui.components.EmptyState
import app.slate.notes.ui.components.NewNoteFab
import app.slate.notes.ui.notes.NoteGrid
import app.slate.notes.ui.notes.SelectedNotesBar
import app.slate.notes.ui.notes.noteCards
import app.slate.notes.ui.notes.rememberSelection

/**
 * One notebook's page: its notes as cards, the + menu to write a note or a
 * checklist in it, and a menu to rename it, change its colour, or move it to the trash.
 * [onRenamed] is told the new name, so the back stack follows it.
 */
@Composable
fun NotebookScreen(
    viewModel: NotesViewModel,
    name: String,
    onOpen: (String) -> Unit,
    onNew: (list: Boolean) -> Unit,
    onRenamed: (String) -> Unit,
    onBack: () -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val infos by viewModel.infos.collectAsStateWithLifecycle()
    val colors by viewModel.notebookColors.collectAsStateWithLifecycle()
    val order by viewModel.sortOrder.collectAsStateWithLifecycle()
    val inGrid by viewModel.grid.collectAsStateWithLifecycle()
    val grid = rememberLazyStaggeredGridState()
    val selection = rememberSelection()
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val notes = library?.notes.orEmpty().filter { it.notebook.equals(name, ignoreCase = true) }
    LaunchedEffect(notes) { selection.retain(notes.mapTo(HashSet()) { it.id }) }
    BackHandler(enabled = selection.active) { selection.clear() }

    Scaffold(
        topBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                AnimatedContent(targetState = selection.active, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "bar") { picking ->
                    if (picking) {
                        SelectedNotesBar(viewModel, selection, notes, onOpen)
                    } else {
                        BackTopBar(name, onBack, insetStatusBar = false, actions = {
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.edit_notebook)) },
                                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                        onClick = { menu = false; editing = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete_notebook)) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = { menu = false; deleting = true },
                                    )
                                }
                            }
                        })
                    }
                }
            }
        },
        floatingActionButton = { if (!selection.active) NewNoteFab(onNote = { onNew(false) }, onList = { onNew(true) }) },
    ) { padding ->
        NoteGrid(
            state = grid,
            inGrid = inGrid,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 96.dp),
        ) {
            if (notes.isEmpty() && library != null) {
                item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.MenuBook,
                        title = stringResource(R.string.empty_notebook_title),
                        body = stringResource(R.string.empty_notebook_body),
                        modifier = Modifier.padding(top = 48.dp).animateItem(),
                    )
                }
            }
            noteCards(notes, infos, viewModel.store(), order, inGrid, showFolder = false, selection = selection, onOpen = { onOpen(it.id) }, keyPrefix = "in:$name")
        }
    }
    if (editing) {
        NotebookDialog(
            title = stringResource(R.string.edit_notebook),
            confirmLabel = stringResource(R.string.save),
            initialName = name,
            initialColor = colors[name],
            onConfirm = { newName, color ->
                val renamed = viewModel.renameNotebook(name, newName, color)
                if (renamed) {
                    val clean = app.slate.notes.data.cleanNotebookName(newName) ?: name
                    editing = false
                    if (clean != name) onRenamed(clean)
                }
                renamed
            },
            onDismiss = { editing = false },
        )
    }
    if (deleting) {
        DeleteNotebookDialog(
            name = name,
            count = library?.notes.orEmpty().count { it.notebook.equals(name, ignoreCase = true) },
            onConfirm = {
                deleting = false
                viewModel.deleteNotebook(name)
                onBack()
            },
            onDismiss = { deleting = false },
        )
    }
}

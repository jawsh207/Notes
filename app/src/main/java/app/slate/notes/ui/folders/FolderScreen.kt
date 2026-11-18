package app.slate.notes.ui.folders

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
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import app.slate.notes.data.ATTACHMENTS
import app.slate.notes.data.sortFolders
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.browse.BrowseRow
import app.slate.notes.ui.components.BackTopBar
import app.slate.notes.ui.components.EmptyState
import app.slate.notes.ui.components.NewNoteFab
import app.slate.notes.ui.components.SectionHeader
import app.slate.notes.ui.components.TextInputDialog
import app.slate.notes.ui.notes.NoteGrid
import app.slate.notes.ui.notes.SelectedNotesBar
import app.slate.notes.ui.notes.noteCards
import app.slate.notes.ui.notes.rememberSelection

/**
 * One folder's page: its sub-folders, then the notes directly in it (not archived) as cards,
 * with the + menu to write a note or a checklist here and a button to make a sub-folder.
 */
@Composable
fun FolderScreen(
    viewModel: NotesViewModel,
    path: String,
    onOpen: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onNew: (list: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val infos by viewModel.infos.collectAsStateWithLifecycle()
    val order by viewModel.sortOrder.collectAsStateWithLifecycle()
    val inGrid by viewModel.grid.collectAsStateWithLifecycle()
    val grid = rememberLazyStaggeredGridState()
    val selection = rememberSelection()
    var naming by remember { mutableStateOf(false) }
    val folders = sortFolders(library?.folders.orEmpty().filter { it.parentPath == path && !it.name.equals(ATTACHMENTS, ignoreCase = true) })
    val notes = library?.notes.orEmpty().filter { it.folder == path && infos[it.id]?.meta?.archived != true }
    LaunchedEffect(notes) { selection.retain(notes.mapTo(HashSet()) { it.id }) }
    BackHandler(enabled = selection.active) { selection.clear() }

    Scaffold(
        topBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                AnimatedContent(targetState = selection.active, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "bar") { picking ->
                    if (picking) {
                        SelectedNotesBar(viewModel, selection, notes, onOpen)
                    } else {
                        BackTopBar(path.substringAfterLast('/'), onBack, insetStatusBar = false, actions = {
                            IconButton(onClick = { naming = true }) { Icon(Icons.Outlined.CreateNewFolder, stringResource(R.string.new_folder)) }
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
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 96.dp),
        ) {
            if (folders.isEmpty() && notes.isEmpty() && library != null) {
                item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                    EmptyState(
                        icon = Icons.Outlined.FolderOpen,
                        title = stringResource(R.string.empty_folder_title),
                        body = stringResource(R.string.empty_folder_body),
                        modifier = Modifier.padding(top = 48.dp).animateItem(),
                    )
                }
            }
            if (folders.isNotEmpty()) {
                item(key = "folders", span = StaggeredGridItemSpan.FullLine) { SectionHeader(stringResource(R.string.folders), Modifier.animateItem()) }
                itemsIndexed(folders, key = { _, folder -> "folder:${folder.id}" }, span = { _, _ -> StaggeredGridItemSpan.FullLine }) { index, folder ->
                    val count = library?.notes.orEmpty().count { it.folder == folder.path || it.folder.startsWith(folder.path + "/") }
                    BrowseRow(Icons.Outlined.Folder, folder.name, count, index, folders.size, Modifier.animateItem()) {
                        onOpenFolder(folder.path)
                    }
                }
                if (notes.isNotEmpty()) item(key = "notes", span = StaggeredGridItemSpan.FullLine) { SectionHeader(stringResource(R.string.notes_here), Modifier.animateItem()) }
            }
            noteCards(notes, infos, viewModel.store(), order, inGrid, showFolder = false, selection = selection, onOpen = { onOpen(it.id) }, keyPrefix = "in:$path")
        }
    }
    if (naming) {
        TextInputDialog(
            title = stringResource(R.string.new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            fieldLabel = stringResource(R.string.folder_name),
            onConfirm = { name -> naming = false; viewModel.createFolder(path, name) },
            onDismiss = { naming = false },
        )
    }
}

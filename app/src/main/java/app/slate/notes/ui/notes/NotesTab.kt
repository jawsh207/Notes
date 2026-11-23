package app.slate.notes.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.data.sortFolders
import app.slate.notes.ui.components.EmptyState
import app.slate.notes.ui.notebooks.NotebookChips

/**
 * The Notes tab: every note that isn't archived, as cards with the pinned ones first. A row of
 * notebook chips across the top shows one notebook at a time ([notebook], null for all of
 * them) and makes new ones. Pull down to look at the folder again straight away.
 */
@Composable
fun NotesTab(
    viewModel: NotesViewModel,
    contentPadding: PaddingValues,
    gridState: LazyStaggeredGridState,
    selection: Selection,
    notebook: String?,
    onNotebookChange: (String?) -> Unit,
    onNewNotebook: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val infos by viewModel.infos.collectAsStateWithLifecycle()
    val colors by viewModel.notebookColors.collectAsStateWithLifecycle()
    val order by viewModel.sortOrder.collectAsStateWithLifecycle()
    val inGrid by viewModel.grid.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val current = library
    // A notebook that's gone (deleted or renamed elsewhere) stops being shown.
    LaunchedEffect(notebook, current) {
        if (notebook != null && current != null && current.notebook(notebook) == null) onNotebookChange(null)
    }
    val notes = current?.notes.orEmpty().filter { note ->
        infos[note.id]?.meta?.archived != true && (notebook == null || note.notebook.equals(notebook, ignoreCase = true))
    }
    LaunchedEffect(notes) { selection.retain(notes.mapTo(HashSet()) { it.id }) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
    ) {
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(top = 96.dp), contentAlignment = Alignment.TopCenter) { LoadingIndicator() }
        } else {
            NoteGrid(
                state = gridState,
                inGrid = inGrid,
                // Room under the last card for the + button.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
            ) {
                item(key = "notebooks", span = StaggeredGridItemSpan.FullLine, contentType = "chips") {
                    NotebookChips(
                        notebooks = sortFolders(current.notebooks),
                        colors = colors,
                        selected = notebook,
                        onSelect = onNotebookChange,
                        onNew = onNewNotebook,
                    )
                }
                if (notes.isEmpty()) {
                    item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                        EmptyState(
                            icon = Icons.Outlined.Lightbulb,
                            title = stringResource(if (notebook == null) R.string.no_notes_title else R.string.empty_notebook_title),
                            body = if (notebook == null) stringResource(R.string.no_notes_body, current.rootName) else stringResource(R.string.empty_notebook_body),
                            modifier = Modifier.padding(top = 48.dp).animateItem(),
                        )
                    }
                }
                noteCards(
                    notes, infos, viewModel.store(), order, inGrid, showFolder = notebook == null, selection = selection,
                    onOpen = { onOpen(it.id) }, keyPrefix = "all",
                )
            }
        }
    }
}

package app.slate.notes.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import app.slate.notes.ui.components.EmptyState

/**
 * The Notes tab: every note that isn't archived, from every folder, as cards with the pinned
 * ones first. Pull down to look at the folder again straight away.
 */
@Composable
fun NotesTab(
    viewModel: NotesViewModel,
    contentPadding: PaddingValues,
    gridState: LazyStaggeredGridState,
    selection: Selection,
    onOpen: (String) -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val infos by viewModel.infos.collectAsStateWithLifecycle()
    val order by viewModel.sortOrder.collectAsStateWithLifecycle()
    val inGrid by viewModel.grid.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val current = library
    val notes = current?.notes.orEmpty().filter { infos[it.id]?.meta?.archived != true }
    LaunchedEffect(notes) { selection.retain(notes.mapTo(HashSet()) { it.id }) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
    ) {
        when {
            current == null -> Box(Modifier.fillMaxSize().padding(top = 96.dp), contentAlignment = Alignment.TopCenter) { LoadingIndicator() }
            notes.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Lightbulb,
                        title = stringResource(R.string.no_notes_title),
                        body = stringResource(R.string.no_notes_body, current.rootName),
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
            }
            else -> NoteGrid(
                state = gridState,
                inGrid = inGrid,
                // Room under the last card for the + button.
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
            ) {
                noteCards(notes, infos, viewModel.store(), order, inGrid, showFolder = true, selection = selection, onOpen = { onOpen(it.id) }, keyPrefix = "all")
            }
        }
    }
}

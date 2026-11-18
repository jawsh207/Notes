package app.slate.notes.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.components.EmptyState
import app.slate.notes.ui.components.SEGMENT_GAP

/**
 * The Notes tab: every note in the folder and its sub-folders, newest first (or A–Z), each
 * showing which folder it's in. Pull down to look at the folder again straight away.
 */
@Composable
fun NotesTab(
    viewModel: NotesViewModel,
    contentPadding: PaddingValues,
    listState: LazyListState,
    actions: NoteActions,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val previews by viewModel.previews.collectAsStateWithLifecycle()
    val order by viewModel.sortOrder.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val current = library

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding()),
    ) {
        when {
            current == null -> Box(Modifier.fillMaxSize().padding(top = 96.dp), contentAlignment = Alignment.TopCenter) { LoadingIndicator() }
            current.notes.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.EditNote,
                        title = stringResource(R.string.no_notes_title),
                        body = stringResource(R.string.no_notes_body, current.rootName),
                        modifier = Modifier.padding(top = 48.dp),
                    )
                }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // Room under the last card for the + button.
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
            ) {
                noteItems(current.notes, previews, order, showFolder = true, actions = actions, keyPrefix = "all")
            }
        }
    }
}

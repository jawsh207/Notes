package app.slate.notes.ui.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.data.Folder
import app.slate.notes.data.SortOrder
import app.slate.notes.data.sortFolders
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.components.EmptyState
import app.slate.notes.ui.components.ListRow
import app.slate.notes.ui.components.SEGMENT_GAP
import app.slate.notes.ui.components.SectionHeader
import app.slate.notes.ui.components.segmentShape
import app.slate.notes.ui.notes.NoteActions
import app.slate.notes.ui.notes.noteItems

/**
 * What's directly inside the folder at [path] ("" for the notes folder itself): its sub-folders,
 * each with how many notes it holds, then its notes. Used by the Folders tab and folder pages.
 */
@Composable
fun FolderContents(
    viewModel: NotesViewModel,
    path: String,
    contentPadding: PaddingValues,
    listState: LazyListState,
    actions: NoteActions,
    onOpenFolder: (Folder) -> Unit,
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
        if (current == null) {
            Box(Modifier.fillMaxSize().padding(top = 96.dp), contentAlignment = Alignment.TopCenter) { LoadingIndicator() }
        } else {
            val folders = sortFolders(current.folders.filter { it.parentPath == path })
            val notes = current.notes.filter { it.folder == path }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
            ) {
                if (folders.isEmpty() && notes.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Outlined.FolderOpen,
                            title = stringResource(R.string.empty_folder_title),
                            body = stringResource(R.string.empty_folder_body),
                            modifier = Modifier.padding(top = 48.dp).animateItem(),
                        )
                    }
                }
                if (folders.isNotEmpty()) {
                    item(key = "folders-header") { SectionHeader(stringResource(R.string.folders), Modifier.animateItem()) }
                    itemsIndexed(folders, key = { _, folder -> "folder:${folder.id}" }) { index, folder ->
                        val count = current.notes.count { it.folder == folder.path || it.folder.startsWith(folder.path + "/") }
                        ListRow(
                            icon = Icons.Outlined.Folder,
                            title = folder.name,
                            summary = pluralStringResource(R.plurals.note_count, count, count),
                            shape = segmentShape(index, folders.size),
                            onClick = { onOpenFolder(folder) },
                            trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                        )
                    }
                    // Sorted by date, the date headings already set the notes apart from the folders.
                    if (notes.isNotEmpty() && order == SortOrder.Title) item(key = "notes-header") { SectionHeader(stringResource(R.string.notes_here), Modifier.animateItem()) }
                }
                noteItems(notes, previews, order, showFolder = false, actions = actions, keyPrefix = "in:$path")
            }
        }
    }
}

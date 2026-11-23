package app.slate.notes.ui.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.data.Library
import app.slate.notes.data.NoteInfo
import app.slate.notes.data.sortFolders
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.Screen
import app.slate.notes.ui.components.ListRow
import app.slate.notes.ui.components.SEGMENT_GAP
import app.slate.notes.ui.components.SectionHeader
import app.slate.notes.ui.components.segmentShape

/**
 * The Organize tab: the notebooks in their colours, each with how many notes it holds, and a
 * row to make one; then Reminders and the Trash. Only what works alongside Folio lives here,
 * plus reminders.
 */
@Composable
fun OrganizeTab(
    viewModel: NotesViewModel,
    contentPadding: PaddingValues,
    listState: LazyListState,
    open: (Screen) -> Unit,
    onNewNotebook: () -> Unit,
) {
    val colors by viewModel.notebookColors.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val infos by viewModel.infos.collectAsStateWithLifecycle()
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
            val notebooks = sortFolders(current.notebooks)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 0.dp, bottom = contentPadding.calculateBottomPadding() + 96.dp),
                verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
            ) {
                item(key = "notebooks") { SectionHeader(stringResource(R.string.notebooks), Modifier.animateItem(), start = 32.dp) }
                itemsIndexed(notebooks, key = { _, notebook -> "notebook:${notebook.id}" }) { index, notebook ->
                    val count = current.notes.count { it.notebook.equals(notebook.name, ignoreCase = true) }
                    BrowseRow(
                        Icons.AutoMirrored.Outlined.MenuBook, notebook.name, count, index, notebooks.size + 1, Modifier.animateItem(),
                        iconTint = colors[notebook.name]?.let { Color(it) },
                    ) { open(Screen.Notebook(notebook.name)) }
                }
                item(key = "new-notebook") {
                    ListRow(
                        icon = Icons.Outlined.Add,
                        title = stringResource(R.string.new_notebook),
                        shape = segmentShape(notebooks.size, notebooks.size + 1),
                        onClick = onNewNotebook,
                        modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                    )
                }
                val places = listOf(
                    Place(Icons.Outlined.Alarm, R.string.reminders, current.count(infos) { it?.meta?.reminder != null }, Screen.Reminders),
                    Place(Icons.Outlined.Delete, R.string.trash, current.trash.size, Screen.Trash),
                )
                item(key = "places") { Spacer(Modifier.height(16.dp)) }
                itemsIndexed(places, key = { _, place -> "place:${place.title}" }) { index, place ->
                    BrowseRow(place.icon, stringResource(place.title), place.count, index, places.size, Modifier.animateItem()) { open(place.screen) }
                }
            }
        }
    }
}

/** One of the fixed places under the notebooks: Reminders or the Trash. */
private class Place(val icon: ImageVector, val title: Int, val count: Int, val screen: Screen)

/** How many notes (not in the trash) [test] picks out. */
private fun Library.count(infos: Map<String, NoteInfo>, test: (NoteInfo?) -> Boolean): Int = notes.count { test(infos[it.id]) }

/**
 * A row in Organize: an icon (coloured with [iconTint] if given), a name, how many notes, and an
 * arrow. It keeps 16dp from the screen edges itself, unless [inset] is off because it sits in a
 * grid that already does.
 */
@Composable
fun BrowseRow(
    icon: ImageVector,
    title: String,
    count: Int,
    index: Int,
    size: Int,
    modifier: Modifier = Modifier,
    inset: Boolean = true,
    iconTint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    ListRow(
        icon = icon,
        title = title,
        summary = pluralStringResource(R.plurals.note_count, count, count),
        shape = segmentShape(index, size),
        onClick = onClick,
        trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = if (inset) modifier.padding(horizontal = 16.dp) else modifier,
        iconTint = iconTint,
    )
}

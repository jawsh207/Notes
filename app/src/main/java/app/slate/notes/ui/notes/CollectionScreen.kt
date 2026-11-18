package app.slate.notes.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.data.Note
import app.slate.notes.data.NoteInfo
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.components.BackTopBar
import app.slate.notes.ui.components.EmptyState

/**
 * A page of notes picked out by [filter]: a label's notes, the archive, or notes with
 * reminders. Same cards and picking as the Notes tab; [actions] adds buttons to its top bar
 * and [fab] a button at the bottom.
 */
@Composable
fun CollectionScreen(
    viewModel: NotesViewModel,
    title: String,
    filter: (Note, NoteInfo?) -> Boolean,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyBody: String,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
    pinSections: Boolean = true,
    sort: ((List<Note>) -> List<Note>)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    fab: @Composable () -> Unit = {},
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val infos by viewModel.infos.collectAsStateWithLifecycle()
    val order by viewModel.sortOrder.collectAsStateWithLifecycle()
    val inGrid by viewModel.grid.collectAsStateWithLifecycle()
    val selection = rememberSelection()
    val grid = rememberLazyStaggeredGridState()
    val notes = library?.notes.orEmpty().filter { filter(it, infos[it.id]) }.let { sort?.invoke(it) ?: it }
    LaunchedEffect(notes) { selection.retain(notes.mapTo(HashSet()) { it.id }) }
    BackHandler(enabled = selection.active) { selection.clear() }

    Scaffold(
        topBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                AnimatedContent(targetState = selection.active, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "bar") { picking ->
                    if (picking) SelectedNotesBar(viewModel, selection, notes, onOpen = onOpen)
                    else BackTopBar(title, onBack, actions = actions, insetStatusBar = false)
                }
            }
        },
        floatingActionButton = fab,
    ) { padding ->
        when {
            library == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                LoadingIndicator(Modifier.padding(top = 96.dp))
            }
            notes.isEmpty() -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item { EmptyState(emptyIcon, emptyTitle, emptyBody, Modifier.padding(top = 48.dp)) }
            }
            else -> NoteGrid(
                state = grid,
                inGrid = inGrid,
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
            ) {
                noteCards(
                    notes, infos, viewModel.store(), order, inGrid, showFolder = true, selection = selection,
                    onOpen = { onOpen(it.id) }, keyPrefix = "collection", pinSections = pinSections && sort == null, presorted = sort != null,
                )
            }
        }
    }
}

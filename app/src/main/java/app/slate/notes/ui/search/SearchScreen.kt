package app.slate.notes.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.data.searchNotes
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.components.EmptyState
import app.slate.notes.ui.components.NoteCard
import app.slate.notes.ui.notes.NoteGrid
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState

/**
 * Finding notes, archived ones too: every word typed must appear in the title, the text or a
 * label. Title matches come first; each card shows the bit of text around the match.
 */
@Composable
fun SearchScreen(viewModel: NotesViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val library by viewModel.library.collectAsStateWithLifecycle()
    val infos by viewModel.infos.collectAsStateWithLifecycle()
    val inGrid by viewModel.grid.collectAsStateWithLifecycle()
    // Labels are searched along with the text.
    val texts = remember(infos) { infos.mapValues { (_, info) -> info.body + "\n" + info.meta.tags.joinToString(" ") } }
    val hits = remember(query, library, texts) { searchNotes(library?.notes.orEmpty(), texts, query) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                            decorationBox = { field ->
                                Box {
                                    if (query.isEmpty()) {
                                        Text(
                                            stringResource(R.string.search_notes),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    field()
                                }
                            },
                        )
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) }
                        } else {
                            Spacer(Modifier.width(12.dp))
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (query.isNotBlank() && hits.isEmpty()) {
            LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(top = padding.calculateTopPadding())) {
                item(key = "none") {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = stringResource(R.string.no_results_title),
                        body = stringResource(R.string.no_results_body),
                        modifier = Modifier.padding(top = 32.dp),
                    )
                }
            }
        } else {
            NoteGrid(
                state = rememberLazyStaggeredGridState(),
                inGrid = inGrid,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 24.dp),
                modifier = Modifier.imePadding(),
            ) {
                items(hits.distinctBy { it.note.id }, key = { it.note.id }) { hit ->
                    NoteCard(
                        note = hit.note,
                        info = infos[hit.note.id],
                        store = viewModel.store(),
                        inGrid = inGrid,
                        showFolder = true,
                        selected = false,
                        onClick = { onOpen(hit.note.id) },
                        onLongClick = { onOpen(hit.note.id) },
                        excerpt = hit.excerpt,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

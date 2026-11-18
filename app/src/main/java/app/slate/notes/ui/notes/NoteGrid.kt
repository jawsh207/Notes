package app.slate.notes.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.slate.notes.R
import app.slate.notes.data.Note
import app.slate.notes.data.NoteInfo
import app.slate.notes.data.NoteStore
import app.slate.notes.data.SortOrder
import app.slate.notes.data.sortNotes
import app.slate.notes.ui.components.NoteCard
import app.slate.notes.ui.components.SectionHeader

/** Which notes are picked on a list screen, by id. A long press starts picking; tapping then adds or removes. */
@Stable
class Selection {
    /** The picked notes' ids. */
    var ids by mutableStateOf(emptySet<String>())
        private set

    /** Whether anything is picked. */
    val active: Boolean get() = ids.isNotEmpty()

    /** Picks [id], or unpicks it if it was picked. */
    fun toggle(id: String) {
        ids = if (id in ids) ids - id else ids + id
    }

    /** Picks all of [all]. */
    fun selectAll(all: Collection<String>) {
        ids = all.toSet()
    }

    /** Unpicks everything. */
    fun clear() {
        ids = emptySet()
    }

    /** Keeps only the ids still in [present], so notes deleted elsewhere drop out. */
    fun retain(present: Set<String>) {
        if (!present.containsAll(ids)) ids = ids intersect present
    }
}

/** A [Selection] that lasts as long as the screen. */
@Composable
fun rememberSelection(): Selection = remember { Selection() }

/**
 * The notes grid: two columns of cards that keep their own heights, as in Google Keep, or one
 * column in list view. [content] adds the cards and any headings or rows.
 */
@Composable
fun NoteGrid(
    state: LazyStaggeredGridState,
    inGrid: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: LazyStaggeredGridScope.() -> Unit,
) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(if (inGrid) 2 else 1),
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        content = content,
    )
}

/**
 * Adds [notes] as cards: pinned ones first under "Pinned", then the rest under "Others" (no
 * headings when nothing is pinned, or when [pinSections] is off). Cards slide into place when
 * the order changes. Tapping opens a note, or picks it while [selection] is active. The notes
 * are sorted by [order] unless they come [presorted].
 */
fun LazyStaggeredGridScope.noteCards(
    notes: List<Note>,
    infos: Map<String, NoteInfo>,
    store: NoteStore?,
    order: SortOrder,
    inGrid: Boolean,
    showFolder: Boolean,
    selection: Selection,
    onOpen: (Note) -> Unit,
    keyPrefix: String = "",
    pinSections: Boolean = true,
    presorted: Boolean = false,
) {
    val sorted = if (presorted) notes else sortNotes(notes, order)
    val pinned = if (pinSections) sorted.filter { infos[it.id]?.meta?.pinned == true } else emptyList()
    val others = if (pinned.isEmpty()) sorted else sorted.filter { infos[it.id]?.meta?.pinned != true }
    fun section(header: Int?, members: List<Note>) {
        if (members.isEmpty()) return
        if (header != null) {
            item(key = "$keyPrefix:header:$header", span = StaggeredGridItemSpan.FullLine, contentType = "header") {
                SectionHeader(stringResource(header), Modifier.animateItem())
            }
        }
        items(members, key = { "$keyPrefix:note:${it.id}" }, contentType = { "note" }) { note ->
            NoteCard(
                note = note,
                info = infos[note.id],
                store = store,
                inGrid = inGrid,
                showFolder = showFolder,
                selected = note.id in selection.ids,
                onClick = { if (selection.active) selection.toggle(note.id) else onOpen(note) },
                onLongClick = { selection.toggle(note.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
    section(if (pinned.isNotEmpty()) R.string.pinned else null, pinned)
    section(if (pinned.isNotEmpty()) R.string.others else null, others)
}

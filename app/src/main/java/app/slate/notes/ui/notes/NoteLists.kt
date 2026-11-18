package app.slate.notes.ui.notes

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.slate.notes.R
import app.slate.notes.data.DateGroup
import app.slate.notes.data.Note
import app.slate.notes.data.SortOrder
import app.slate.notes.data.dateGroupOf
import app.slate.notes.data.sortNotes
import app.slate.notes.ui.components.NoteCard
import app.slate.notes.ui.components.SectionHeader
import app.slate.notes.ui.components.segmentShape
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a note list needs to do when a note is tapped, shared or deleted. */
class NoteActions(
    val open: (Note) -> Unit,
    val share: (Note) -> Unit,
    val delete: (Note) -> Unit,
)

/**
 * Adds [notes] to a list as grouped cards: under Today / Yesterday / … headings when sorted by
 * date, or as one A–Z group. Cards slide into place when the order changes.
 */
fun LazyListScope.noteItems(
    notes: List<Note>,
    previews: Map<String, String>,
    order: SortOrder,
    showFolder: Boolean,
    actions: NoteActions,
    keyPrefix: String = "",
) {
    val sorted = sortNotes(notes, order)
    val groups: List<Pair<DateGroup?, List<Note>>> = when (order) {
        SortOrder.Title -> listOf(null to sorted)
        SortOrder.Modified -> {
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            sorted.groupBy { dateGroupOf(it.modified, today, zone) }.toList()
        }
    }
    for ((group, members) in groups) {
        if (group != null) {
            item(key = "$keyPrefix:group:$group", contentType = "header") {
                SectionHeader(dateGroupLabel(group), Modifier.animateItem())
            }
        }
        itemsIndexed(members, key = { _, note -> "$keyPrefix:note:${note.id}" }, contentType = { _, _ -> "note" }) { index, note ->
            NoteCard(
                note = note,
                preview = previews[note.id],
                shape = segmentShape(index, members.size),
                showFolder = showFolder,
                onClick = { actions.open(note) },
                onShare = { actions.share(note) },
                onDelete = { actions.delete(note) },
                modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
            )
        }
    }
}

/** The heading over a [DateGroup]. */
@Composable
fun dateGroupLabel(group: DateGroup): String = when (group) {
    DateGroup.Today -> stringResource(R.string.group_today)
    DateGroup.Yesterday -> stringResource(R.string.group_yesterday)
    DateGroup.ThisWeek -> stringResource(R.string.group_week)
    DateGroup.ThisMonth -> stringResource(R.string.group_month)
    is DateGroup.Month -> group.month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
}

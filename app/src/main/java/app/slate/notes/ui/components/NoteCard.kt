package app.slate.notes.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.slate.notes.R
import app.slate.notes.data.Note
import app.slate.notes.data.NoteInfo
import app.slate.notes.data.NoteStore
import app.slate.notes.ui.theme.noteSurface

/** How cards on the main lists are shaped. */
val CARD_SHAPE = RoundedCornerShape(20.dp)

/**
 * A note as a card, as in Google Keep: its picture, title, a few lines of text (or the first
 * checklist items), then its reminder, labels and folder as small chips, all on the note's
 * colour. Tapping opens it, or in selection mode picks it; a long press starts selecting.
 * While [selected], it's outlined and ticked. In the grid ([inGrid]) cards show more lines
 * of text, as they're narrower. The card gives a little under the finger.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteCard(
    note: Note,
    info: NoteInfo?,
    store: NoteStore?,
    inGrid: Boolean,
    showFolder: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    excerpt: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val meta = info?.meta
    val background = noteSurface(meta?.color ?: app.slate.notes.data.NoteColor.Default)
    val outline by animateDpAsState(if (selected) 3.dp else 0.dp, spring(Spring.DampingRatioMediumBouncy), label = "outline")
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .pressBounce(interaction)
                .fillMaxWidth()
                .clip(CARD_SHAPE)
                .background(background)
                .border(outline, if (selected) scheme.primary else Color.Transparent, CARD_SHAPE)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            info?.image?.let { image ->
                NoteImage(store, note.folder, image.path, maxSide = 720, aspect = if (inGrid) 4f / 3f else null)
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val list = info?.checklist
                when {
                    excerpt != null -> BodyText(excerpt, maxLines = 4)
                    list != null -> {
                        val open = list.filter { !it.checked && it.text.isNotBlank() }
                        val ticked = list.count { it.checked && it.text.isNotBlank() }
                        open.take(if (inGrid) 8 else 4).forEach { ChecklistLine(it.text, checked = false, depth = it.depth) }
                        if (open.isEmpty()) list.filter { it.checked }.take(3).forEach { ChecklistLine(it.text, checked = true, depth = it.depth) }
                        val hidden = (open.size - (if (inGrid) 8 else 4)).coerceAtLeast(0)
                        if (hidden > 0 || (ticked > 0 && open.isNotEmpty())) {
                            Text(
                                listOfNotNull(
                                    pluralStringResource(R.plurals.more_items, hidden, hidden).takeIf { hidden > 0 },
                                    pluralStringResource(R.plurals.ticked_items, ticked, ticked).takeIf { ticked > 0 && open.isNotEmpty() },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    !info?.preview.isNullOrBlank() -> BodyText(info!!.preview, maxLines = if (inGrid) 10 else 3)
                }
                val chips = mutableListOf<Pair<ImageVector?, String>>()
                meta?.reminder?.let { chips += Icons.Outlined.Alarm to reminderLabel(it) }
                meta?.tags?.take(3)?.forEach { chips += null to it }
                meta?.tags?.size?.takeIf { it > 3 }?.let { chips += null to "+${it - 3}" }
                if (showFolder && note.folder.isNotEmpty()) chips += Icons.Outlined.Folder to note.folder.substringAfterLast('/')
                if (chips.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        chips.forEach { (icon, label) -> Chip(icon, label) }
                    }
                }
            }
        }
        // The tick on a selected card springs in at its corner.
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Box(Modifier.size(24.dp).background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = scheme.onPrimary)
            }
        }
    }
}

/** A few lines of a note's text on its card. */
@Composable
private fun BodyText(text: String, maxLines: Int) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** One checklist item on a card: a box and its text, crossed out when ticked. */
@Composable
private fun ChecklistLine(text: String, checked: Boolean, depth: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.padding(start = (depth * 16).dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (checked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            null,
            Modifier.size(16.dp),
            tint = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (checked) scheme.onSurfaceVariant else scheme.onSurface,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small rounded label on a card or under a note: a reminder, a label or a folder. */
@Composable
fun Chip(icon: ImageVector?, label: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .clip(CircleShape)
            .background(scheme.onSurface.copy(alpha = 0.08f))
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

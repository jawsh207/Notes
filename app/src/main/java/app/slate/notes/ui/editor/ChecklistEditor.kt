package app.slate.notes.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.rounded.FormatIndentIncrease
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.slate.notes.R

/**
 * A checklist note's rows, edited as in Google Keep: a box and a line of text each. Enter starts
 * a new item below; backspace in an empty item removes it; the × appears on the item being
 * typed in, with buttons to indent it under the one above. With [tickedToBottom], ticked items
 * gather under a "ticked items" heading that folds away.
 */
@Composable
fun ChecklistEditor(session: NoteSession, tickedToBottom: Boolean, monospace: Boolean) {
    val focus = remember { HashMap<Long, FocusRequester>() }
    var focusKey by remember { mutableStateOf<Long?>(null) }
    var focused by remember { mutableStateOf<Long?>(null) }
    var tickedOpen by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(focusKey) {
        val key = focusKey ?: return@LaunchedEffect
        // The new row has to be laid out before it can take focus.
        kotlinx.coroutines.delay(16)
        runCatching { focus[key]?.requestFocus() }
        focusKey = null
    }
    val items = session.items.toList()
    val open = if (tickedToBottom) items.filter { !it.checked } else items
    val ticked = if (tickedToBottom) items.filter { it.checked } else emptyList()

    @Composable
    fun ItemFor(item: EditItem) {
        key(item.key) {
            ItemRow(
                item = item,
                requester = focus.getOrPut(item.key) { FocusRequester() },
                isFocused = focused == item.key,
                monospace = monospace,
                onFocus = { has -> if (has) focused = item.key else if (focused == item.key) focused = null },
                onChange = { value ->
                    // Enter splits the item: what's after the cursor starts a new one below.
                    val at = value.text.indexOf('\n')
                    if (at >= 0) {
                        session.editItem(item.key, TextFieldValue(value.text.substring(0, at)))
                        val next = session.addItem(after = item.key, tickedToBottom = tickedToBottom)
                        val rest = value.text.substring(at + 1).replace("\n", " ")
                        if (rest.isNotEmpty()) session.editItem(next, TextFieldValue(rest, TextRange(0)))
                        focusKey = next
                    } else {
                        session.editItem(item.key, value)
                    }
                },
                onToggle = { session.toggleItem(item.key) },
                onRemove = { focusKey = session.removeItem(item.key) },
                onIndent = { session.setDepth(item.key, if (item.depth == 0) 1 else 0) },
            )
        }
    }

    Column(Modifier.fillMaxWidth().animateContentSize(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))) {
        open.forEach { ItemFor(it) }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { focusKey = session.addItem(after = null, tickedToBottom = tickedToBottom) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.padding(horizontal = 12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.list_item), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ticked.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val turn by animateFloatAsState(if (tickedOpen) 0f else -90f, spring(Spring.DampingRatioMediumBouncy), label = "chevron")
            Row(
                Modifier.fillMaxWidth().clickable { tickedOpen = !tickedOpen }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.ExpandMore, null, Modifier.padding(horizontal = 12.dp).graphicsLayer { rotationZ = turn })
                Text(
                    pluralStringResource(R.plurals.ticked_items, ticked.size, ticked.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = tickedOpen,
                enter = expandVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column { ticked.forEach { ItemFor(it) } }
            }
        }
    }
}

/** One checklist row: indent, box, text, and while focused the indent and × buttons. */
@Composable
private fun ItemRow(
    item: EditItem,
    requester: FocusRequester,
    isFocused: Boolean,
    monospace: Boolean,
    onFocus: (Boolean) -> Unit,
    onChange: (TextFieldValue) -> Unit,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    onIndent: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = (item.depth * 28).dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.checked, onCheckedChange = { onToggle() })
        BasicTextField(
            value = item.text,
            onValueChange = onChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = if (item.checked) scheme.onSurfaceVariant else scheme.onSurface,
                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                fontFamily = if (monospace) FontFamily.Monospace else MaterialTheme.typography.bodyLarge.fontFamily,
            ),
            cursorBrush = SolidColor(scheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .focusRequester(requester)
                .onFocusChanged { onFocus(it.isFocused) }
                .onPreviewKeyEvent { event ->
                    // Backspace in an empty item removes it, as in Keep.
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Backspace && item.text.text.isEmpty()) {
                        onRemove()
                        true
                    } else {
                        false
                    }
                },
        )
        AnimatedVisibility(visible = isFocused, enter = fadeIn(), exit = fadeOut()) {
            Row {
                IconButton(onClick = onIndent, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (item.depth == 0) Icons.AutoMirrored.Rounded.FormatIndentIncrease else Icons.AutoMirrored.Rounded.FormatIndentDecrease,
                        stringResource(if (item.depth == 0) R.string.indent else R.string.outdent),
                    )
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Close, stringResource(R.string.remove_item)) }
            }
        }
        if (!isFocused) Spacer(Modifier.width(8.dp))
    }
}

package app.slate.notes.ui.notebooks

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.slate.notes.R
import app.slate.notes.data.Folder
import app.slate.notes.data.NOTEBOOK_COLORS
import app.slate.notes.data.cleanNotebookName

/** A small round dot in a notebook's colour (the theme's outline colour when it has none). */
@Composable
fun NotebookDot(color: Int?, size: Dp = 12.dp) {
    Box(Modifier.size(size).background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline, CircleShape))
}

/**
 * The row of notebook chips across the top of the Notes tab: All, then each notebook with its
 * colour, then a + chip to make one. [selected] is the notebook being shown (null for All).
 */
@Composable
fun NotebookChips(
    notebooks: List<Folder>,
    colors: Map<String, Int>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.all_notebooks)) },
            leadingIcon = if (selected == null) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
        )
        notebooks.forEach { notebook ->
            val isSelected = selected.equals(notebook.name, ignoreCase = true)
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else notebook.name) },
                label = { Text(notebook.name) },
                leadingIcon = { NotebookDot(colors[notebook.name]) },
            )
        }
        FilterChip(
            selected = false,
            onClick = onNew,
            label = { Text(stringResource(R.string.new_notebook)) },
            leadingIcon = { Icon(Icons.Outlined.Add, null, Modifier.size(FilterChipDefaults.IconSize)) },
        )
    }
}

/**
 * Making or renaming a notebook: its name and one of [NOTEBOOK_COLORS]. [onConfirm] returns
 * false when the name can't be used or another notebook has it, and the dialog says so.
 */
@Composable
fun NotebookDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialColor: Int?,
    onConfirm: (name: String, color: Int) -> Boolean,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor ?: NOTEBOOK_COLORS.first()) }
    var problem by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        problem = false
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.notebook_name)) },
                    isError = problem,
                    supportingText = if (problem) ({ Text(stringResource(R.string.notebook_name_problem)) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    NOTEBOOK_COLORS.forEach { option -> ColorOption(option, option == color) { color = option } }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cleanNotebookName(name) != null,
                onClick = { if (!onConfirm(name, color)) problem = true },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** One colour to pick for a notebook, ticked and a little bigger when chosen. */
@Composable
private fun ColorOption(color: Int, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1.15f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "color")
    Box(
        Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(Color(color))
            .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = Color.White)
    }
}

/**
 * Choosing a notebook to move notes to, as a sheet from the bottom: every notebook with its
 * colour (the [current] one ticked), and New notebook at the end.
 */
@Composable
fun MoveToNotebookSheet(
    notebooks: List<Folder>,
    colors: Map<String, Int>,
    current: String?,
    onPick: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.move_to_notebook),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.heightIn(max = 480.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            items(notebooks, key = { it.id }) { notebook ->
                SheetRow(onClick = { onPick(notebook.name) }) {
                    NotebookDot(colors[notebook.name], 16.dp)
                    Spacer(Modifier.width(20.dp))
                    Text(notebook.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (notebook.name.equals(current, ignoreCase = true)) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            item(key = "new") {
                SheetRow(onClick = onNew) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(20.dp))
                    Text(stringResource(R.string.new_notebook), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** A tappable row in a sheet. */
@Composable
private fun SheetRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Asking before a notebook goes to the trash with its [count] notes. */
@Composable
fun DeleteNotebookDialog(name: String, count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_notebook_title, name)) },
        text = { Text(pluralStringResource(R.plurals.delete_notebook_body, count, count)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

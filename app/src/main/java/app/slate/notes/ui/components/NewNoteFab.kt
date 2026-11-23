package app.slate.notes.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.slate.notes.R

/** An extra choice in the [NewNoteFab] menu, such as New folder. */
class FabChoice(val label: String, val icon: ImageVector, val onClick: () -> Unit)

/**
 * The + button, as Material 3 Expressive's floating menu: tapping it springs open a stack of
 * choices (a note, a checklist, and any [extra] ones) while the + turns into a ×. With
 * [listsOnly] it makes a checklist straight away instead.
 */
@Composable
fun NewNoteFab(onNote: () -> Unit, onList: () -> Unit, extra: List<FabChoice> = emptyList(), listsOnly: Boolean = false) {
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = open) { open = false }
    FloatingActionButtonMenu(
        expanded = open,
        // The menu pads itself 16dp at the side and bottom, on top of the 16dp the screen frame
        // already leaves; this takes it back, so the button sits where Contacts' and Twill's do.
        // offset follows right-to-left layouts, where the button sits on the left.
        modifier = Modifier.offset(x = 16.dp, y = 16.dp),
        button = {
            // On the Lists tab there's nothing to choose between: the button makes a list.
            ToggleFloatingActionButton(checked = open, onCheckedChange = { if (listsOnly) onList() else open = it }) {
                // A quarter turn as it opens: + becomes ×. The button doesn't colour its icon itself,
                // so the icon follows its container from primary container (closed) to primary (open).
                val scheme = MaterialTheme.colorScheme
                Icon(
                    Icons.Rounded.Add,
                    stringResource(if (open) R.string.close_menu else if (listsOnly) R.string.new_list else R.string.new_note),
                    Modifier.graphicsLayer { rotationZ = checkedProgress * 45f },
                    tint = lerp(scheme.onPrimaryContainer, scheme.onPrimary, checkedProgress),
                )
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { open = false; onList() },
            text = { Text(stringResource(R.string.new_list)) },
            icon = { Icon(Icons.Outlined.CheckBox, null) },
        )
        FloatingActionButtonMenuItem(
            onClick = { open = false; onNote() },
            text = { Text(stringResource(R.string.new_note)) },
            icon = { Icon(Icons.Outlined.EditNote, null) },
        )
        extra.forEach { choice ->
            FloatingActionButtonMenuItem(
                onClick = { open = false; choice.onClick() },
                text = { Text(choice.label) },
                icon = { Icon(choice.icon, null) },
            )
        }
    }
}

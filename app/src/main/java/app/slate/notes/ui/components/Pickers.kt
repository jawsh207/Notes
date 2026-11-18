package app.slate.notes.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.slate.notes.R
import app.slate.notes.data.NoteColor
import app.slate.notes.data.QuickReminder
import app.slate.notes.data.cleanLabel
import app.slate.notes.data.quickReminders
import app.slate.notes.ui.theme.noteSwatch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * The colour palette, as a sheet from the bottom: a row of round swatches, the current one
 * ticked. Picking one springs it up a little and closes the sheet.
 */
@Composable
fun ColorSheet(current: NoteColor?, onPick: (NoteColor) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.color),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp).navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NoteColor.entries.forEach { color -> Swatch(color, selected = color == current) { onPick(color) } }
        }
    }
}

/** One round colour swatch. "No colour" shows a crossed-out drop instead. */
@Composable
private fun Swatch(color: NoteColor, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fill = noteSwatch(color)
    val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "swatch")
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(fill ?: scheme.surfaceBright, CircleShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Rounded.Check, stringResource(R.string.color_selected), tint = scheme.onSurface)
            fill == null -> Icon(Icons.Outlined.FormatColorReset, stringResource(R.string.no_color), tint = scheme.onSurfaceVariant)
        }
    }
}

/**
 * Choosing labels for one or more notes: every label with a box (part-ticked when only some
 * of the notes have it), and a field to find one or make a new one. [state] says how a label
 * stands; [onToggle] adds it to all the notes or takes it off them.
 */
@Composable
fun LabelDialog(
    labels: List<String>,
    state: (String) -> ToggleableState,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val clean = cleanLabel(query)
    val shown = labels.filter { clean.isEmpty() || it.contains(clean, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
        title = { Text(stringResource(R.string.label_note)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.label_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                    if (clean.isNotEmpty() && labels.none { it.equals(clean, ignoreCase = true) }) {
                        item {
                            Row(
                                Modifier.fillMaxWidth().clickable { onToggle(clean, true); query = "" }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Add, null, Modifier.padding(horizontal = 12.dp))
                                Text(stringResource(R.string.create_label, clean))
                            }
                        }
                    }
                    items(shown, key = { it }) { label ->
                        val current = state(label)
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(label, current != ToggleableState.On) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TriStateCheckbox(state = current, onClick = { onToggle(label, current != ToggleableState.On) })
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}

/**
 * Setting a reminder: Keep's quick choices (later today, tomorrow morning, next week), or a day
 * and time picked on calendars and clocks, or removing it. Asks to show notifications the first
 * time a reminder is set, as Android 13 and later require.
 */
@Composable
fun ReminderDialog(current: LocalDateTime?, onSet: (LocalDateTime?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val set: (LocalDateTime?) -> Unit = { time ->
        if (time != null && android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onSet(time)
    }
    var step by remember { mutableStateOf(0) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    when (step) {
        0 -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Outlined.Alarm, null) },
            title = { Text(stringResource(R.string.remind_me)) },
            text = {
                Column {
                    quickReminders(LocalDateTime.now()).forEach { (kind, time) ->
                        ChoiceRow(
                            Icons.Outlined.Alarm,
                            stringResource(
                                when (kind) {
                                    QuickReminder.LaterToday -> R.string.later_today
                                    QuickReminder.Tomorrow -> R.string.tomorrow_morning
                                    QuickReminder.NextWeek -> R.string.next_week
                                },
                            ),
                            reminderLabel(time),
                        ) { set(time) }
                    }
                    ChoiceRow(Icons.Outlined.CalendarMonth, stringResource(R.string.pick_date_time), null) { step = 1 }
                    if (current != null) ChoiceRow(Icons.Outlined.AlarmOff, stringResource(R.string.remove_reminder), null) { set(null) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        1 -> {
            // The date picker works in UTC midnights.
            val initial = (current?.toLocalDate() ?: LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val dates = rememberDatePickerState(initialSelectedDateMillis = initial)
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = {
                        day = dates.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        if (day != null) step = 2
                    }) { Text(stringResource(R.string.next)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            ) { DatePicker(dates) }
        }
        else -> {
            val start = current?.toLocalTime() ?: LocalTime.of(8, 0)
            val clock = rememberTimePickerState(start.hour, start.minute, android.text.format.DateFormat.is24HourFormat(context))
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.pick_time)) },
                text = { TimePicker(clock) },
                confirmButton = {
                    TextButton(onClick = { day?.let { set(it.atTime(clock.hour, clock.minute)) } }) { Text(stringResource(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
    }
}

/** A tappable choice in a dialog: an icon, a name and an optional line on the right. */
@Composable
private fun ChoiceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

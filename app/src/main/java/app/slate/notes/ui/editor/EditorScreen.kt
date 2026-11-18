package app.slate.notes.ui.editor

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RemoveDone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.data.toggleTask
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.components.Chip
import app.slate.notes.ui.components.ColorSheet
import app.slate.notes.ui.components.EmptyState
import app.slate.notes.ui.components.LabelDialog
import app.slate.notes.ui.components.NoteImage
import app.slate.notes.ui.components.ReminderDialog
import app.slate.notes.ui.components.reminderLabel
import app.slate.notes.ui.theme.noteSurface
import app.slate.notes.util.shareNote
import java.text.DateFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One note, laid out as in Google Keep: pictures across the top, the title, the text (or a
 * checklist), its reminder and labels as chips, and a bar along the bottom to add pictures or
 * tick boxes, change the colour, and more. The whole screen takes the note's colour.
 *
 * The title is the file name; the text is the file. Typing saves itself a moment after you
 * stop. When the file changes on another device and syncs in, the new text appears in place
 * (or, if you were mid-edit, you choose which to keep).
 *
 * [onIdChanged] is told the note's new id after a rename, so the back stack keeps pointing at
 * it. [onClosed] leaves the screen after the note is deleted from here, and
 * [onOpen] opens another note (a copy just made).
 */
@Composable
fun EditorScreen(
    viewModel: NotesViewModel,
    id: String,
    onBack: () -> Unit,
    onIdChanged: (String) -> Unit,
    onClosed: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val open by viewModel.session.collectAsStateWithLifecycle()
    val loaded = library != null
    LaunchedEffect(id, loaded) { viewModel.openNote(id) }
    // Going back closes the session at once, but the screen still shows while it slides away:
    // it keeps showing the note it had rather than flashing a loading state.
    val shown = remember { arrayOfNulls<NoteSession>(1) }
    val session = open?.takeIf { it.note.id == id || it.openedAs == id }?.also { shown[0] = it }
        ?: shown[0]?.takeIf { it.note.id == id || it.openedAs == id }

    if (session == null) {
        Scaffold(topBar = { BarRow { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) } } }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                val missing = loaded && library?.notes?.none { it.id == id } == true
                if (missing) {
                    EmptyState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.note_missing_title),
                        body = stringResource(R.string.note_missing_body),
                        modifier = Modifier.padding(top = 48.dp),
                    )
                } else {
                    LoadingIndicator(Modifier.padding(top = 96.dp))
                }
            }
        }
        return
    }
    LaunchedEffect(session.note.id) { if (session.note.id != id) onIdChanged(session.note.id) }
    Editor(viewModel, session, onBack, onClosed, onOpen)
}

/** A 64dp row for the top of the screen, below the status bar. */
@Composable
private fun BarRow(color: Color = MaterialTheme.colorScheme.background, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(color).statusBarsPadding().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The editor itself, once the note's session is open. */
@Composable
private fun Editor(viewModel: NotesViewModel, session: NoteSession, onBack: () -> Unit, onClosed: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val openFormatted by viewModel.openFormatted.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    var formatted by rememberSaveable(session.openedAs) { mutableStateOf(openFormatted && !session.isNew) }
    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    // A new note starts in its title, once the fields exist (they appear after the file is read).
    val ready = session.text != null
    LaunchedEffect(session.openedAs, ready) { if (session.isNew && ready) runCatching { titleFocus.requestFocus() } }
    var coloring by remember { mutableStateOf(false) }
    var labeling by remember { mutableStateOf(false) }
    var reminding by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.addImage(session, uri)
    }

    // "Updated" shows for a moment each time newer text arrives from elsewhere.
    var showUpdated by remember { mutableStateOf(false) }
    LaunchedEffect(session.reloads) {
        if (session.reloads == 0) return@LaunchedEffect
        showUpdated = true
        delay(2_500)
        showUpdated = false
    }
    val meta = session.meta
    val background by animateColorAsState(noteSurface(meta.color), spring(stiffness = Spring.StiffnessMediumLow), label = "color")

    Scaffold(
        containerColor = background,
        topBar = {
            BarRow(background) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                Box(Modifier.weight(1f).padding(start = 8.dp)) {
                    StatusPill(visible = showUpdated, text = stringResource(R.string.updated_elsewhere), emphasized = true)
                    StatusPill(visible = session.saving && !showUpdated, text = stringResource(R.string.saving), emphasized = false)
                }
                IconButton(onClick = { session.updateMeta { it.copy(pinned = !it.pinned) } }) {
                    AnimatedContent(targetState = meta.pinned, transitionSpec = { bounceIn() togetherWith fadeOut() }, label = "pin") { pinned ->
                        Icon(if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, stringResource(if (pinned) R.string.unpin else R.string.pin))
                    }
                }
                IconButton(onClick = { reminding = true }) { Icon(Icons.Outlined.AddAlert, stringResource(R.string.remind_me)) }
                IconButton(onClick = {
                    viewModel.setArchived(listOf(session.note), !meta.archived)
                    // Archiving puts the note away, as in Keep; the Undo bar can bring it back.
                    if (!meta.archived) onBack()
                }) {
                    Icon(
                        if (meta.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                        stringResource(if (meta.archived) R.string.unarchive else R.string.archive),
                    )
                }
                if (!session.listMode) {
                    IconButton(onClick = { formatted = !formatted }) {
                        AnimatedContent(targetState = formatted, transitionSpec = { bounceIn() togetherWith (scaleOut() + fadeOut()) }, label = "mode") { isFormatted ->
                            if (isFormatted) Icon(Icons.Rounded.Edit, stringResource(R.string.edit))
                            else Icon(Icons.Rounded.Visibility, stringResource(R.string.show_formatted))
                        }
                    }
                }
            }
        },
        bottomBar = {
            BottomTools(
                background = background,
                session = session,
                onAddImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onToggleList = {
                    formatted = false
                    session.toggleListMode()
                },
                onColor = { coloring = true },
                onLabels = { labeling = true },
                onCopy = { viewModel.copy(session.note, onOpen) },
                onShare = { shareNote(context, session.note.title, session.text?.text.orEmpty()) },
                onDelete = {
                    viewModel.trash(listOf(session.note))
                    onClosed()
                },
            )
        },
    ) { padding ->
        val text = session.text
        if (text == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                if (session.failed) {
                    EmptyState(
                        icon = Icons.Outlined.ErrorOutline,
                        title = stringResource(R.string.note_unreadable_title),
                        body = stringResource(R.string.note_unreadable_body),
                        modifier = Modifier.padding(top = 48.dp),
                    )
                } else {
                    LoadingIndicator(Modifier.padding(top = 96.dp))
                }
            }
        } else {
            NoteBody(
                viewModel = viewModel,
                session = session,
                text = text,
                padding = padding,
                formatted = formatted && !session.listMode,
                onFormattedChange = { formatted = it },
                titleFocus = titleFocus,
                bodyFocus = bodyFocus,
                onReminder = { reminding = true },
                onLabels = { labeling = true },
            )
        }
    }

    if (coloring) {
        ColorSheet(current = meta.color, onPick = { color -> session.updateMeta { it.copy(color = color) } }, onDismiss = { coloring = false })
    }
    if (labeling) {
        LabelDialog(
            labels = (labels + meta.tags).distinctBy { it.lowercase() },
            state = { label -> if (meta.tags.any { it.equals(label, ignoreCase = true) }) ToggleableState.On else ToggleableState.Off },
            onToggle = { label, add -> viewModel.setLabel(listOf(session.note), label, add) },
            onDismiss = { labeling = false },
        )
    }
    if (reminding) {
        ReminderDialog(
            current = meta.reminder,
            onSet = { time ->
                reminding = false
                session.updateMeta { it.copy(reminder = time) }
            },
            onDismiss = { reminding = false },
        )
    }
}

/** An icon springing in, slightly overshooting. */
private fun bounceIn() = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) + fadeIn()

/**
 * Keep's bottom bar: add a picture or switch tick boxes, the colour palette, when it was last
 * edited, and a menu with labels, copy, share, delete (and, for checklists, untick all and
 * delete ticked items). It rides above the keyboard.
 */
@Composable
private fun BottomTools(
    background: Color,
    session: NoteSession,
    onAddImage: () -> Unit,
    onToggleList: () -> Unit,
    onColor: () -> Unit,
    onLabels: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(background).navigationBarsPadding().imePadding().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            IconButton(onClick = { adding = true }) { Icon(Icons.Outlined.AddBox, stringResource(R.string.add)) }
            DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.add_image)) },
                    leadingIcon = { Icon(Icons.Outlined.Image, null) },
                    onClick = { adding = false; onAddImage() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (session.listMode) R.string.hide_tick_boxes else R.string.show_tick_boxes)) },
                    leadingIcon = { Icon(Icons.Outlined.CheckBox, null) },
                    onClick = { adding = false; onToggleList() },
                )
            }
        }
        IconButton(onClick = onColor) { Icon(Icons.Outlined.Palette, stringResource(R.string.color)) }
        Text(
            stringResource(
                R.string.edited_at,
                DateUtils.formatSameDayTime(session.note.modified, System.currentTimeMillis(), DateFormat.MEDIUM, DateFormat.SHORT).toString(),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem(Icons.AutoMirrored.Outlined.Label, stringResource(R.string.labels)) { menu = false; onLabels() }
                if (session.listMode) {
                    MenuItem(Icons.Outlined.RemoveDone, stringResource(R.string.untick_all)) { menu = false; session.untickAll() }
                    MenuItem(Icons.Outlined.Delete, stringResource(R.string.delete_ticked)) { menu = false; session.deleteTicked() }
                }
                MenuItem(Icons.Outlined.ContentCopy, stringResource(R.string.make_copy)) { menu = false; onCopy() }
                MenuItem(Icons.Outlined.Share, stringResource(R.string.share)) { menu = false; onShare() }
                MenuItem(Icons.Outlined.Delete, stringResource(R.string.delete)) { menu = false; onDelete() }
            }
        }
    }
}

/** One entry in a menu, with its icon. */
@Composable
private fun MenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}

/** The pictures, title, any banners, the text (editable, formatted, or as a checklist) and the chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteBody(
    viewModel: NotesViewModel,
    session: NoteSession,
    text: TextFieldValue,
    padding: PaddingValues,
    formatted: Boolean,
    onFormattedChange: (Boolean) -> Unit,
    titleFocus: FocusRequester,
    bodyFocus: FocusRequester,
    onReminder: () -> Unit,
    onLabels: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    val monospace by viewModel.monospace.collectAsStateWithLifecycle()
    val tickedToBottom by viewModel.tickedToBottom.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        // Pictures run edge to edge, as in Keep; a long press offers to take one out.
        session.images.forEach { image ->
            var menu by remember { mutableStateOf(false) }
            Box {
                NoteImage(
                    viewModel.store(), session.note.folder, image.path,
                    Modifier.combinedClickable(onClick = {}, onLongClick = { menu = true }),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MenuItem(Icons.Outlined.Delete, stringResource(R.string.remove_image)) { menu = false; session.removeImage(image.path) }
                }
            }
        }
        Column(Modifier.padding(horizontal = 24.dp)) {
            Banner(
                visible = session.conflict != null,
                icon = Icons.Outlined.CloudSync,
                message = stringResource(R.string.conflict_message),
                primary = stringResource(R.string.keep_mine) to session::keepMine,
                secondary = stringResource(R.string.use_theirs) to session::useTheirs,
            )
            Banner(
                visible = session.gone,
                icon = Icons.Outlined.ErrorOutline,
                message = stringResource(R.string.gone_message),
                primary = stringResource(R.string.save_as_new) to session::saveAsNew,
                secondary = null,
            )
            BasicTextField(
                value = session.title,
                onValueChange = { session.title = it.replace("\n", "") },
                textStyle = MaterialTheme.typography.headlineSmall.copy(color = scheme.onSurface, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = {
                    onFormattedChange(false)
                    scope.launch {
                        // The body field may only just be appearing (leaving the formatted view).
                        delay(50)
                        runCatching { bodyFocus.requestFocus() }
                    }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .focusRequester(titleFocus)
                    .onFocusChanged { if (!it.isFocused) scope.launch { session.commitTitle() } },
                decorationBox = { field ->
                    Box {
                        if (session.title.isEmpty()) {
                            Text(
                                stringResource(R.string.title_hint),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                        field()
                    }
                },
            )
            if (session.note.folder.isNotEmpty()) {
                Text(session.note.folder, style = MaterialTheme.typography.labelLarge, color = scheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = when {
                    session.listMode -> 2
                    formatted -> 1
                    else -> 0
                },
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.97f)) togetherWith
                        fadeOut(motion.fastEffectsSpec())
                },
                label = "body",
            ) { mode ->
                when (mode) {
                    2 -> ChecklistEditor(session, tickedToBottom, monospace)
                    1 -> MarkdownView(
                        source = text.text,
                        onToggleTask = { line -> session.replaceText(toggleTask(text.text, line)) },
                        store = viewModel.store(),
                        noteFolder = session.note.folder,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 240.dp),
                    )
                    else -> BasicTextField(
                        value = text,
                        onValueChange = session::edit,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = scheme.onSurface,
                            fontFamily = if (monospace) FontFamily.Monospace else MaterialTheme.typography.bodyLarge.fontFamily,
                        ),
                        cursorBrush = SolidColor(scheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 240.dp).focusRequester(bodyFocus),
                        decorationBox = { field ->
                            Box {
                                if (text.text.isEmpty()) {
                                    Text(
                                        stringResource(R.string.body_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                field()
                            }
                        },
                    )
                }
            }
            val meta = session.meta
            if (meta.reminder != null || meta.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    meta.reminder?.let { Chip(Icons.Outlined.Alarm, reminderLabel(it), onClick = onReminder) }
                    meta.tags.forEach { Chip(null, it, onClick = onLabels) }
                }
            }
        }
    }
}

/** A small rounded label that drops in with a bounce and fades away. */
@Composable
private fun StatusPill(visible: Boolean, text: String, emphasized: Boolean) {
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) { -it } + fadeIn(),
        exit = slideOutVertically { -it / 2 } + fadeOut(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasized) scheme.onTertiaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier
                .clip(CircleShape)
                .background(if (emphasized) scheme.tertiaryContainer else scheme.onSurface.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** A card across the top of the note for something that needs a choice, sliding open and shut. */
@Composable
private fun Banner(
    visible: Boolean,
    icon: ImageVector,
    message: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>?,
) {
    val scheme = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Surface(
            color = scheme.tertiaryContainer,
            contentColor = scheme.onTertiaryContainer,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(message, style = MaterialTheme.typography.bodyMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (secondary != null) TextButton(onClick = secondary.second) { Text(secondary.first) }
                    TextButton(onClick = primary.second) { Text(primary.first) }
                }
            }
        }
    }
}

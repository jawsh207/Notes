package app.slate.notes.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.components.EmptyState
import app.slate.notes.util.shareNote
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One note, to write in or read formatted. The title is the file name; the text is the file.
 * Typing saves itself a moment after you stop. When the file changes on another device and syncs
 * in, the new text appears in place (or, if you were mid-edit, you choose which to keep).
 *
 * [onIdChanged] is told the note's new id after a rename, so the back stack keeps pointing at it.
 * [onDeleted] closes the screen after the note is deleted from here.
 */
@Composable
fun EditorScreen(
    viewModel: NotesViewModel,
    id: String,
    onBack: () -> Unit,
    onIdChanged: (String) -> Unit,
    onDeleted: () -> Unit,
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
        Scaffold(topBar = { EditorTopBar(onBack = onBack) }) { padding ->
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
    Editor(viewModel, session, onBack, onDeleted)
}

/** The editor itself, once the note's session is open. */
@Composable
private fun Editor(viewModel: NotesViewModel, session: NoteSession, onBack: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val openFormatted by viewModel.openFormatted.collectAsStateWithLifecycle()
    val monospace by viewModel.monospace.collectAsStateWithLifecycle()
    var formatted by rememberSaveable(session.openedAs) { mutableStateOf(openFormatted && !session.isNew) }
    val titleFocus = remember { FocusRequester() }
    val bodyFocus = remember { FocusRequester() }
    // A new note starts in its title, once the fields exist (they appear after the file is read).
    val ready = session.text != null
    LaunchedEffect(session.openedAs, ready) { if (session.isNew && ready) runCatching { titleFocus.requestFocus() } }

    // "Updated" shows for a moment each time newer text arrives from elsewhere.
    var showUpdated by remember { mutableStateOf(false) }
    LaunchedEffect(session.reloads) {
        if (session.reloads == 0) return@LaunchedEffect
        showUpdated = true
        delay(2_500)
        showUpdated = false
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                onBack = onBack,
                saving = session.saving,
                updated = showUpdated,
                formatted = formatted,
                onToggleFormatted = { formatted = !formatted },
                onShare = { shareNote(context, session.note.title, session.text?.text.orEmpty()) },
                onDelete = {
                    viewModel.delete(session.note)
                    onDeleted()
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
            NoteBody(session, text, padding, formatted, monospace, onFormattedChange = { formatted = it }, titleFocus, bodyFocus)
        }
    }
}

/** The title, any banners, and the text: editable, or drawn formatted. */
@Composable
private fun NoteBody(
    session: NoteSession,
    text: androidx.compose.ui.text.input.TextFieldValue,
    padding: androidx.compose.foundation.layout.PaddingValues,
    formatted: Boolean,
    monospace: Boolean,
    onFormattedChange: (Boolean) -> Unit,
    titleFocus: FocusRequester,
    bodyFocus: FocusRequester,
) {
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = padding.calculateTopPadding())
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = padding.calculateBottomPadding() + 48.dp),
    ) {
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
        val scheme = MaterialTheme.colorScheme
        BasicTextField(
            value = session.title,
            onValueChange = { session.title = it.replace("\n", "") },
            textStyle = MaterialTheme.typography.headlineMedium.copy(color = scheme.onSurface, fontWeight = FontWeight.SemiBold),
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
                .padding(top = 8.dp)
                .focusRequester(titleFocus)
                .onFocusChanged { if (!it.isFocused) scope.launch { session.commitTitle() } },
            decorationBox = { field ->
                Box {
                    if (session.title.isEmpty()) {
                        Text(
                            stringResource(R.string.title_hint),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    field()
                }
            },
        )
        if (session.note.folder.isNotEmpty()) {
            Text(
                session.note.folder,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = formatted,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.97f)) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            },
            label = "mode",
        ) { showFormatted ->
            if (showFormatted) {
                MarkdownView(
                    source = text.text,
                    onToggleTask = { line -> session.replaceText(app.slate.notes.data.toggleTask(text.text, line)) },
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 320.dp),
                )
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = session::edit,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = scheme.onSurface,
                        fontFamily = if (monospace) FontFamily.Monospace else MaterialTheme.typography.bodyLarge.fontFamily,
                    ),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 320.dp).focusRequester(bodyFocus),
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
    }
}

/**
 * The editor's top bar: back, a "Saving…" or "Updated" note that springs in and out, the
 * formatted/edit switch, and a menu with share and delete. Opaque, so the text scrolls behind it.
 */
@Composable
private fun EditorTopBar(
    onBack: () -> Unit,
    saving: Boolean = false,
    updated: Boolean = false,
    formatted: Boolean? = null,
    onToggleFormatted: () -> Unit = {},
    onShare: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()
            .height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
        Box(Modifier.weight(1f).padding(start = 8.dp)) {
            StatusPill(visible = updated, text = stringResource(R.string.updated_elsewhere), emphasized = true)
            StatusPill(visible = saving && !updated, text = stringResource(R.string.saving), emphasized = false)
        }
        if (formatted != null) {
            FilledTonalIconButton(onClick = onToggleFormatted) {
                AnimatedContent(
                    targetState = formatted,
                    transitionSpec = {
                        (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) + fadeIn()) togetherWith
                            (scaleOut() + fadeOut())
                    },
                    label = "modeIcon",
                ) { isFormatted ->
                    if (isFormatted) {
                        Icon(Icons.Rounded.Edit, stringResource(R.string.edit))
                    } else {
                        Icon(Icons.Rounded.Visibility, stringResource(R.string.show_formatted))
                    }
                }
            }
        }
        if (onShare != null || onDelete != null) {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (onShare != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share)) },
                            leadingIcon = { Icon(Icons.Outlined.Share, null) },
                            onClick = { menu = false; onShare() },
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
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
                .background(if (emphasized) scheme.tertiaryContainer else scheme.surfaceContainerHigh, CircleShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** A card across the top of the note for something that needs a choice, sliding open and shut. */
@Composable
private fun Banner(
    visible: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

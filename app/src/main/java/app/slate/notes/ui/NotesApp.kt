package app.slate.notes.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.ui.browse.BrowseTab
import app.slate.notes.ui.components.BottomBar
import app.slate.notes.ui.components.FabChoice
import app.slate.notes.ui.components.NewNoteFab
import app.slate.notes.ui.components.SearchPill
import app.slate.notes.ui.components.TextInputDialog
import app.slate.notes.ui.editor.EditorScreen
import app.slate.notes.ui.folders.FolderScreen
import app.slate.notes.ui.notes.CollectionScreen
import app.slate.notes.ui.notes.NotesTab
import app.slate.notes.ui.notes.SelectedNotesBar
import app.slate.notes.ui.notes.TrashScreen
import app.slate.notes.ui.notes.rememberSelection
import app.slate.notes.ui.search.SearchScreen
import app.slate.notes.ui.settings.SettingsScreen
import app.slate.notes.ui.setup.SetupScreen
import kotlinx.coroutines.launch

/** The two bottom tabs, in order. Notes is home. */
enum class Tab(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Notes(R.string.tab_notes, Icons.Outlined.Lightbulb, Icons.Filled.Lightbulb),
    Browse(R.string.tab_browse, Icons.Outlined.Explore, Icons.Filled.Explore),
}

/**
 * The whole app: asks for a notes folder the first time, then shows the screen on top of the
 * back stack. Screens slide and spring in from the side going forward and back out going back.
 */
@Composable
fun NotesApp(viewModel: NotesViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.setForeground(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.setForeground(false) }

    val folder by viewModel.folder.collectAsStateWithLifecycle()
    when (val state = folder) {
        FolderState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        is FolderState.Needed -> SetupScreen(lost = state.lost, onChosen = viewModel::chooseFolder)
        FolderState.Ready -> Screens(viewModel)
    }
}

/** The back stack of screens, with the Undo bar on top of them. */
@Composable
private fun Screens(viewModel: NotesViewModel) {
    val context = LocalContext.current
    val stack = rememberBackStack()
    var tab by rememberSaveable { mutableStateOf(Tab.Notes) }
    val open: (Screen) -> Unit = { stack.add(it) }
    val back: () -> Unit = {
        if (stack.size > 1) {
            if (stack.last() is Screen.Note) viewModel.closeNote()
            stack.removeAt(stack.lastIndex)
        }
    }
    BackHandler(enabled = stack.size > 1, onBack = back)
    val openNote: (String) -> Unit = { open(Screen.Note(it)) }
    val newNote: (folder: String, list: Boolean, label: String?) -> Unit = { path, list, label ->
        viewModel.createNote(path, list, label) { id -> open(Screen.Note(id)) }
    }

    // A note asked for by a reminder notification opens once the folder has been read.
    val request by viewModel.openRequest.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    LaunchedEffect(request, library != null) {
        val id = request ?: return@LaunchedEffect
        if (library == null) return@LaunchedEffect
        viewModel.openRequestHandled()
        if (library?.notes?.any { it.id == id } == true) open(Screen.Note(id))
    }

    // Undo for notes just trashed or archived.
    val snackbar = remember { SnackbarHostState() }
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    LaunchedEffect(undo) {
        val action = undo
        if (action == null) {
            snackbar.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        val message = when (action.kind) {
            UndoKind.Trashed -> action.title?.let { context.getString(R.string.trashed_one, it) }
                ?: context.resources.getQuantityString(R.plurals.trashed_many, action.count, action.count)
            UndoKind.Archived -> context.resources.getQuantityString(R.plurals.archived_many, action.count, action.count)
            UndoKind.Unarchived -> context.resources.getQuantityString(R.plurals.unarchived_many, action.count, action.count)
        }
        val result = snackbar.showSnackbar(message, actionLabel = context.getString(R.string.undo), duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) action.undo()
        viewModel.undoShown()
    }

    val motion = MaterialTheme.motionScheme
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stack.last() to stack.size,
            // A renamed note gets a new id; that's the same screen, so it mustn't animate.
            contentKey = { (screen, depth) -> if (screen is Screen.Note) "note:$depth" else screen },
            transitionSpec = {
                val forward = targetState.second >= initialState.second
                val slide = motion.defaultSpatialSpec<IntOffset>()
                val transform: ContentTransform = if (forward) {
                    (slideInHorizontally(slide) { it / 3 } + fadeIn(motion.defaultEffectsSpec()) +
                        scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f)) togetherWith
                        (slideOutHorizontally(slide) { -it / 8 } + fadeOut(motion.fastEffectsSpec()))
                } else {
                    (slideInHorizontally(slide) { -it / 8 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                        (slideOutHorizontally(slide) { it / 3 } + fadeOut(motion.fastEffectsSpec()) +
                            scaleOut(motion.defaultSpatialSpec(), targetScale = 0.96f))
                }
                // The screen being opened draws over the one it covers; going back, the reverse.
                transform.apply { targetContentZIndex = if (forward) 1f else 0f }
            },
            label = "screens",
        ) { (screen, _) ->
            when (screen) {
                Screen.Home -> Home(viewModel, tab, onTabChange = { tab = it }, open = open, newNote = newNote)
                Screen.Search -> SearchScreen(viewModel, onOpen = openNote, onBack = back)
                Screen.Settings -> SettingsScreen(viewModel, onBack = back)
                Screen.Trash -> TrashScreen(viewModel, onBack = back)
                Screen.Archive -> CollectionScreen(
                    viewModel,
                    title = stringResource(R.string.archive),
                    filter = { _, info -> info?.meta?.archived == true },
                    emptyIcon = Icons.Outlined.Archive,
                    emptyTitle = stringResource(R.string.archive_empty_title),
                    emptyBody = stringResource(R.string.archive_empty_body),
                    onOpen = openNote,
                    onBack = back,
                    pinSections = false,
                )
                Screen.Reminders -> CollectionScreen(
                    viewModel,
                    title = stringResource(R.string.reminders),
                    filter = { _, info -> info?.meta?.reminder != null },
                    emptyIcon = Icons.Outlined.Alarm,
                    emptyTitle = stringResource(R.string.reminders_empty_title),
                    emptyBody = stringResource(R.string.reminders_empty_body),
                    onOpen = openNote,
                    onBack = back,
                    sort = { notes ->
                        val infos = viewModel.infos.value
                        notes.sortedBy { infos[it.id]?.meta?.reminder }
                    },
                )
                is Screen.Label -> LabelScreen(viewModel, screen.name, openNote, newNote, back) { renamed ->
                    stack[stack.lastIndex] = Screen.Label(renamed)
                }
                is Screen.Folder -> FolderScreen(
                    viewModel, screen.path,
                    onOpen = openNote,
                    onOpenFolder = { open(Screen.Folder(it)) },
                    onNew = { list -> newNote(screen.path, list, null) },
                    onBack = back,
                )
                is Screen.Note -> EditorScreen(
                    viewModel, screen.id,
                    onBack = back,
                    onIdChanged = { newId ->
                        val at = stack.indexOfLast { it is Screen.Note }
                        if (at >= 0) stack[at] = Screen.Note(newId)
                    },
                    onClosed = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                    onOpen = openNote,
                )
            }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(bottom = if (stack.last() == Screen.Home) 80.dp else 16.dp),
        )
    }
}

/** A label's page: its notes, a + that makes notes with the label, and a menu to rename or delete the label. */
@Composable
private fun LabelScreen(
    viewModel: NotesViewModel,
    label: String,
    onOpen: (String) -> Unit,
    newNote: (String, Boolean, String?) -> Unit,
    onBack: () -> Unit,
    onRenamed: (String) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    CollectionScreen(
        viewModel,
        title = label,
        filter = { _, info -> info?.meta?.tags?.any { it.equals(label, ignoreCase = true) } == true },
        emptyIcon = Icons.AutoMirrored.Outlined.Label,
        emptyTitle = stringResource(R.string.label_empty_title),
        emptyBody = stringResource(R.string.label_empty_body),
        onOpen = onOpen,
        onBack = onBack,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rename_label)) },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { menu = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete_label)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = { menu = false; deleting = true },
                    )
                }
            }
        },
        fab = { NewNoteFab(onNote = { newNote("", false, label) }, onList = { newNote("", true, label) }) },
    )
    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.rename_label),
            initial = label,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { name ->
                renaming = false
                viewModel.renameLabel(label, name)
                onRenamed(app.slate.notes.data.cleanLabel(name))
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_label_title, label)) },
            text = { Text(stringResource(R.string.delete_label_body)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    viewModel.deleteLabel(label)
                    onBack()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * The bottom tabs, as swipeable pages (as in Contacts and Dialtone): swipe between them or tap
 * the bottom bar. Back from Browse returns to Notes. The + springs open into New note and New
 * list (and New folder on Browse). Long-pressing a card picks it, and the search bar becomes a
 * bar of actions for the picked notes.
 */
@Composable
private fun Home(
    viewModel: NotesViewModel,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    open: (Screen) -> Unit,
    newNote: (String, Boolean, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = tab.ordinal) { Tab.entries.size }
    // targetPage, not currentPage, so the bar and button follow a swipe as soon as it's let go.
    val current = Tab.entries[pager.targetPage]
    LaunchedEffect(pager.settledPage) { onTabChange(Tab.entries[pager.settledPage]) }
    val notesGrid = rememberLazyStaggeredGridState()
    val browseList = rememberLazyListState()
    val selection = rememberSelection()
    val selecting = selection.active && current == Tab.Notes
    LaunchedEffect(current) { if (current != Tab.Notes) selection.clear() }
    BackHandler(enabled = selecting) { selection.clear() }
    BackHandler(enabled = current != Tab.Notes && !selecting) { scope.launch { pager.animateScrollToPage(Tab.Notes.ordinal) } }
    val inGrid by viewModel.grid.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val infos by viewModel.infos.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }

    Scaffold(
        // Pinned above the tabs, as in Contacts: lists scroll behind it.
        topBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                AnimatedContent(targetState = selecting, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "bar") { picking ->
                    if (picking) {
                        val visible = library?.notes.orEmpty().filter { infos[it.id]?.meta?.archived != true }
                        SelectedNotesBar(viewModel, selection, visible, onOpen = { open(Screen.Note(it)) })
                    } else {
                        SearchPill(
                            onClick = { open(Screen.Search) },
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            trailing = {
                                Row {
                                    IconButton(onClick = { viewModel.setGrid(!inGrid) }) {
                                        AnimatedContent(targetState = inGrid, label = "layout") { grid ->
                                            Icon(
                                                if (grid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                                                stringResource(if (grid) R.string.list_view else R.string.grid_view),
                                            )
                                        }
                                    }
                                    IconButton(onClick = { open(Screen.Settings) }) {
                                        Icon(Icons.Rounded.Settings, stringResource(R.string.settings))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selecting) {
                NewNoteFab(
                    onNote = { newNote("", false, null) },
                    onList = { newNote("", true, null) },
                    extra = if (current == Tab.Browse) {
                        listOf(FabChoice(stringResource(R.string.new_folder), Icons.Outlined.CreateNewFolder) { naming = true })
                    } else {
                        emptyList()
                    },
                )
            }
        },
        bottomBar = {
            BottomBar {
                Tab.entries.forEach { entry ->
                    val selected = entry == current
                    ShortNavigationBarItem(
                        selected = selected,
                        onClick = { scope.launch { pager.animateScrollToPage(entry.ordinal) } },
                        icon = { Icon(if (selected) entry.selectedIcon else entry.icon, contentDescription = null) },
                        label = { Text(stringResource(entry.label)) },
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager, modifier = Modifier.fillMaxSize(), key = { Tab.entries[it] },
            // No swiping away from a selection.
            userScrollEnabled = !selecting,
        ) { page ->
            when (Tab.entries[page]) {
                Tab.Notes -> NotesTab(viewModel, padding, notesGrid, selection, onOpen = { open(Screen.Note(it)) })
                Tab.Browse -> BrowseTab(viewModel, padding, browseList, open)
            }
        }
    }

    if (naming) {
        TextInputDialog(
            title = stringResource(R.string.new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            fieldLabel = stringResource(R.string.folder_name),
            onConfirm = { name -> naming = false; viewModel.createFolder("", name) },
            onDismiss = { naming = false },
        )
    }
}

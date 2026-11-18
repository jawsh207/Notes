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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import app.slate.notes.R
import app.slate.notes.ui.components.BottomBar
import app.slate.notes.ui.components.SearchPill
import app.slate.notes.ui.components.TextInputDialog
import app.slate.notes.ui.editor.EditorScreen
import app.slate.notes.ui.folders.FolderContents
import app.slate.notes.ui.folders.FolderScreen
import app.slate.notes.ui.notes.NoteActions
import app.slate.notes.ui.notes.NotesTab
import app.slate.notes.ui.search.SearchScreen
import app.slate.notes.ui.settings.SettingsScreen
import app.slate.notes.ui.setup.SetupScreen
import app.slate.notes.util.shareNote
import kotlinx.coroutines.launch

/** The two bottom tabs, in order. Notes is home. */
enum class Tab(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Notes(R.string.tab_notes, Icons.Outlined.Description, Icons.Filled.Description),
    Folders(R.string.tab_folders, Icons.Outlined.Folder, Icons.Filled.Folder),
}

/**
 * The whole app: asks for a notes folder the first time, then shows the screen on top of the
 * back stack. Screens slide and spring in from the side going forward and back out going back.
 */
@Composable
fun NotesApp(viewModel: NotesViewModel = viewModel()) {
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

/** The back stack of screens, with the undo bar for deleted notes on top of them. */
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

    val texts by viewModel.texts.collectAsStateWithLifecycle()
    val actions = NoteActions(
        open = { open(Screen.Note(it.id)) },
        share = { shareNote(context, it.title, texts[it.id].orEmpty()) },
        delete = viewModel::delete,
    )
    val newNoteIn: (String) -> Unit = { path -> viewModel.createNote(path) { id -> open(Screen.Note(id)) } }

    // Undo for a note just deleted, here or from the note itself.
    val snackbar = remember { SnackbarHostState() }
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) {
        val gone = deleted
        if (gone == null) {
            snackbar.currentSnackbarData?.dismiss()
            return@LaunchedEffect
        }
        val result = snackbar.showSnackbar(
            context.getString(R.string.deleted_note, gone.note.title),
            actionLabel = context.getString(R.string.undo),
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.forgetDeleted()
    }

    val motion = MaterialTheme.motionScheme
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = stack.last() to stack.size,
            // A renamed note gets a new id; that's the same screen, so it mustn't animate.
            contentKey = { (screen, _) -> if (screen is Screen.Note) "note" else screen },
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
                Screen.Home -> Home(viewModel, tab, onTabChange = { tab = it }, open = open, actions = actions, newNoteIn = newNoteIn)
                Screen.Search -> SearchScreen(viewModel, actions, onBack = back)
                Screen.Settings -> SettingsScreen(viewModel, onBack = back)
                is Screen.Folder -> FolderScreen(
                    viewModel, screen.path, actions,
                    onOpenFolder = { open(Screen.Folder(it.path)) },
                    onNewNote = { newNoteIn(screen.path) },
                    onBack = back,
                )
                is Screen.Note -> EditorScreen(
                    viewModel, screen.id,
                    onBack = back,
                    onIdChanged = { newId ->
                        val at = stack.indexOfLast { it is Screen.Note }
                        if (at >= 0) stack[at] = Screen.Note(newId)
                    },
                    onDeleted = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
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

/**
 * The bottom tabs, as swipeable pages (as in Contacts and Dialtone): swipe between them or tap
 * the bottom bar. Back from Folders returns to Notes. The button writes a new note on the Notes
 * tab and makes a folder on the Folders tab; it tucks into an icon once the list is scrolled.
 */
@Composable
private fun Home(
    viewModel: NotesViewModel,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    open: (Screen) -> Unit,
    actions: NoteActions,
    newNoteIn: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = tab.ordinal) { Tab.entries.size }
    // targetPage, not currentPage, so the bar and button follow a swipe as soon as it's let go.
    val current = Tab.entries[pager.targetPage]
    LaunchedEffect(pager.settledPage) { onTabChange(Tab.entries[pager.settledPage]) }
    BackHandler(enabled = current != Tab.Notes) { scope.launch { pager.animateScrollToPage(Tab.Notes.ordinal) } }
    val notesList = rememberLazyListState()
    val foldersList = rememberLazyListState()
    val atTop = if (current == Tab.Notes) !notesList.canScrollBackward else !foldersList.canScrollBackward
    var naming by remember { mutableStateOf(false) }

    Scaffold(
        // Pinned above the tabs, as in Contacts: lists scroll behind it.
        topBar = {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
                SearchPill(
                    onClick = { open(Screen.Search) },
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    trailing = {
                        IconButton(onClick = { open(Screen.Settings) }) {
                            Icon(Icons.Rounded.Settings, stringResource(R.string.settings))
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            val notesTab = current == Tab.Notes
            ExtendedFloatingActionButton(
                onClick = { if (notesTab) newNoteIn("") else naming = true },
                expanded = atTop,
                icon = {
                    AnimatedContent(targetState = notesTab, label = "fabIcon") { isNotes ->
                        Icon(if (isNotes) Icons.Rounded.Edit else Icons.Outlined.CreateNewFolder, null)
                    }
                },
                text = {
                    AnimatedContent(targetState = notesTab, label = "fabText") { isNotes ->
                        Text(stringResource(if (isNotes) R.string.new_note else R.string.new_folder))
                    }
                },
            )
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
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), key = { Tab.entries[it] }) { page ->
            when (Tab.entries[page]) {
                Tab.Notes -> NotesTab(viewModel, padding, notesList, actions)
                Tab.Folders -> FolderContents(
                    viewModel, path = "", contentPadding = padding, listState = foldersList, actions = actions,
                    onOpenFolder = { open(Screen.Folder(it.path)) },
                )
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

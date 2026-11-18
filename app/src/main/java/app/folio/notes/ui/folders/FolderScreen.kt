package app.folio.notes.ui.folders

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.folio.notes.R
import app.folio.notes.data.Folder
import app.folio.notes.ui.NotesViewModel
import app.folio.notes.ui.components.BackTopBar
import app.folio.notes.ui.components.TextInputDialog
import app.folio.notes.ui.notes.NoteActions

/**
 * One folder's page: its sub-folders and notes, a button to write a note here and one to make
 * a sub-folder.
 */
@Composable
fun FolderScreen(
    viewModel: NotesViewModel,
    path: String,
    actions: NoteActions,
    onOpenFolder: (Folder) -> Unit,
    onNewNote: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    var naming by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            BackTopBar(path.substringAfterLast('/'), onBack) {
                IconButton(onClick = { naming = true }) { Icon(Icons.Outlined.CreateNewFolder, stringResource(R.string.new_folder)) }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewNote,
                expanded = !listState.canScrollBackward,
                icon = { Icon(Icons.Rounded.Edit, null) },
                text = { Text(stringResource(R.string.new_note)) },
            )
        },
    ) { padding ->
        FolderContents(viewModel, path, padding, listState, actions, onOpenFolder)
    }
    if (naming) {
        TextInputDialog(
            title = stringResource(R.string.new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            fieldLabel = stringResource(R.string.folder_name),
            onConfirm = { name -> naming = false; viewModel.createFolder(path, name) },
            onDismiss = { naming = false },
        )
    }
}

package app.slate.notes.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slate.notes.R
import app.slate.notes.data.SortOrder
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.components.BackTopBar
import app.slate.notes.ui.components.ListRow
import app.slate.notes.ui.components.SectionHeader
import app.slate.notes.ui.components.SEGMENT_GAP
import app.slate.notes.ui.components.segmentShape

/** Settings: the notes folder, how lists are sorted, how notes open, and the editor font, in grouped cards. */
@Composable
fun SettingsScreen(viewModel: NotesViewModel, onBack: () -> Unit) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val order by viewModel.sortOrder.collectAsStateWithLifecycle()
    val formatted by viewModel.openFormatted.collectAsStateWithLifecycle()
    val monospace by viewModel.monospace.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.chooseFolder(uri)
    }

    Scaffold(topBar = { BackTopBar(stringResource(R.string.settings), onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
        ) {
            item { SectionHeader(stringResource(R.string.settings_folder)) }
            item {
                ListRow(
                    icon = Icons.Outlined.FolderOpen,
                    title = library?.rootName ?: stringResource(R.string.loading),
                    summary = stringResource(R.string.settings_folder_summary),
                    shape = segmentShape(0, 1),
                    onClick = { pickFolder.launch(null) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_display)) }
            item {
                Surface(
                    shape = segmentShape(0, 3),
                    color = MaterialTheme.colorScheme.surfaceBright,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.bodyLarge)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            SortOrder.entries.forEachIndexed { index, entry ->
                                SegmentedButton(
                                    selected = order == entry,
                                    onClick = { viewModel.setSortOrder(entry) },
                                    shape = SegmentedButtonDefaults.itemShape(index, SortOrder.entries.size),
                                    label = {
                                        Text(stringResource(if (entry == SortOrder.Modified) R.string.sort_modified else R.string.sort_title))
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item {
                ListRow(
                    icon = Icons.Outlined.TextFields,
                    title = stringResource(R.string.settings_formatted_title),
                    summary = stringResource(R.string.settings_formatted_summary),
                    shape = segmentShape(1, 3),
                    onClick = { viewModel.setOpenFormatted(!formatted) },
                    trailing = { Switch(checked = formatted, onCheckedChange = viewModel::setOpenFormatted) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            item {
                ListRow(
                    icon = Icons.Outlined.FontDownload,
                    title = stringResource(R.string.settings_monospace_title),
                    summary = stringResource(R.string.settings_monospace_summary),
                    shape = segmentShape(2, 3),
                    onClick = { viewModel.setMonospace(!monospace) },
                    trailing = { Switch(checked = monospace, onCheckedChange = viewModel::setMonospace) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            item { SectionHeader(stringResource(R.string.settings_about)) }
            item {
                ListRow(
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.about_title),
                    summary = stringResource(R.string.about_body),
                    shape = segmentShape(0, 1),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

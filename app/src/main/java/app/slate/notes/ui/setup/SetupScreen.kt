package app.slate.notes.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.slate.notes.R
import app.slate.notes.ui.components.EmptyState

/**
 * The first screen: explains that notes are plain files in a folder of the user's choosing, and
 * opens the system folder picker. Also shown if the app [lost] access to the folder it had.
 */
@Composable
fun SetupScreen(lost: Boolean, onChosen: (android.net.Uri) -> Unit) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) onChosen(uri) }
    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = if (lost) Icons.Outlined.FolderOff else Icons.Outlined.CreateNewFolder,
            title = stringResource(if (lost) R.string.setup_lost_title else R.string.setup_title),
            body = stringResource(if (lost) R.string.setup_lost_body else R.string.setup_body),
            modifier = Modifier.padding(bottom = 48.dp),
            action = {
                Button(
                    onClick = { pick.launch(null) },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.choose_folder))
                }
            },
        )
    }
}

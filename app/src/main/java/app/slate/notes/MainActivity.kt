package app.slate.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.slate.notes.reminders.ReminderAlarms
import app.slate.notes.ui.NotesApp
import app.slate.notes.ui.NotesViewModel
import app.slate.notes.ui.theme.NotesTheme

/**
 * The app's only screen holder. Everything inside is drawn by [NotesApp]. It also takes what
 * comes from outside the app: a tap on a reminder notification or a widget, which asks for that
 * note to be opened, and the New note and New list shortcuts and widget buttons.
 */
class MainActivity : ComponentActivity() {
    private val viewModel: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only on a fresh start: after rotation the note is already open.
        if (savedInstanceState == null) handle(intent)
        setContent {
            NotesTheme {
                // A Surface sets the text and icon colour for the theme; screens drawn outside a
                // Scaffold (the folder picker screen, loading) would otherwise get black text.
                Surface(color = MaterialTheme.colorScheme.background) {
                    NotesApp(viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_NOTE -> intent.getStringExtra(ReminderAlarms.EXTRA_NOTE_ID)?.let(viewModel::requestOpen)
            ACTION_NEW_NOTE -> viewModel.requestNew(list = false)
            ACTION_NEW_LIST -> viewModel.requestNew(list = true)
        }
    }

    /** The actions other parts of the phone send to open a note or start a new one. */
    companion object {
        /** The action of an intent that opens the note named in its extras: a reminder's or widget's tap. */
        const val ACTION_OPEN_NOTE = "app.slate.notes.action.OPEN_NOTE"

        /** Makes a new note in Unsorted and opens it (the New note shortcut and widget button). */
        const val ACTION_NEW_NOTE = "app.slate.notes.action.NEW_NOTE"

        /** Makes a new list in Unsorted and opens it (the New list shortcut and widget button). */
        const val ACTION_NEW_LIST = "app.slate.notes.action.NEW_LIST"
    }
}

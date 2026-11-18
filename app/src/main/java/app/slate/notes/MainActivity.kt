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
 * The app's only screen holder. Everything inside is drawn by [NotesApp]. It also takes the
 * tap on a reminder notification, which asks for that note to be opened.
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
        if (intent?.action != ACTION_OPEN_NOTE) return
        intent.getStringExtra(ReminderAlarms.EXTRA_NOTE_ID)?.let(viewModel::requestOpen)
    }

    /** Opens a note: what a reminder notification's tap sends. */
    companion object {
        /** The action of an intent that opens the note named in its extras. */
        const val ACTION_OPEN_NOTE = "app.slate.notes.action.OPEN_NOTE"
    }
}

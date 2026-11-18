package app.folio.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.folio.notes.ui.NotesApp
import app.folio.notes.ui.theme.NotesTheme

/** The app's only screen holder. Everything inside is drawn by [NotesApp]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NotesTheme {
                NotesApp()
            }
        }
    }
}

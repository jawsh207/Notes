package app.slate.notes.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.slate.notes.data.NoteColor

// Note colours: soft pastels in light mode and deep tones in dark mode, in the spirit of
// Google Keep's, so dark text reads on the first and light text on the second.

private val LIGHT = mapOf(
    NoteColor.Red to Color(0xFFFAAFA8),
    NoteColor.Orange to Color(0xFFF39F76),
    NoteColor.Yellow to Color(0xFFFFF8B8),
    NoteColor.Green to Color(0xFFE2F6D3),
    NoteColor.Teal to Color(0xFFB4DDD3),
    NoteColor.Blue to Color(0xFFD4E4ED),
    NoteColor.DarkBlue to Color(0xFFAECCDC),
    NoteColor.Purple to Color(0xFFD3BFDB),
    NoteColor.Pink to Color(0xFFF6E2DD),
    NoteColor.Brown to Color(0xFFE9E3D4),
    NoteColor.Gray to Color(0xFFEFEFF1),
)

private val DARK = mapOf(
    NoteColor.Red to Color(0xFF77172E),
    NoteColor.Orange to Color(0xFF692B17),
    NoteColor.Yellow to Color(0xFF7C4A03),
    NoteColor.Green to Color(0xFF264D3B),
    NoteColor.Teal to Color(0xFF0C625D),
    NoteColor.Blue to Color(0xFF256377),
    NoteColor.DarkBlue to Color(0xFF284255),
    NoteColor.Purple to Color(0xFF472E5B),
    NoteColor.Pink to Color(0xFF6C394F),
    NoteColor.Brown to Color(0xFF4B443A),
    NoteColor.Gray to Color(0xFF232427),
)

/** Whether the theme in use is dark, judged from its page colour. */
@Composable
fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** The colour a card or the editor takes for [color]; the theme's card colour for [NoteColor.Default]. */
@Composable
fun noteSurface(color: NoteColor): Color =
    (if (isDarkTheme()) DARK else LIGHT)[color] ?: MaterialTheme.colorScheme.surfaceBright

/** The swatch shown for [color] in the colour picker; null for [NoteColor.Default], which shows a "no colour" mark. */
@Composable
fun noteSwatch(color: NoteColor): Color? = (if (isDarkTheme()) DARK else LIGHT)[color]

/**
 * The light-mode and dark-mode colours for [color], for home screen widgets, which pick between
 * them themselves as the phone switches; null for [NoteColor.Default].
 */
fun noteColorsDayNight(color: NoteColor): Pair<Color, Color>? {
    val day = LIGHT[color] ?: return null
    val night = DARK[color] ?: return null
    return day to night
}

package app.slate.notes.widgets

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.slate.notes.R
import app.slate.notes.data.NoteColor
import app.slate.notes.ui.theme.noteColorsDayNight
import androidx.glance.color.ColorProvider as dayNight

// Pieces every widget is built from, so they look alike: the rounded panel, the title row and
// the messages shown when there's nothing to show yet. Widgets are drawn by the home screen,
// not the app, so these use Glance (Compose for widgets) and the phone's wallpaper colours.

/** The rounded panel a widget sits on, in [background] (the theme's widget colour when null). */
@Composable
fun WidgetPanel(background: ColorProvider?, content: @Composable ColumnScope.() -> Unit) {
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(background ?: GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

/** A note's colour as a widget background, switching with the phone's dark theme; null for no colour. */
fun noteBackground(color: NoteColor): ColorProvider? = noteColorsDayNight(color)?.let { (day, night) -> dayNight(day, night) }

/** A widget's title row: an optional [icon] and the [title], opening [onClick] when tapped. */
@Composable
fun WidgetHeader(title: String, onClick: Action, @DrawableRes icon: Int? = null) {
    Row(
        GlanceModifier.fillMaxWidth().padding(bottom = 6.dp).clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(ImageProvider(icon), null, GlanceModifier.size(18.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.primary))
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(
            title,
            GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

/** A line of explanation in the middle of a widget, with an optional [action] word under it; tapping anywhere runs [onClick]. */
@Composable
fun WidgetMessage(text: String, action: String?, onClick: Action) {
    Column(
        GlanceModifier.fillMaxSize().clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center),
        )
        if (action != null) {
            Spacer(GlanceModifier.height(8.dp))
            Text(
                action,
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
            )
        }
    }
}

/**
 * What a note or list widget [widgetId] shows when it has no note to show: no folder yet, no
 * note chosen yet, or the note is gone. [lists] words it for a list.
 */
@Composable
fun NoteProblem(widgetId: Int, shown: ShownNote, lists: Boolean) {
    val context = LocalContext.current
    val choose = actionStartActivity(Widgets.setupIntent(context, widgetId))
    when (shown) {
        ShownNote.NoFolder -> WidgetMessage(
            context.getString(R.string.widget_no_folder),
            context.getString(R.string.widget_open_app),
            actionStartActivity(Widgets.openAppIntent(context)),
        )
        ShownNote.NotChosen -> WidgetMessage(
            context.getString(if (lists) R.string.widget_choose_list_prompt else R.string.widget_choose_note_prompt),
            null,
            choose,
        )
        is ShownNote.Missing -> WidgetMessage(
            context.getString(R.string.widget_missing, shown.title),
            context.getString(R.string.widget_choose_another),
            choose,
        )
        is ShownNote.Ready -> {}
    }
}

package app.slate.notes.widgets

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.slate.notes.R

/**
 * The quick-start widget: a New note and a New list button, each opening the app on a fresh
 * note in Unsorted. Wide enough, the buttons are labelled; small, they're just icons, side by
 * side or stacked to fit.
 */
class NewNoteWidget : GlanceAppWidget() {
    // Exact sizes, so the layout can follow the widget as it's resized.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { GlanceTheme { NewNoteContent() } }
    }
}

/** The two buttons, laid out for the widget's size. */
@Composable
private fun NewNoteContent() {
    val context = LocalContext.current
    val size = LocalSize.current
    val labelled = size.width >= 160.dp
    val stacked = !labelled && size.height > size.width
    val note = actionStartActivity(Widgets.newNoteIntent(context, list = false))
    val list = actionStartActivity(Widgets.newNoteIntent(context, list = true))
    val noteLabel = context.getString(R.string.new_note)
    val listLabel = context.getString(R.string.new_list)
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .padding(6.dp),
    ) {
        if (stacked) {
            Column(GlanceModifier.fillMaxSize()) {
                NewButton(R.drawable.ic_widget_note, noteLabel, false, note, GlanceModifier.defaultWeight().fillMaxWidth())
                Spacer(GlanceModifier.height(6.dp))
                NewButton(R.drawable.ic_widget_list, listLabel, false, list, GlanceModifier.defaultWeight().fillMaxWidth())
            }
        } else {
            Row(GlanceModifier.fillMaxSize()) {
                NewButton(R.drawable.ic_widget_note, noteLabel, labelled, note, GlanceModifier.defaultWeight().fillMaxHeight())
                Spacer(GlanceModifier.width(6.dp))
                NewButton(R.drawable.ic_widget_list, listLabel, labelled, list, GlanceModifier.defaultWeight().fillMaxHeight())
            }
        }
    }
}

/** One button: an [icon] in a rounded tile, with its [label] beside it when [showLabel] is set. Tapping it runs [onClick]. */
@Composable
private fun NewButton(@DrawableRes icon: Int, label: String, showLabel: Boolean, onClick: Action, modifier: GlanceModifier) {
    Row(
        modifier.background(GlanceTheme.colors.primaryContainer).cornerRadius(20.dp).clickable(onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(ImageProvider(icon), label, GlanceModifier.size(24.dp), colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer))
        if (showLabel) {
            Spacer(GlanceModifier.width(8.dp))
            Text(
                label,
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
        }
    }
}

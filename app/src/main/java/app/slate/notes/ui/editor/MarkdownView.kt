package app.slate.notes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.slate.notes.data.Block
import app.slate.notes.data.InlineText
import app.slate.notes.data.Marker
import app.slate.notes.data.parseBlocks
import app.slate.notes.data.parseInline
import app.slate.notes.util.openLink
import app.slate.notes.data.SpanStyle as MarkStyle

/**
 * A note drawn formatted: headings, lists, checkboxes, quotes, code and links. Ticking a
 * checkbox calls [onToggleTask] with its line, so the tick is written into the file itself.
 */
@Composable
fun MarkdownView(source: String, onToggleTask: (line: Int) -> Unit, modifier: Modifier = Modifier) {
    val blocks = remember(source) { parseBlocks(source) }
    val typography = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            when (block) {
                is Block.Heading -> MarkText(
                    block.text,
                    when (block.level) {
                        1 -> typography.headlineMedium
                        2 -> typography.headlineSmall
                        3 -> typography.titleLarge
                        else -> typography.titleMedium
                    }.copy(fontWeight = FontWeight.SemiBold),
                    Modifier.padding(top = if (block.level <= 2) 12.dp else 6.dp),
                )
                is Block.Paragraph -> MarkText(block.text, typography.bodyLarge)
                is Block.Item -> ListItem(block, onToggleTask)
                is Block.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(scheme.primary, RoundedCornerShape(2.dp)))
                    Spacer(Modifier.width(12.dp))
                    MarkText(block.text, typography.bodyLarge.copy(fontStyle = FontStyle.Italic, color = scheme.onSurfaceVariant))
                }
                is Block.Code -> Surface(
                    color = scheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        block.text,
                        style = typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
                    )
                }
                is Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

/** One list entry: indented by its depth, with a bullet, a number or a checkbox. */
@Composable
private fun ListItem(item: Block.Item, onToggleTask: (Int) -> Unit) {
    val typography = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.padding(start = (item.depth * 20).dp), verticalAlignment = Alignment.Top) {
        when (val marker = item.marker) {
            is Marker.Task -> {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Checkbox(checked = marker.checked, onCheckedChange = { onToggleTask(item.line) })
                }
                Spacer(Modifier.width(8.dp))
                MarkText(
                    item.text,
                    if (marker.checked) {
                        typography.bodyLarge.copy(color = scheme.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
                    } else {
                        typography.bodyLarge
                    },
                    Modifier.padding(top = 2.dp),
                )
            }
            else -> {
                val label = if (marker is Marker.Number) "${marker.n}." else if (item.depth % 2 == 0) "•" else "◦"
                Text(
                    label,
                    style = typography.bodyLarge,
                    color = scheme.primary,
                    modifier = Modifier.widthIn(min = 20.dp),
                )
                Spacer(Modifier.width(8.dp))
                MarkText(item.text, typography.bodyLarge)
            }
        }
    }
}

/** A run of text with its inline marks (bold, italic, code, links) applied. Links open when tapped. */
@Composable
private fun MarkText(source: String, style: TextStyle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val text = remember(source, scheme) {
        styled(parseInline(source), linkColor = scheme.primary, codeBackground = scheme.surfaceContainerHighest) { url ->
            openLink(context, url)
        }
    }
    Text(text, style = style, modifier = modifier.fillMaxWidth())
}

/** Turns [inline] into styled text, with each link calling [onLink]. */
private fun styled(inline: InlineText, linkColor: Color, codeBackground: Color, onLink: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        append(inline.text)
        for (span in inline.spans) {
            when (val mark = span.style) {
                MarkStyle.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), span.start, span.end)
                MarkStyle.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), span.start, span.end)
                MarkStyle.Strike -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), span.start, span.end)
                MarkStyle.Code -> addStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground), span.start, span.end)
                is MarkStyle.Link -> addLink(
                    LinkAnnotation.Clickable(
                        tag = mark.url,
                        styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        linkInteractionListener = { onLink(mark.url) },
                    ),
                    span.start,
                    span.end,
                )
            }
        }
    }

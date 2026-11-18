package app.slate.notes.data

// A small Markdown reader for showing notes formatted. It covers what people write in notes
// (headings, lists, checkboxes, quotes, code, links, bold, italic, strikethrough) rather than
// all of CommonMark, and never changes the text: the file stays exactly as written.
// Kept free of Android so it can be unit-tested.

/** One piece of a note as the formatted view draws it. */
sealed interface Block {
    /** The line in the source (counting from 0) this block starts on. */
    val line: Int

    /** "# Title": [level] is the number of #s, 1 to 6. */
    data class Heading(val level: Int, val text: String, override val line: Int) : Block

    /** Ordinary text. Lines that follow each other stay on separate lines, as typed. */
    data class Paragraph(val text: String, override val line: Int) : Block

    /** One list entry. [depth] is how far it's indented (0 at the left). */
    data class Item(val marker: Marker, val text: String, val depth: Int, override val line: Int) : Block

    /** "> quoted" lines. */
    data class Quote(val text: String, override val line: Int) : Block

    /** A ``` fenced block, shown as written in a fixed-width font. */
    data class Code(val text: String, override val line: Int) : Block

    /** A "---" line across the page. */
    data class Rule(override val line: Int) : Block

    /** A line that's only a picture: "![description](attachments/photo.jpg)". */
    data class Image(val image: ImageRef, override val line: Int) : Block
}

/** What starts a list entry. */
sealed interface Marker {
    /** "- ", "* " or "+ ". */
    data object Bullet : Marker

    /** "1. " or "1) ". */
    data class Number(val n: Int) : Marker

    /** "- [ ] " or "- [x] ": a checkbox the user can tick in the formatted view. */
    data class Task(val checked: Boolean) : Marker
}

private val HEADING = Regex("^(#{1,6})\\s+(.*?)(?:\\s+#+)?\\s*$")
private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
private val TASK = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s?(.*)$")
private val BULLET = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val NUMBERED = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
private val QUOTE = Regex("^\\s{0,3}>\\s?(.*)$")
private val FENCE = Regex("^\\s{0,3}(`{3,}|~{3,}).*$")

/** How deep a list entry is from its leading spaces (a tab counts as four). */
private fun depthOf(indent: String): Int {
    val spaces = indent.sumOf { if (it == '\t') 4 else 1 }
    return (spaces / 2).coerceAtMost(6)
}

/**
 * Where the note's body starts, past YAML front matter ("---" … "---" at the very top, as
 * Obsidian and static-site tools write it). Returns 0 when there is none.
 */
fun bodyStartLine(lines: List<String>): Int {
    if (lines.isEmpty() || lines[0].trim() != "---") return 0
    for (i in 1 until lines.size) {
        if (lines[i].trim() == "---" || lines[i].trim() == "...") return i + 1
    }
    return 0
}

/** Splits [source] into blocks to draw, skipping any front matter. */
fun parseBlocks(source: String): List<Block> {
    val lines = source.split('\n').map { it.removeSuffix("\r") }
    val blocks = mutableListOf<Block>()
    var i = bodyStartLine(lines)
    val paragraph = StringBuilder()
    var paragraphLine = 0
    val quote = StringBuilder()
    var quoteLine = 0

    fun flush() {
        if (paragraph.isNotEmpty()) blocks += Block.Paragraph(paragraph.toString(), paragraphLine)
        if (quote.isNotEmpty()) blocks += Block.Quote(quote.toString(), quoteLine)
        paragraph.clear()
        quote.clear()
    }

    while (i < lines.size) {
        val line = lines[i]
        val fence = FENCE.matchEntire(line)
        if (fence != null) {
            flush()
            val marker = fence.groupValues[1]
            val start = i
            val code = mutableListOf<String>()
            i++
            // An unclosed fence runs to the end of the note, as in other Markdown apps.
            while (i < lines.size && !lines[i].trimStart().startsWith(marker)) {
                code += lines[i]
                i++
            }
            blocks += Block.Code(code.joinToString("\n"), start)
            i++
            continue
        }
        if (line.isBlank()) {
            flush()
            i++
            continue
        }
        val quoted = QUOTE.matchEntire(line)
        if (quoted != null) {
            if (paragraph.isNotEmpty()) flush()
            if (quote.isEmpty()) quoteLine = i else quote.append('\n')
            quote.append(quoted.groupValues[1])
            i++
            continue
        }
        val block: Block? = imageOf(line)?.let { Block.Image(it, i) }
            ?: HEADING.matchEntire(line)?.let { Block.Heading(it.groupValues[1].length, it.groupValues[2], i) }
            ?: RULE.matchEntire(line)?.let { Block.Rule(i) }
            ?: TASK.matchEntire(line)?.let {
                Block.Item(Marker.Task(it.groupValues[2] != " "), it.groupValues[3], depthOf(it.groupValues[1]), i)
            }
            ?: BULLET.matchEntire(line)?.let { Block.Item(Marker.Bullet, it.groupValues[2], depthOf(it.groupValues[1]), i) }
            ?: NUMBERED.matchEntire(line)?.let {
                Block.Item(Marker.Number(it.groupValues[2].toInt()), it.groupValues[3], depthOf(it.groupValues[1]), i)
            }
        if (block != null) {
            flush()
            blocks += block
        } else {
            if (quote.isNotEmpty()) flush()
            if (paragraph.isEmpty()) paragraphLine = i else paragraph.append('\n')
            paragraph.append(line.trim())
        }
        i++
    }
    flush()
    return blocks
}

/** A styled stretch of [InlineText.text], from [start] up to (not including) [end]. */
data class Span(val start: Int, val end: Int, val style: SpanStyle)

/** How a [Span] looks. */
sealed interface SpanStyle {
    /** **bold** or __bold__. */
    data object Bold : SpanStyle

    /** *italic* or _italic_. */
    data object Italic : SpanStyle

    /** ~~struck through~~. */
    data object Strike : SpanStyle

    /** `code`. */
    data object Code : SpanStyle

    /** [text](url), or a bare http(s) address. */
    data class Link(val url: String) : SpanStyle
}

/** A line of text with the Markdown marks taken out, and where each style applies. */
data class InlineText(val text: String, val spans: List<Span>)

/**
 * Reads the inline marks in [source]: the result's text has the marks removed, and its spans
 * say which parts are bold, italic and so on. Unmatched marks are kept as typed.
 */
fun parseInline(source: String): InlineText {
    val out = StringBuilder()
    val spans = mutableListOf<Span>()

    /** Adds [inner], parsed in turn, wrapped in [style]. */
    fun nest(inner: String, style: SpanStyle) {
        val parsed = parseInline(inner)
        val start = out.length
        out.append(parsed.text)
        parsed.spans.forEach { spans += Span(it.start + start, it.end + start, it.style) }
        spans += Span(start, out.length, style)
    }

    var i = 0
    while (i < source.length) {
        val c = source[i]
        when {
            c == '\\' && i + 1 < source.length && !source[i + 1].isLetterOrDigit() -> {
                out.append(source[i + 1])
                i += 2
                continue
            }
            c == '`' -> {
                val close = source.indexOf('`', i + 1)
                if (close > i + 1) {
                    val start = out.length
                    out.append(source, i + 1, close)
                    spans += Span(start, out.length, SpanStyle.Code)
                    i = close + 1
                    continue
                }
            }
            source.startsWith("**", i) || source.startsWith("__", i) -> {
                val mark = source.substring(i, i + 2)
                var close = source.indexOf(mark, i + 2)
                // "***" closing both an italic and the bold: the bold takes the last two.
                while (close != -1 && close + 2 < source.length && source[close + 2] == mark[0]) close++
                if (close > i + 2) {
                    nest(source.substring(i + 2, close), SpanStyle.Bold)
                    i = close + 2
                    continue
                }
            }
            source.startsWith("~~", i) -> {
                val close = source.indexOf("~~", i + 2)
                if (close > i + 2) {
                    nest(source.substring(i + 2, close), SpanStyle.Strike)
                    i = close + 2
                    continue
                }
            }
            (c == '*' || c == '_') && i + 1 < source.length && !source[i + 1].isWhitespace() &&
                // snake_case words aren't italic: _ only opens at the start of a word.
                (c == '*' || i == 0 || !source[i - 1].isLetterOrDigit()) -> {
                var close = source.indexOf(c, i + 1)
                // Skip a doubled mark (the start of a bold run inside the italic).
                while (close != -1 && close + 1 < source.length && source[close + 1] == c) close = source.indexOf(c, close + 2)
                if (close > i + 1 && !source[close - 1].isWhitespace() &&
                    (c == '*' || close + 1 == source.length || !source[close + 1].isLetterOrDigit())
                ) {
                    nest(source.substring(i + 1, close), SpanStyle.Italic)
                    i = close + 1
                    continue
                }
            }
            c == '[' -> {
                val middle = source.indexOf("](", i + 1)
                val close = if (middle == -1) -1 else source.indexOf(')', middle + 2)
                if (middle > i && close > middle + 2 && source.indexOf('\n', i).let { it == -1 || it > close }) {
                    nest(source.substring(i + 1, middle), SpanStyle.Link(source.substring(middle + 2, close).trim()))
                    i = close + 1
                    continue
                }
            }
            source.startsWith("http://", i) || source.startsWith("https://", i) -> {
                var end = i
                while (end < source.length && !source[end].isWhitespace() && source[end] != '<' && source[end] != '>') end++
                // A sentence's closing punctuation isn't part of the address.
                while (end > i && source[end - 1] in ".,;:!?)'\"") end--
                val url = source.substring(i, end)
                val start = out.length
                out.append(url)
                spans += Span(start, out.length, SpanStyle.Link(url))
                i = end
                continue
            }
        }
        out.append(c)
        i++
    }
    return InlineText(out.toString(), spans)
}

/**
 * Ticks or unticks the checkbox on [line] of [source] ("- [ ]" ↔ "- [x]"), leaving every other
 * character as it was. Returns [source] unchanged if that line has no checkbox.
 */
fun toggleTask(source: String, line: Int): String {
    val lines = source.split('\n')
    if (line !in lines.indices) return source
    val match = TASK.matchEntire(lines[line].removeSuffix("\r")) ?: return source
    val box = lines[line].indexOf('[', match.groupValues[1].length)
    val flipped = if (match.groupValues[2] == " ") 'x' else ' '
    val changed = lines[line].substring(0, box + 1) + flipped + lines[line].substring(box + 2)
    return lines.toMutableList().also { it[line] = changed }.joinToString("\n")
}

/**
 * A short plain-text preview of a note for the lists: its first lines with the Markdown marks
 * removed, skipping front matter and a first heading that only repeats the [title].
 */
fun previewOf(source: String, title: String, maxLength: Int = 200): String {
    val parts = mutableListOf<String>()
    var length = 0
    for ((index, block) in parseBlocks(source).withIndex()) {
        val raw = when (block) {
            is Block.Heading -> if (index == 0 && block.text.trim().equals(title.trim(), ignoreCase = true)) null else block.text
            is Block.Paragraph -> block.text
            is Block.Item -> when (val m = block.marker) {
                is Marker.Task -> (if (m.checked) "☑ " else "☐ ") + block.text
                else -> block.text
            }
            is Block.Quote -> block.text
            is Block.Code -> block.text
            is Block.Rule, is Block.Image -> null
        } ?: continue
        val plain = raw.split('\n').joinToString(" ") { parseInline(it).text.trim() }.trim()
        if (plain.isEmpty()) continue
        parts += plain
        length += plain.length
        if (length >= maxLength) break
    }
    val joined = parts.joinToString(" · ")
    return if (joined.length > maxLength) joined.take(maxLength).trimEnd() + "…" else joined
}

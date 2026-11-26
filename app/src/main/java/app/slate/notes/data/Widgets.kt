package app.slate.notes.data

// What the home screen widgets need that doesn't touch Android: which note each widget shows,
// keeping that pointing at the right file when the app renames or moves it, the lines a note
// widget draws, and which reminders the reminders widget lists. Kept free of Android so it can
// be unit-tested.

/**
 * The note a widget shows: its document id, and its place in the notes folder ("Shopping/Milk.md").
 * The id is what's read; the path finds the note again when the id stops working, such as after
 * the trash puts it back where it was.
 */
data class WidgetNote(val noteId: String, val path: String) {
    /** The note's title, from the file name at the end of its path. */
    val title: String get() = titleFromFileName(path.substringAfterLast('/'))
}

/** The place of [note] in the notes folder, as a [WidgetNote] keeps it. */
fun widgetPathOf(note: Note): String = if (note.folder.isEmpty()) note.name else "${note.folder}/${note.name}"

/** [note] as a widget keeps it. */
fun widgetNoteOf(note: Note): WidgetNote = WidgetNote(note.id, widgetPathOf(note))

/** One widget's note as one line of text, to keep in the settings file. Document ids may contain tabs, so the id is last. */
fun encodeWidgetNote(widgetId: Int, note: WidgetNote): String =
    listOf(widgetId.toString(), note.path.replace('\t', ' ').replace('\n', ' '), note.noteId).joinToString("\t")

/** Reads a line written by [encodeWidgetNote]; null if it's damaged. */
fun decodeWidgetNote(line: String): Pair<Int, WidgetNote>? {
    val parts = line.split('\t', limit = 3)
    if (parts.size != 3) return null
    val id = parts[0].toIntOrNull() ?: return null
    return id to WidgetNote(parts[2], parts[1])
}

/**
 * [widgets] with every widget that showed the note [fromId] now showing [to] instead: what the
 * app does when it renames or moves a note, which gives it a new id and path.
 */
fun followNote(widgets: Map<Int, WidgetNote>, fromId: String, to: Note): Map<Int, WidgetNote> =
    widgets.mapValues { (_, shown) -> if (shown.noteId == fromId) widgetNoteOf(to) else shown }

/**
 * [widgets] with the paths of notes in the notebook [old] moved to [new], after the notebook is
 * renamed. Their ids are out of date then too, so the paths are what find them again.
 */
fun followNotebook(widgets: Map<Int, WidgetNote>, old: String, new: String): Map<Int, WidgetNote> =
    widgets.mapValues { (_, shown) ->
        val notebook = shown.path.substringBefore('/', "")
        if (notebook.isNotEmpty() && notebook.equals(old, ignoreCase = true)) shown.copy(path = new + "/" + shown.path.substringAfter('/')) else shown
    }

/**
 * The checklist in a whole note file [text], each item with the line it's on in the file
 * (front matter and top pictures counted), or null if the note isn't a checklist.
 */
fun fileChecklist(text: String): List<IndexedValue<CheckItem>>? {
    val rest = splitLeadingImages(splitFrontMatter(text).body).rest
    if (!isChecklist(rest)) return null
    // rest is always the end of text, so the lines before it are the ones it starts after.
    val offset = text.substring(0, text.length - rest.length).count { it == '\n' }
    return checklistLines(rest).map { IndexedValue(it.index + offset, it.value) }
}

/**
 * [text] with the checklist item on [line] ticked or unticked, changing only its tick box.
 * Returns null when that line no longer holds the item the widget showed ([itemText], ticked
 * or not as [checked]), because the file changed since: the widget then just shows it again.
 */
fun tickInFile(text: String, line: Int, itemText: String, checked: Boolean): String? {
    val item = fileChecklist(text)?.firstOrNull { it.index == line }?.value ?: return null
    if (item.text != itemText || item.checked != checked) return null
    return toggleTask(text, line)
}

/** How a line of a note widget is drawn. */
enum class WidgetLineStyle {
    /** A heading, drawn bold. */
    Heading,

    /** Ordinary text, a list entry or a quote. */
    Text,

    /** Fixed-width text from a code block. */
    Code,
}

/** One line of a note widget: plain [text] (Markdown marks removed) drawn in [style], indented by [depth]. */
data class WidgetLine(val text: String, val style: WidgetLineStyle, val depth: Int = 0)

/**
 * The lines a note widget shows for [text] (the note after its front matter and top pictures),
 * at most [max] of them. Widgets can't draw rich text, so bullets and tick boxes become
 * characters ("•", "☐", "☑") and bold, links and the like become plain words.
 */
fun widgetLines(text: String, max: Int = 60): List<WidgetLine> {
    val lines = mutableListOf<WidgetLine>()
    fun plain(raw: String) = parseInline(raw).text.trim()
    for (block in parseBlocks(text)) {
        if (lines.size >= max) break
        when (block) {
            is Block.Heading -> lines += WidgetLine(plain(block.text), WidgetLineStyle.Heading)
            is Block.Paragraph -> block.text.split('\n').mapTo(lines) { WidgetLine(plain(it), WidgetLineStyle.Text) }
            is Block.Item -> {
                val mark = when (val m = block.marker) {
                    is Marker.Bullet -> "•"
                    is Marker.Number -> "${m.n}."
                    is Marker.Task -> if (m.checked) "☑" else "☐"
                }
                lines += WidgetLine("$mark ${plain(block.text)}", WidgetLineStyle.Text, block.depth)
            }
            is Block.Quote -> block.text.split('\n').mapTo(lines) { WidgetLine("│ " + plain(it), WidgetLineStyle.Text) }
            is Block.Code -> block.text.split('\n').mapTo(lines) { WidgetLine(it, WidgetLineStyle.Code) }
            is Block.Rule, is Block.Image -> {}
        }
    }
    return lines.filter { it.text.isNotBlank() }.take(max)
}

/** The reminders the reminders widget lists from [scheduled]: those still to come at [now], soonest first, at most [max]. */
fun upcomingReminders(scheduled: List<ScheduledReminder>, now: Long, max: Int = 20): List<ScheduledReminder> =
    scheduled.filter { it.at > now }.distinctBy { it.noteId }.sortedBy { it.at }.take(max)

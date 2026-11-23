package app.slate.notes.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Pins, colours and reminders are kept in the note's own file, in the YAML
// "front matter" block at the top that Obsidian, Jekyll, Hugo and other Markdown tools use:
//
//     ---
//     pinned: true
//     color: yellow
//     tags: [work, ideas]
//     ---
//
// So they travel with the file wherever it's copied or synced. Only these keys are ever
// changed; anything else in the block is kept as written. Kept free of Android so it can be
// unit-tested.

/** A note's colour, as Google Keep offers them. [key] is what's written in the file. */
enum class NoteColor(val key: String) {
    Default(""),
    Red("red"),
    Orange("orange"),
    Yellow("yellow"),
    Green("green"),
    Teal("teal"),
    Blue("blue"),
    DarkBlue("darkblue"),
    Purple("purple"),
    Pink("pink"),
    Brown("brown"),
    Gray("gray");

    /** Looks up colours by the name written in a file. */
    companion object {
        /** The colour called [key] in a file, or [Default] for none or one this app doesn't know. */
        fun of(key: String?): NoteColor {
            val wanted = key?.trim()?.lowercase()?.replace(" ", "")?.replace("grey", "gray") ?: return Default
            return entries.firstOrNull { it != Default && it.key == wanted } ?: Default
        }
    }
}

/** What the front matter says about a note. */
data class NoteMeta(
    /** Shown at the top of the lists. */
    val pinned: Boolean = false,
    /** Archived by an earlier version of the app. No longer used, but kept in the file as written. */
    val archived: Boolean = false,
    val color: NoteColor = NoteColor.Default,
    /** Labels ("tags", as Obsidian writes them). No longer shown, but kept in the file as written. */
    val tags: List<String> = emptyList(),
    /** When to remind, in the phone's local time. */
    val reminder: LocalDateTime? = null,
)

/** A note's file split in two: the front matter block (or "") and everything after it. */
data class NoteParts(val front: String, val body: String)

/**
 * Splits [source] into its front matter and body, without changing a character:
 * front + body is always exactly [source]. A block that's opened but never closed isn't front
 * matter, so the whole file is the body.
 */
fun splitFrontMatter(source: String): NoteParts {
    val firstEnd = source.indexOf('\n')
    if (firstEnd == -1 || source.substring(0, firstEnd).trim() != "---") return NoteParts("", source)
    var pos = firstEnd + 1
    while (pos <= source.length) {
        val end = source.indexOf('\n', pos).let { if (it == -1) source.length else it }
        val line = source.substring(pos, end).trim()
        if (line == "---" || line == "...") {
            val cut = if (end < source.length) end + 1 else end
            return NoteParts(source.substring(0, cut), source.substring(cut))
        }
        if (end >= source.length) break
        pos = end + 1
    }
    return NoteParts("", source)
}

private val KEY_VALUE = Regex("^([A-Za-z_][\\w-]*)\\s*:\\s*(.*)$")
private val LIST_ENTRY = Regex("^\\s*-\\s+(.*)$")
private val OUR_KEYS = setOf("pinned", "archived", "color", "tags", "reminder")
private val REMINDER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

/** The lines between the front matter's opening and closing lines. */
private fun innerLines(front: String): List<String> {
    if (front.isEmpty()) return emptyList()
    val lines = front.split('\n').map { it.removeSuffix("\r") }
    val close = (1 until lines.size).firstOrNull { lines[it].trim() == "---" || lines[it].trim() == "..." } ?: lines.size
    return lines.subList(1, close)
}

private fun unquote(value: String): String {
    val v = value.trim()
    return when {
        v.length >= 2 && v.startsWith('"') && v.endsWith('"') -> v.substring(1, v.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        v.length >= 2 && v.startsWith('\'') && v.endsWith('\'') -> v.substring(1, v.length - 1).replace("''", "'")
        else -> v
    }
}

/** Splits a flow list's inside ("a, \"b, c\"") at commas that aren't in quotes. */
private fun splitFlow(inside: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var quote: Char? = null
    var i = 0
    while (i < inside.length) {
        val c = inside[i]
        when {
            quote == '"' && c == '\\' && i + 1 < inside.length -> {
                current.append(c).append(inside[i + 1])
                i++
            }
            quote != null -> {
                current.append(c)
                if (c == quote) quote = null
            }
            c == '"' || c == '\'' -> {
                quote = c
                current.append(c)
            }
            c == ',' -> {
                parts += current.toString()
                current.clear()
            }
            else -> current.append(c)
        }
        i++
    }
    parts += current.toString()
    return parts
}

private fun tagsFrom(value: String): List<String> {
    val v = value.trim()
    val raw = if (v.startsWith('[') && v.endsWith(']')) splitFlow(v.substring(1, v.length - 1)) else splitFlow(v)
    return raw.map { cleanLabel(unquote(it)) }.filter { it.isNotEmpty() }
}

/** A label as stored: trimmed, without a leading # (Obsidian writes tags both ways), spaces collapsed. */
fun cleanLabel(name: String): String = name.trim().trimStart('#').replace(Regex("\\s+"), " ").trim()

/** Reads what the front matter block [front] says. Unknown keys and bad values are ignored. */
fun readMeta(front: String): NoteMeta {
    var meta = NoteMeta()
    val tags = mutableListOf<String>()
    var inTagList = false
    for (line in innerLines(front)) {
        val entry = LIST_ENTRY.matchEntire(line)
        if (entry != null && inTagList) {
            cleanLabel(unquote(entry.groupValues[1])).takeIf { it.isNotEmpty() }?.let { tags += it }
            continue
        }
        val pair = KEY_VALUE.matchEntire(line)
        if (pair == null) {
            if (line.isNotBlank()) inTagList = false
            continue
        }
        val key = pair.groupValues[1].lowercase()
        val raw = pair.groupValues[2].trim()
        // " #" starts a YAML comment, except inside a list or quotes.
        val value = if (raw.startsWith('[') || raw.startsWith('"') || raw.startsWith('\'')) raw else raw.substringBefore(" #").trim()
        inTagList = key == "tags" && value.isEmpty()
        when (key) {
            "pinned" -> meta = meta.copy(pinned = isTrue(value))
            "archived" -> meta = meta.copy(archived = isTrue(value))
            "color" -> meta = meta.copy(color = NoteColor.of(unquote(value)))
            "tags" -> if (value.isNotEmpty()) tags += tagsFrom(value)
            "reminder" -> meta = meta.copy(reminder = parseReminder(unquote(value)))
        }
    }
    return meta.copy(tags = tags.distinctBy { it.lowercase() })
}

private fun isTrue(value: String) = unquote(value).lowercase() in setOf("true", "yes", "on")

/** Reads a reminder time written as 2026-11-18T09:00 (seconds and a space instead of T are fine too). */
fun parseReminder(value: String): LocalDateTime? = runCatching {
    LocalDateTime.parse(value.trim().replace(' ', 'T').let { if (it.length == 16) "$it:00" else it })
}.getOrNull()

/** How a reminder time is written into the file. */
fun formatReminder(time: LocalDateTime): String = time.format(REMINDER_FORMAT)

private fun quoteIfNeeded(tag: String): String =
    if (Regex("^[\\p{L}\\p{N}_][\\p{L}\\p{N} _/.-]*$").matches(tag) && !tag.endsWith(' ')) tag
    else "\"" + tag.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

/**
 * The front matter block [front] rewritten to say [meta]. The app's own keys are written at the
 * end, the rest kept as they were. Returns "" when nothing at all is left to say, so a note
 * that's unpinned and uncoloured loses the block it was given rather than keeping an empty one.
 */
fun writeMeta(front: String, meta: NoteMeta): String {
    val newline = if (front.contains("\r\n")) "\r\n" else "\n"
    val kept = mutableListOf<String>()
    var skippingTagList = false
    for (line in innerLines(front)) {
        if (skippingTagList && LIST_ENTRY.matches(line)) continue
        skippingTagList = false
        val pair = KEY_VALUE.matchEntire(line)
        if (pair != null && pair.groupValues[1].lowercase() in OUR_KEYS) {
            skippingTagList = pair.groupValues[1].lowercase() == "tags" && pair.groupValues[2].isBlank()
            continue
        }
        kept += line
    }
    while (kept.isNotEmpty() && kept.last().isBlank()) kept.removeAt(kept.lastIndex)
    val ours = buildList {
        if (meta.pinned) add("pinned: true")
        if (meta.archived) add("archived: true")
        if (meta.color != NoteColor.Default) add("color: ${meta.color.key}")
        if (meta.tags.isNotEmpty()) add("tags: [" + meta.tags.joinToString(", ", transform = ::quoteIfNeeded) + "]")
        meta.reminder?.let { add("reminder: ${formatReminder(it)}") }
    }
    if (kept.isEmpty() && ours.isEmpty()) return ""
    return "---$newline" + (kept + ours).joinToString(newline) + "$newline---$newline"
}

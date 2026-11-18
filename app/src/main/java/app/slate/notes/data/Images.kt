package app.slate.notes.data

import java.net.URLDecoder

// Pictures in notes. A picture is a Markdown image line, "![](attachments/photo.jpg)", pointing
// at an image file next to the note, so any Markdown tool shows it too. Pictures at the very top
// of a note are shown as a strip above the text, as in Google Keep, and new ones are added
// there. Kept free of Android so it can be unit-tested.

/** A picture in a note: its description and where the file is, as written. */
data class ImageRef(val alt: String, val path: String)

private val IMAGE_LINE = Regex("^\\s*!\\[([^\\]]*)]\\((<[^>]+>|[^)\\s]+)(?:\\s+\"[^\"]*\")?\\)\\s*$")

/** The folder, next to each note, where pictures added in the app are saved. */
const val ATTACHMENTS = "attachments"

/** The picture on [line] if the line is nothing but a Markdown image; null otherwise. */
fun imageOf(line: String): ImageRef? {
    val match = IMAGE_LINE.matchEntire(line.removeSuffix("\r")) ?: return null
    val raw = match.groupValues[2].removeSurrounding("<", ">")
    val path = runCatching { URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
    return ImageRef(match.groupValues[1], path)
}

/** A note's text split into the pictures at its top ([head], blank lines after them included) and the rest. */
data class LeadingImages(val head: String, val images: List<ImageRef>, val rest: String)

/** Splits off the run of picture lines (and blank lines) at the top of [text]. head + rest is always [text]. */
fun splitLeadingImages(text: String): LeadingImages {
    val images = mutableListOf<ImageRef>()
    var pos = 0
    while (pos < text.length) {
        val end = text.indexOf('\n', pos).let { if (it == -1) text.length else it }
        val line = text.substring(pos, end)
        if (line.isNotBlank()) images += imageOf(line) ?: break
        pos = if (end < text.length) end + 1 else end
    }
    if (images.isEmpty()) return LeadingImages("", emptyList(), text)
    return LeadingImages(text.substring(0, pos), images, text.substring(pos))
}

/** The Markdown line for a picture at [path]. Spaces are written as %20, as Markdown needs. */
fun imageLine(path: String): String = "![](" + path.replace("%", "%25").replace(" ", "%20") + ")"

/** [text] with a picture at [path] added after the pictures at its top (or as its first line). */
fun addLeadingImage(text: String, path: String): String {
    val split = splitLeadingImages(text)
    val line = imageLine(path)
    if (split.images.isEmpty()) return if (text.isBlank()) "$line\n" else "$line\n\n$text"
    // After the last picture line in the head, before the blank lines that end it.
    val head = split.head
    var lastEnd = 0
    var pos = 0
    while (pos < head.length) {
        val end = head.indexOf('\n', pos).let { if (it == -1) head.length else it }
        if (head.substring(pos, end).isNotBlank()) lastEnd = end
        pos = end + 1
    }
    return head.substring(0, lastEnd) + "\n" + line + head.substring(lastEnd).ifEmpty { "\n" } + split.rest
}

/** [text] without the picture at [path] from its top. When it was the last one, the blank lines after it go too. */
fun removeLeadingImage(text: String, path: String): String {
    val split = splitLeadingImages(text)
    if (split.images.none { it.path == path }) return text
    val lines = split.head.split('\n')
    val index = lines.indexOfFirst { imageOf(it)?.path == path }
    val remaining = lines.toMutableList().also { it.removeAt(index) }
    if (remaining.none { imageOf(it) != null }) return split.rest
    return remaining.joinToString("\n") + split.rest
}

/**
 * Where a picture written as [path] in a note in [noteFolder] is, relative to the notes folder
 * ("Work/attachments/a.jpg"). Null for web addresses and for paths that lead outside the
 * notes folder, which the app can't reach.
 */
fun resolveImagePath(noteFolder: String, path: String): String? {
    if (path.contains("://") || path.startsWith("data:")) return null
    val parts = mutableListOf<String>()
    if (!path.startsWith("/") && noteFolder.isNotEmpty()) parts += noteFolder.split('/')
    for (part in path.trimStart('/').split('/')) {
        when (part) {
            "", "." -> Unit
            ".." -> if (parts.isEmpty()) return null else parts.removeAt(parts.lastIndex)
            else -> parts += part
        }
    }
    return parts.joinToString("/").ifEmpty { null }
}

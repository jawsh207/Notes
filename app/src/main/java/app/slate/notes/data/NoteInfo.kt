package app.slate.notes.data

/**
 * What the lists show about a note, worked out from its text once when it's read: the front
 * matter, a preview, the checklist if it is one, and the first picture. Kept free of Android
 * so it can be unit-tested.
 */
data class NoteInfo(
    val meta: NoteMeta,
    /** A few lines of the text with the Markdown marks removed ("" for a checklist). */
    val preview: String,
    /** The items, when the note is a checklist. */
    val checklist: List<CheckItem>?,
    /** The first picture at the top of the note, if any. */
    val image: ImageRef?,
    /** The text after the front matter, which search looks through. */
    val body: String,
)

/** Works out a [NoteInfo] from a note's whole file [text]. */
fun noteInfoOf(text: String, title: String): NoteInfo {
    val parts = splitFrontMatter(text)
    val split = splitLeadingImages(parts.body)
    val list = if (isChecklist(split.rest)) parseChecklist(split.rest) else null
    return NoteInfo(
        meta = readMeta(parts.front),
        preview = if (list != null) "" else previewOf(split.rest, title),
        checklist = list,
        image = split.images.firstOrNull(),
        body = parts.body,
    )
}

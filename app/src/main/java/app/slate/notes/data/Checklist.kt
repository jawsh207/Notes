package app.slate.notes.data

// Checklist notes, as in Google Keep. A note is a checklist when every line of its text is a
// Markdown task ("- [ ] milk", "- [x] eggs"), which is how other Markdown tools write them too.
// Kept free of Android so it can be unit-tested.

/** One line of a checklist. [depth] is 1 for an item indented under the one above it. */
data class CheckItem(val text: String, val checked: Boolean, val depth: Int = 0)

private val ITEM = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s?(.*)$")
private val PLAIN_MARKER = Regex("^\\s*(?:[-*+]\\s+(?:\\[[ xX]]\\s?)?|\\d{1,9}[.)]\\s+)")

/** Whether [text] is a checklist: at least one task line, and nothing but task lines and blank lines. */
fun isChecklist(text: String): Boolean {
    val lines = text.split('\n').map { it.removeSuffix("\r") }.filter { it.isNotBlank() }
    return lines.isNotEmpty() && lines.all { ITEM.matches(it) }
}

/** The task lines of [text] as items. Other lines are skipped. */
fun parseChecklist(text: String): List<CheckItem> = text.split('\n').mapNotNull { line ->
    val match = ITEM.matchEntire(line.removeSuffix("\r")) ?: return@mapNotNull null
    val indent = match.groupValues[1].sumOf { if (it == '\t') 4 else 1 }
    CheckItem(match.groupValues[3].trimEnd(), match.groupValues[2] != " ", if (indent >= 2) 1 else 0)
}

/** [items] written as Markdown tasks, one per line, ending with a line break. */
fun checklistText(items: List<CheckItem>): String =
    if (items.isEmpty()) "" else items.joinToString("\n", postfix = "\n") {
        "  ".repeat(it.depth) + "- [" + (if (it.checked) "x" else " ") + "] " + it.text
    }

/**
 * Turns ordinary text into a checklist (Keep's "Show tick boxes"): every line that has words
 * becomes an unticked item, losing any bullet or number it had. Empty text becomes one empty item.
 */
fun textToChecklist(text: String): String {
    val items = text.split('\n').map { it.removeSuffix("\r") }.filter { it.isNotBlank() }.map { line ->
        val match = ITEM.matchEntire(line)
        if (match != null) CheckItem(match.groupValues[3].trimEnd(), match.groupValues[2] != " ")
        else CheckItem(line.replace(PLAIN_MARKER, "").trim(), false)
    }
    return checklistText(items.ifEmpty { listOf(CheckItem("", false)) })
}

/** Turns a checklist back into ordinary lines (Keep's "Hide tick boxes"), ticked items included. */
fun checklistToText(text: String): String {
    val items = parseChecklist(text).filter { it.text.isNotBlank() }
    return if (items.isEmpty()) "" else items.joinToString("\n", postfix = "\n") { it.text }
}

/** Whether a checklist has nothing written in it. */
fun isEmptyChecklist(items: List<CheckItem>): Boolean = items.all { it.text.isBlank() }

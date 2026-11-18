package app.slate.notes.data

/**
 * What to do when an open note's file changes on disk (edited on another device and synced in,
 * or changed by another app). Kept free of Android so it can be unit-tested.
 */
enum class DiskChange {
    /** The file already says what's on screen: nothing to do. */
    InSync,

    /** No unsaved typing here, so show the new text straight away. */
    Reload,

    /** Only the file's date moved; its words are what was last saved. Keep typing. */
    Unchanged,

    /** Both changed: the user picks which to keep, so nobody's writing is lost. */
    Conflict,
}

/**
 * Decides what to do with [disk], the file's new text, given [saved] (the text last known to be
 * in the file) and [screen] (what's being edited now).
 */
fun diskChange(disk: String, saved: String, screen: String): DiskChange = when {
    disk == screen -> DiskChange.InSync
    screen == saved -> DiskChange.Reload
    disk == saved -> DiskChange.Unchanged
    else -> DiskChange.Conflict
}

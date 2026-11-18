package app.slate.notes.data

/** A note for tests, with only the parts a test cares about filled in. */
fun testNote(name: String, modified: Long = 0, folder: String = "", id: String = "$folder/$name") =
    Note(id = id, name = name, parentId = "root", folder = folder, modified = modified, size = 0)

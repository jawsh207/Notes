package app.folio.notes.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncTest {
    @Test
    fun decisions() {
        assertEquals(DiskChange.InSync, diskChange(disk = "b", saved = "a", screen = "b"))
        assertEquals(DiskChange.Reload, diskChange(disk = "b", saved = "a", screen = "a"))
        assertEquals(DiskChange.Unchanged, diskChange(disk = "a", saved = "a", screen = "a typing"))
        assertEquals(DiskChange.Conflict, diskChange(disk = "b", saved = "a", screen = "a typing"))
    }
}

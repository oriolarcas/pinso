package cat.pinso

import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private val entries = listOf(
        Event(1, Bowl.DRY, Action.ADD, 100, added = 50125, note = "Dinner 🐈\n\"special\""),
        Event(2, Bowl.WET, Action.ADD, 100, added = 80000),
        Event(3, Bowl.DRY, Action.WEIGH, 101, measured = 20000),
        Event(4, Bowl.WET, Action.REPLACE, 102, measured = 30000, added = 90000),
        Event(5, Bowl.WET, Action.DISCARD, 103, measured = 10000))
    @Test fun roundTripPreservesAllFieldsAndBalances() {
        val restored = Backup.read(Backup.encode(entries).byteInputStream())
        assertEquals(entries, restored)
        assertEquals(Food.replay(entries), Food.replay(restored))
    }
    @Test fun emptyBackupIsValid() { assertEquals(emptyList<Event>(), Backup.decode(Backup.encode(emptyList()))) }
    @Test(expected = IllegalArgumentException::class) fun rejectUnknownVersion() { Backup.decode(Backup.encode(entries).replace("\"version\": 1", "\"version\": 2")) }
    @Test(expected = IllegalArgumentException::class) fun rejectDuplicateIds() { Backup.encode(entries + entries.first()) }
    @Test(expected = IllegalArgumentException::class) fun rejectImpossibleHistory() { Backup.encode(entries.drop(1)) }
    @Test(expected = IllegalArgumentException::class) fun rejectFractionalWeight() { Backup.decode(Backup.encode(entries).replace("50125", "50125.5")) }
    @Test(expected = IllegalArgumentException::class) fun rejectMalformedJson() { Backup.decode("not a backup") }
    @Test(expected = IllegalArgumentException::class) fun rejectOversizedFile() { Backup.read(ByteArray(Backup.MAX_BYTES + 1).inputStream()) }
}

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
    private val data = BackupData(entries, listOf(
        CatWeight(1, 100, WeightMethod.DIRECT, 4125, note = "Vet 🐈"),
        CatWeight(2, 200, WeightMethod.DIFFERENCE, 74400, 70000)))
    @Test fun roundTripPreservesAllFieldsAndBalances() {
        val restored = Backup.read(Backup.encode(data).byteInputStream())
        assertEquals(data, restored)
        assertEquals(Food.replay(entries), Food.replay(restored.events))
        assertEquals(listOf(4125L, 4400L), restored.weights.map { it.catGrams })
    }
    @Test fun emptyBackupIsValid() { val empty = BackupData(emptyList(), emptyList()); assertEquals(empty, Backup.decode(Backup.encode(empty))) }
    @Test(expected = IllegalArgumentException::class) fun rejectOldVersion() { Backup.decode(Backup.encode(data).replace("\"version\": 2", "\"version\": 1")) }
    @Test(expected = IllegalArgumentException::class) fun rejectDuplicateIds() { Backup.encode(data.copy(events = entries + entries.first())) }
    @Test(expected = IllegalArgumentException::class) fun rejectImpossibleHistory() { Backup.encode(data.copy(events = entries.drop(1))) }
    @Test(expected = IllegalArgumentException::class) fun rejectFractionalWeight() { Backup.decode(Backup.encode(data).replace("50125", "50125.5")) }
    @Test(expected = IllegalArgumentException::class) fun rejectDuplicateWeightIds() { Backup.encode(data.copy(weights = data.weights + data.weights.first())) }
    @Test(expected = IllegalArgumentException::class) fun rejectInvalidDifference() { Backup.decode(Backup.encode(data).replace("74400", "69000")) }
    @Test(expected = IllegalArgumentException::class) fun rejectMalformedJson() { Backup.decode("not a backup") }
    @Test(expected = IllegalArgumentException::class) fun rejectOversizedFile() { Backup.read(ByteArray(Backup.MAX_BYTES + 1).inputStream()) }
}

package cat.pinso

import org.junit.Assert.*
import org.junit.Test

class FoodTest {
    private fun event(action: Action, measured: Long = 0, added: Long = 0, bowl: Bowl = Bowl.DRY, time: Long = 1) = Event(bowl = bowl, action = action, time = time, measured = measured, added = added)
    @Test fun mealCycleSeparatesConsumptionAndWaste() {
        val results = Food.replay(listOf(event(Action.ADD, added = 100000), event(Action.WEIGH, measured = 70000, time = 2), event(Action.REPLACE, measured = 20000, added = 85000, time = 3), event(Action.DISCARD, measured = 5000, time = 4)))
        assertEquals(listOf(100000L, 70000L, 85000L, 0L), results.map { it.second.after })
        assertEquals(160000L, results.sumOf { it.second.eaten })
        assertEquals(25000L, results.sumOf { it.second.discarded })
    }
    @Test fun bowlsAreIndependent() {
        val results = Food.replay(listOf(event(Action.ADD, added = 40000), event(Action.ADD, added = 85000, bowl = Bowl.WET), event(Action.WEIGH, measured = 10000, time = 2)))
        assertEquals(30000L, results.last().second.eaten)
        assertEquals(85000L, results[1].second.after)
    }
    @Test fun backdatedEntriesReplayChronologically() {
        val results = Food.replay(listOf(event(Action.WEIGH, measured = 20000, time = 3), event(Action.ADD, added = 50000, time = 1), event(Action.ADD, added = 10000, time = 2)))
        assertEquals(40000L, results.last().second.eaten)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnloggedIncrease() { Food.apply(20000, event(Action.WEIGH, measured = 30000)) }
    @Test(expected = IllegalArgumentException::class) fun invalidHistoryCannotReplay() { Food.replay(listOf(event(Action.WEIGH, measured = 30000))) }
    @Test fun decimalInputIsExact() { assertEquals(1234L, Food.parse("1,234")); assertEquals("1.234", Food.grams(1234)); assertEquals("0", Food.grams(0)) }
    @Test(expected = IllegalArgumentException::class) fun rejectsNegativeWeight() { Food.parse("-1") }
    @Test(expected = IllegalArgumentException::class) fun rejectsExcessPrecision() { Food.parse("0.0001") }
    @Test(expected = IllegalArgumentException::class) fun rejectsZeroAddition() { Food.apply(0, event(Action.ADD)) }
}

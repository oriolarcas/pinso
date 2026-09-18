package cat.pinso

import org.junit.Assert.assertEquals
import org.junit.Test

class FoodGoalsTest {
    private val goals = FoodGoals(60000, 200000)
    @Test fun mixedFoodsCompleteOneDailyAllowance() {
        val p = goals.progress(42000, 60000)
        assertEquals(0.7, p.dry, 0.000001); assertEquals(0.3, p.wet, 0.000001)
        assertEquals(1.0, p.total, 0.000001); assertEquals(0.0, p.remainingBar, 0.000001)
    }
    @Test fun partialAndEmptyDaysLeaveUnfilledSpace() {
        assertEquals(1.0, goals.progress(0, 0).remainingBar, 0.000001)
        val p = goals.progress(15000, 50000)
        assertEquals(0.5, p.total, 0.000001); assertEquals(0.5, p.remainingBar, 0.000001)
    }
    @Test fun eitherFoodCanSupplyFullDay() {
        assertEquals(1.0, goals.progress(60000, 0).dryBar, 0.000001)
        assertEquals(1.0, goals.progress(0, 200000).wetBar, 0.000001)
    }
    @Test fun excessKeepsActualTotalAndBothColors() {
        val p = goals.progress(60000, 100000)
        assertEquals(1.5, p.total, 0.000001)
        assertEquals(2.0 / 3, p.dryBar, 0.000001); assertEquals(1.0 / 3, p.wetBar, 0.000001)
        assertEquals(0.0, p.remainingBar, 0.000001)
    }
    @Test fun onlyConsumptionCounts() {
        val rows = Food.replay(listOf(Event(1, Bowl.DRY, Action.ADD, 1, added = 60000),
            Event(2, Bowl.DRY, Action.REPLACE, 2, measured = 30000, added = 60000)))
        assertEquals(0.5, goals.progress(rows.sumOf { it.second.eaten }, 0).total, 0.000001)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectZeroGoal() { FoodGoals(0, 200000).validate() }
    @Test(expected = IllegalArgumentException::class) fun rejectNegativeGoal() { FoodGoals(60000, -1).validate() }
}

package cat.pinso

import java.math.BigDecimal
import java.math.RoundingMode

enum class Bowl(val title: String) { DRY("Dry food"), WET("Wet food") }
enum class Action(val title: String) { ADD("Add"), WEIGH("Weigh"), REPLACE("Replace"), DISCARD("Throw away") }
data class Event(val id: Long = 0, val bowl: Bowl, val action: Action, val time: Long,
                 val measured: Long = 0, val added: Long = 0, val note: String = "")
data class Result(val before: Long, val after: Long, val eaten: Long, val discarded: Long)

object Food {
    // Integer milligrams avoid accumulating floating-point rounding errors.
    fun parse(text: String): Long {
        val value = text.trim().replace(',', '.').toBigDecimalOrNull()
            ?: throw IllegalArgumentException("Enter a weight in grams.")
        require(value >= BigDecimal.ZERO && value <= BigDecimal("10000")) { "Enter between 0 and 10,000 g." }
        return try { value.movePointRight(3).setScale(0, RoundingMode.UNNECESSARY).longValueExact() }
        catch (_: ArithmeticException) { throw IllegalArgumentException("Use at most three decimal places.") }
    }
    fun grams(mg: Long): String = BigDecimal.valueOf(mg, 3).stripTrailingZeros().toPlainString()
    fun apply(before: Long, event: Event): Result {
        require(before >= 0 && event.measured >= 0 && event.added >= 0) { "Weights cannot be negative." }
        if (event.action == Action.ADD) {
            require(event.added > 0) { "Enter an amount greater than zero." }
            return Result(before, Math.addExact(before, event.added), 0, 0)
        }
        require(event.measured <= before) { "Measured food exceeds the tracked amount (${grams(before)} g). Log any missing addition first." }
        val discarded = if (event.action == Action.WEIGH) 0L else event.measured
        val after = when (event.action) {
            Action.WEIGH -> event.measured
            Action.REPLACE -> event.added
            else -> 0L
        }
        return Result(before, after, before - event.measured, discarded)
    }
    fun replay(events: List<Event>): List<Pair<Event, Result>> {
        val balances = Bowl.entries.associateWith { 0L }.toMutableMap()
        return events.sortedWith(compareBy<Event> { it.time }.thenBy { it.id }).map { event ->
            val result = apply(balances.getValue(event.bowl), event)
            balances[event.bowl] = result.after
            event to result
        }
    }
}

package cat.pinso

data class FoodGoals(val dryMg: Long, val wetMg: Long) {
    fun validate() {
        require(dryMg in 1..10_000_000L && wetMg in 1..10_000_000L) { "Each daily allowance must be greater than zero and at most 10,000 g." }
    }
    fun progress(dryEatenMg: Long, wetEatenMg: Long): GoalProgress {
        validate()
        require(dryEatenMg >= 0 && wetEatenMg >= 0)
        return GoalProgress(dryEatenMg.toDouble() / dryMg, wetEatenMg.toDouble() / wetMg)
    }
}

data class GoalProgress(val dry: Double, val wet: Double) {
    val total get() = dry + wet
    // Above 100%, keep both contributions visible in proportion and label the excess.
    val dryBar get() = dry / maxOf(1.0, total)
    val wetBar get() = wet / maxOf(1.0, total)
    val remainingBar get() = maxOf(0.0, 1.0 - total)
}

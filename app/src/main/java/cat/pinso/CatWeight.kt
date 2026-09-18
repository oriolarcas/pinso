package cat.pinso

enum class WeightMethod { DIRECT, DIFFERENCE }

// Inputs are stored as integer grams; the UI displays kilograms.
data class CatWeight(val id: Long = 0, val time: Long, val method: WeightMethod,
                    val firstGrams: Long, val personGrams: Long = 0, val note: String = "") {
    val catGrams: Long get() = if (method == WeightMethod.DIRECT) firstGrams else firstGrams - personGrams
    fun validate() {
        require(time >= 0) { "Invalid timestamp." }
        require(firstGrams in 1..500_000L && personGrams in 0..500_000L) { "Enter a weight greater than zero and no more than 500 kg." }
        if (method == WeightMethod.DIRECT) require(personGrams == 0L) { "Direct weighing needs only the cat’s weight." }
        else require(personGrams > 0 && firstGrams > personGrams) { "Your weight holding the cat must be greater than your weight alone." }
        require(catGrams > 0) { "The cat’s weight must be greater than zero." }
    }
}

data class BackupData(val events: List<Event>, val weights: List<CatWeight>)

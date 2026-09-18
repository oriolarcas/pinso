package cat.pinso

import org.junit.Assert.assertEquals
import org.junit.Test

class CatWeightTest {
    @Test fun directWeight() {
        val w = CatWeight(time = 1, method = WeightMethod.DIRECT, firstGrams = 4125)
        w.validate(); assertEquals(4125L, w.catGrams)
    }
    @Test fun differenceIsExact() {
        val w = CatWeight(time = 1, method = WeightMethod.DIFFERENCE, firstGrams = Food.parse("74,125"), personGrams = Food.parse("70.1"))
        w.validate(); assertEquals(4025L, w.catGrams); assertEquals("4.025", Food.grams(w.catGrams))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectReversedReadings() { CatWeight(time = 1, method = WeightMethod.DIFFERENCE, firstGrams = 70000, personGrams = 74000).validate() }
    @Test(expected = IllegalArgumentException::class) fun rejectEqualReadings() { CatWeight(time = 1, method = WeightMethod.DIFFERENCE, firstGrams = 70000, personGrams = 70000).validate() }
    @Test(expected = IllegalArgumentException::class) fun rejectZeroDirectWeight() { CatWeight(time = 1, method = WeightMethod.DIRECT, firstGrams = 0).validate() }
    @Test(expected = IllegalArgumentException::class) fun rejectMissingPerson() { CatWeight(time = 1, method = WeightMethod.DIFFERENCE, firstGrams = 74000).validate() }
}

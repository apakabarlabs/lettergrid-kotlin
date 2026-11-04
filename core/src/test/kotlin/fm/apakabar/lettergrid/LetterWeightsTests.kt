package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LetterWeightsTests {
    @ParameterizedTest
    @ValueSource(doubles = [Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN])
    fun nonFiniteWeightsAreRejected(weight: Double) {
        val failure = assertFailsWith<LetterWeights.Failure> { LetterWeights(mapOf("a" to weight)) }
        assertEquals(LetterWeights.Failure.NotFinite("a"), failure)
    }

    @Test
    fun keysEqualAfterUppercasingAreOneLetterWithTheirWeightsAdded() {
        val merged = LetterWeights(mapOf("a" to 1.0, "A" to 2.0, "b" to 3.0))
        assertEquals(2, merged.letterCount)
        assertEquals(LetterWeights(mapOf("a" to 3.0, "b" to 3.0)), merged)
    }

    @Test
    fun mergedKeysWhoseWeightsOverflowAreRejected() {
        val failure =
            assertFailsWith<LetterWeights.Failure> {
                LetterWeights(mapOf("A" to Double.MAX_VALUE, "a" to Double.MAX_VALUE))
            }
        assertEquals(LetterWeights.Failure.NotFinite("a"), failure)
    }

    @Test
    fun finiteWeightsWhoseSumOverflowsStillProduceEveryLetter() {
        val weights = LetterWeights(mapOf("a" to Double.MAX_VALUE, "b" to Double.MAX_VALUE, "c" to Double.MIN_VALUE))
        val ordered = weights.orderedLetters(SeededRandom(42uL))
        assertEquals(3, ordered.size)
        assertEquals(setOf("A", "B", "C"), ordered.toSet())
    }

    @Test
    fun neighbourPenaltiesThatUnderflowStillProduceEveryLetter() {
        val weights = LetterWeights(mapOf("a" to 1.0, "b" to Double.MIN_VALUE), sameNeighbour = Double.MIN_VALUE)
        val ordered = weights.orderedLetters(SeededRandom(42uL), neighbours = mapOf("A" to 8, "B" to 8))
        assertEquals(2, ordered.size)
        assertEquals(setOf("A", "B"), ordered.toSet())
        assertEquals("A", ordered.first())
    }

    @Test
    fun weightsThatNormaliseAlikeAreEqualAndDrawAlike() {
        for (item in cases().alike) {
            val left = LetterWeights(item.first)
            val right = LetterWeights(item.second)
            assertEquals(left, right, item.name)
            assertEquals(left.orderedLetters(SeededRandom(3uL)), right.orderedLetters(SeededRandom(3uL)), item.name)
        }
    }

    @Test
    fun firstDrawsFollowTheRecordedShares() {
        for (item in cases().shares) {
            val weights = LetterWeights(item.weights, item.sameNeighbour)
            val random = SeededRandom(item.seed.toULong())
            val hits = (0 until item.draws).count { weights.orderedLetters(random, item.neighbours).first() == item.letter }
            assertTrue(abs(hits.toDouble() / item.draws - item.share) < item.tolerance, item.name)
        }
    }

    private fun cases() = Fixtures.decode("letter-weights", WeightCases.serializer())

    @Serializable
    private data class WeightCases(
        val alike: List<AlikeWeights>,
        val shares: List<DrawShare>,
    )

    @Serializable
    private data class AlikeWeights(
        val name: String,
        val first: Map<String, Double>,
        val second: Map<String, Double>,
    )

    @Serializable
    private data class DrawShare(
        val name: String,
        val weights: Map<String, Double>,
        val sameNeighbour: Double,
        val neighbours: Map<String, Int>,
        val seed: Long,
        val draws: Int,
        val letter: String,
        val share: Double,
        val tolerance: Double,
    )
}

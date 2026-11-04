package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DirectionLimitTests {
    private val filler = LetterWeights(mapOf("q" to 1.0, "w" to 1.0))

    @Test
    fun aRejectedCompleteLayoutReleasesDirectionCapacity() {
        val words = listOf("CAT", "DOG")
        val packing =
            PathPacking(
                columns = 5,
                rows = 5,
                rules = listOf(WordRule(listOf(Direction.RIGHT, Direction.DOWN))),
                maxWordsPerDirection = 1,
                crossingRestarts = 1,
            )
        val initial = assertIs<PathPacking.Outcome.Placed>(packing.pack(words), "The unconstrained completion must fit")
        val rejected = initial.placements[0].cells
        val retrying =
            packing.copy(completes = { placements, _ ->
                if (placements[0].cells == rejected) PathPacking.Completion.DOES_NOT_FIT else PathPacking.Completion.FITS
            })
        val retried =
            assertIs<PathPacking.Outcome.Placed>(
                retrying.pack(words),
                "Backtracking must release the rejected layout's direction counts",
            )
        assertNotEquals(rejected, retried.placements[0].cells)
        assertEquals(setOf(Direction.RIGHT, Direction.DOWN), retried.placements.map { it.direction }.toSet())
    }

    @ParameterizedTest
    @MethodSource("directionsAndTurns")
    fun bentWordsCountTheirFirstSegment(
        direction: Direction,
        turns: Int,
    ) {
        val rules = listOf(WordRule(listOf(direction, direction.opposite), turns))
        val leading = List(2) { WordRule(listOf(direction), turns) }
        val words = listOf("PLANE", "ORBIT", "DUSKY")
        for (maximumRows in listOf(7, null)) {
            val generator =
                BoardGenerator(columns = 7, rows = 7, maxRows = maximumRows, rules = rules, filler = filler, maxWordsPerDirection = 2)
            generator.checkPlacement(words, leadingRules = leading)
            val request = generator.placementRequest(words, leading)
            for (seed in 0uL..2uL) {
                val placements = request.targets(seed).placements
                assertTrue(placements.take(2).all { it.direction == direction })
                assertEquals(direction.opposite, placements.last().direction)
                assertTrue(placements.all { it.turns == turns })
            }
        }
    }

    @Test
    fun straightInitializerEnforcesTheSameLimit() {
        val generator =
            BoardGenerator(
                columns = 7,
                rows = 7,
                maxRows = 7,
                directions = listOf(Direction.RIGHT, Direction.DOWN),
                filler = filler,
                maxWordsPerDirection = 1,
            )
        val words = listOf("CAT", "DOG", "SUN")
        assertFalse(generator.accepts(words))
        val failure = assertFailsWith<BoardGenerator.Failure> { generator.checkPlacement(words) }
        assertEquals(BoardGenerator.Failure.NoRoom(words, 7, 7), failure)
        val request = generator.request(listOf("CAT", "DOG"), firstDirection = Direction.RIGHT)
        for (seed in 0uL..2uL) {
            assertEquals(setOf(Direction.RIGHT, Direction.DOWN), request.board(seed).placements.map { it.direction }.toSet())
        }
    }

    @Test
    fun aLimitChangesRequestIdentityWithoutChangingUnlimitedDefaults() {
        val directions = listOf(Direction.RIGHT, Direction.DOWN)
        val unlimited = BoardGenerator(columns = 7, rows = 7, directions = directions, filler = filler)
        val explicitNil = BoardGenerator(columns = 7, rows = 7, directions = directions, filler = filler, maxWordsPerDirection = null)
        val limited = BoardGenerator(columns = 7, rows = 7, directions = directions, filler = filler, maxWordsPerDirection = 2)
        assertEquals(unlimited, explicitNil)
        assertNotEquals(unlimited, limited)
        val words = listOf("CAT", "DOG", "SUN")
        val original = unlimited.request(words)
        val same = explicitNil.request(words)
        val capped = limited.request(words)
        assertEquals(original, same)
        assertNotEquals(original, capped)
        for (seed in 0uL..2uL) assertEquals(original.board(seed), same.board(seed))
    }

    companion object {
        @JvmStatic
        fun directionsAndTurns(): List<Arguments> =
            Direction.entries.flatMap { direction -> (0..3).map { Arguments.of(direction, it) } }
    }
}

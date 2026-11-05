package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DiagonalTests {
    private val words = listOf("CAT", "DOG", "SUN")

    @Test
    fun aRequiredFirstDirectionKeepsWordOrderAndVerticalQuota() {
        val ways = listOf(Direction.RIGHT, Direction.DOWN, Direction.DOWN_RIGHT, Direction.DOWN_LEFT)
        val generator =
            BoardGenerator(
                size = BoardSize(7, 7),
                rules = listOf(WordRule.straight(ways)),
                filler = Filler(LetterWeights(mapOf("x" to 1.0, "y" to 1.0, "z" to 1.0, "q" to 1.0))),
                limits = PlacementLimits(downWords = 1..2),
            )
        for (direction in ways) {
            for (seed in 1uL..10uL) {
                val board = generator.board(words, seed, firstRule = WordRule.straight(listOf(direction)))
                assertEquals(words, board.placements.map { it.word })
                assertEquals(direction, board.placements.first().direction)
                assertTrue(board.placements.count { it.direction == Direction.DOWN } in 1..2)
            }
        }
    }

    @Test
    fun anImpossibleRequiredDirectionIsRefused() {
        val generator =
            BoardGenerator(
                size = BoardSize(3, 3),
                rules = listOf(WordRule.straight(listOf(Direction.RIGHT, Direction.DOWN, Direction.DOWN_RIGHT))),
                filler = Filler(LetterWeights(mapOf("x" to 1.0))),
                limits = PlacementLimits(downWords = 1..1),
            )
        for (direction in listOf(Direction.DOWN_RIGHT, Direction.DOWN_LEFT)) {
            assertFailsWith<BoardGenerator.Failure> {
                generator.board(
                    listOf("ELEPHANT", "CAT"),
                    1uL,
                    firstRule = WordRule.straight(listOf(direction)),
                )
            }
        }
    }

    @Test
    fun aBoardCanUseEitherDiagonalReadingEachWordOnce() {
        for (direction in listOf(Direction.DOWN_RIGHT, Direction.DOWN_LEFT)) {
            val generator =
                BoardGenerator(
                    size = BoardSize(7, 7),
                    rules = listOf(WordRule.straight(listOf(direction))),
                    filler = Filler(LetterWeights(mapOf("x" to 1.0, "q" to 1.0, "z" to 1.0))),
                )
            val board = generator.board(words, 8uL)
            assertTrue(board.placements.all { it.direction == direction })
            for (placement in board.placements) {
                assertEquals(placement.word, board.text(placement.cells))
                assertEquals(1, board.occurrences(placement.word, along = listOf(direction)))
            }
        }
    }

    @Test
    fun mixedDirectionsKeepTheVerticalQuotaAndPermitBothDiagonals() {
        val generator =
            BoardGenerator(
                size = BoardSize(7, 7),
                rules =
                    listOf(
                        WordRule.straight(
                            listOf(Direction.RIGHT, Direction.DOWN, Direction.DOWN_RIGHT, Direction.DOWN_LEFT),
                        ),
                    ),
                filler = Filler(LetterWeights(mapOf("x" to 1.0, "q" to 1.0, "z" to 1.0))),
                limits = PlacementLimits(downWords = 1..2),
            )
        val seen = mutableSetOf<Direction>()
        for (seed in 1uL..30uL) {
            val board = generator.board(words, seed)
            assertTrue(board.placements.count { it.direction == Direction.DOWN } in 1..2)
            seen += board.placements.map { it.direction }
        }
        assertTrue(Direction.DOWN_RIGHT in seen)
        assertTrue(Direction.DOWN_LEFT in seen)
    }

    @Test
    fun aWordTooWideForADiagonalFailsInsteadOfGrowingForever() {
        val generator =
            BoardGenerator(
                size = BoardSize(3, 3),
                rules = listOf(WordRule.straight(listOf(Direction.DOWN_LEFT))),
                filler = Filler(LetterWeights(mapOf("x" to 1.0))),
            )
        assertFailsWith<BoardGenerator.Failure> { generator.board(listOf("ELEPHANT"), 1uL) }
    }
}

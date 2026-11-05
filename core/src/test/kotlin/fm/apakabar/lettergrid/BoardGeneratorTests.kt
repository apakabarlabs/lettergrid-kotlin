package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BoardGeneratorTests {
    private val filler = LetterWeights(mapOf("a" to 8.0, "e" to 12.0, "t" to 9.0, "s" to 6.0, "q" to 0.1))
    private val words = listOf("house", "water", "cat", "tree", "plan")
    private val straight = listOf(Direction.RIGHT, Direction.DOWN)

    private fun generator(
        columns: Int = 7,
        rows: Int = 7,
    ) = BoardGenerator(
        size = BoardSize(columns, rows),
        rules = listOf(WordRule.straight(straight)),
        filler = Filler(filler),
    )

    @Test
    fun placesEveryWordExactlyOnce() {
        val board = generator().board(words, 42uL)
        for (word in words.map { it.uppercase() }) assertEquals(1, board.occurrences(word, along = straight))
        assertEquals(words.map { it.uppercase() }, board.placements.map { it.word })
        for (placement in board.placements) assertEquals(placement.word, board.text(placement.cells))
    }

    @Test
    fun fillsEveryCellWithALetter() {
        val board = generator().board(words, 7uL)
        assertEquals(7, board.rows)
        assertEquals(7, board.columns)
        assertTrue(board.letters.flatten().all { it.isLetterCell })
    }

    @Test
    fun theSameSeedGivesTheSameBoard() {
        assertEquals(generator().board(words, 99uL), generator().board(words, 99uL))
    }

    @Test
    fun aHeavilyWeightedFillerStillReadsEachWordOnce() {
        val directions = listOf(Direction.RIGHT, Direction.DOWN, Direction.DOWN_RIGHT, Direction.DOWN_LEFT)
        val generator =
            BoardGenerator(
                size = BoardSize(7, 15),
                rules = listOf(WordRule.straight(directions)),
                filler = Filler(LetterWeights(mapOf("a" to 1_000.0, "b" to 1.0))),
                limits = PlacementLimits(downWords = 1..2),
            )
        val words = listOf("PERSPECTIVE", "SUN", "MOON")
        val board = generator.board(words, 1uL)
        for (word in words) assertEquals(1, board.occurrences(word, along = directions))
        assertEquals(words, board.placements.map { it.word })
    }

    @Test
    fun moreWordsWiderThanTheBoardThanMayStandDownHaveNoRoom() {
        val generator =
            BoardGenerator(
                BoardSize(4, 4),
                listOf(WordRule.straight(straight)),
                Filler(filler),
                PlacementLimits(0..1),
            )
        assertFailsWith<BoardGenerator.Failure> { generator.board(listOf("elephant", "umbrella"), 1uL) }
    }

    @Test
    fun theWordsStandingDownStayWithinTheirRange() {
        for (range in listOf(0..1, 2..3, 3..5)) {
            val generator =
                BoardGenerator(
                    BoardSize(7, 7),
                    listOf(WordRule.straight(straight)),
                    Filler(filler),
                    PlacementLimits(range),
                )
            for (seed in 1uL..20uL) {
                val board = generator.board(listOf("cat", "dog", "sun", "pen", "map"), seed)
                assertTrue(board.placements.count { it.direction == Direction.DOWN } in range)
            }
        }
    }

    @Test
    fun anImpossibleMinimumVerticalQuotaFails() {
        for (firstDirection in listOf(null, Direction.RIGHT)) {
            val generator =
                BoardGenerator(
                    size = BoardSize(3, 3),
                    rules = listOf(WordRule.straight(straight)),
                    filler = Filler(filler),
                    limits = PlacementLimits(downWords = 2..2),
                )
            assertFailsWith<BoardGenerator.Failure> {
                generator.board(listOf("CAT"), 1uL, firstDirection?.let { WordRule.straight(listOf(it)) })
            }
        }
    }

    @Test
    fun repeatedTargetsAreRejectedAfterCaseNormalization() {
        for (firstDirection in listOf(null, Direction.RIGHT)) {
            assertFailsWith<BoardGenerator.Failure> {
                generator().board(listOf("cat", "CAT"), 1uL, firstDirection?.let { WordRule.straight(listOf(it)) })
            }
        }
    }

    @Test
    fun anEmptyTargetIsRejectedAndAOneLetterTargetTakesOneCell() {
        assertFailsWith<BoardGenerator.Failure> { generator().board(listOf(""), 1uL) }
        val board = generator().board(listOf("A"), 1uL)
        assertEquals(listOf(1), board.placements.map { it.cells.size })
        assertEquals(1, board.letters.flatten().count { it == "A" })
    }

    @Test
    fun aWordWiderThanTheBoardStandsDownAndStretchesIt() {
        val board = generator(columns = 4, rows = 4).board(listOf("elephant", "cat"), 1uL)
        assertEquals(Direction.DOWN, board.placements.first { it.word == "ELEPHANT" }.direction)
        assertTrue(board.rows >= 8)
        assertEquals(4, board.columns)
    }

    @Test
    fun wordsTooWideToLieBesideTheStandingOnesGetRowsOfTheirOwn() {
        val generator =
            BoardGenerator(
                BoardSize(7, 7),
                listOf(WordRule.straight(straight)),
                Filler(filler),
                PlacementLimits(2..2),
            )
        val words = listOf("breakfast", "telephone", "kitchen", "freedom", "weather")
        for (seed in 1uL..10uL) {
            val board = generator.board(words, seed)
            assertEquals(words.size, board.placements.size)
            assertEquals(2, board.placements.count { it.direction == Direction.DOWN })
        }
    }

    @Test
    fun aWordLyingAcrossOftenCrossesOneStandingDown() {
        val generator =
            BoardGenerator(
                BoardSize(7, 7),
                listOf(WordRule.straight(straight)),
                Filler(filler),
                PlacementLimits(2..2),
            )
        val crossed =
            (1uL..20uL).filter { seed ->
                val cells =
                    generator
                        .board(
                            listOf("stone", "notes", "tones", "onset"),
                            seed,
                        ).placements
                        .flatMap { it.cells }
                cells.toSet().size < cells.size
            }
        assertTrue(crossed.isNotEmpty())
    }
}

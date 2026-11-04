package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BoundedHeightTests {
    private fun filler() = LetterWeights(mapOf("x" to 1.0, "q" to 2.0, "z" to 3.0))

    @ParameterizedTest
    @ValueSource(ints = [-1, 0, 4])
    fun aMaximumBelowTheMinimumIsRejected(maximum: Int) {
        val generator =
            BoardGenerator(
                columns = 5,
                rows = 5,
                maxRows = maximum,
                directions = listOf(Direction.RIGHT),
                filler = filler(),
            )
        val failure = assertFailsWith<BoardGenerator.Failure> { generator.request(listOf("CAT")) }
        assertEquals(BoardGenerator.Failure.InvalidConfiguration, failure)
    }

    @Test
    fun impossiblePackingFailsBeforeAPlayableRequestExists() {
        val generator =
            BoardGenerator(columns = 3, rows = 1, maxRows = 1, directions = listOf(Direction.RIGHT), filler = filler())
        val failure = assertFailsWith<BoardGenerator.Failure> { generator.request(listOf("CAT", "DOG")) }
        assertEquals(BoardGenerator.Failure.NoRoom(listOf("CAT", "DOG"), 3, 1), failure)
        val unrestricted =
            BoardGenerator(columns = 3, rows = 1, directions = listOf(Direction.RIGHT), filler = filler())
        assertTrue(unrestricted.request(listOf("CAT", "DOG")).board(1uL).rows > 1)
    }

    @Test
    fun aHeightRangeCanFitAWordTallerThanItsMinimum() {
        val request =
            BoardGenerator(
                columns = 3,
                rows = 2,
                maxRows = 5,
                directions = listOf(Direction.DOWN),
                filler = filler(),
                downWords = 1..1,
            ).request(listOf("CAT"))
        for (seed in 0uL until 12uL) {
            val board = request.board(seed)
            assertTrue(board.rows in 2..5)
            assertEquals(3, board.columns)
            assertEquals(1, board.occurrences("CAT", along = listOf(Direction.DOWN)))
        }
    }

    @Test
    fun anIntroductionTriesItsOtherAllowedDirectionWithinTheBounds() {
        val rule = WordRule(listOf(Direction.DOWN, Direction.RIGHT))
        val request =
            BoardGenerator(columns = 6, rows = 2, maxRows = 2, rules = listOf(rule), filler = filler())
                .request(listOf("ABCDEF"), firstRule = rule)
        val board = request.board(1uL)
        assertEquals(2, board.rows)
        assertEquals(Direction.RIGHT, board.placements.first().direction)
        assertTrue(rule.matches(board.placements[0]))
    }

    @Test
    fun theArmenianThirteenthLevelKeepsItsWordsAndSquare() {
        val groups =
            listOf(
                listOf(Direction.RIGHT, Direction.DOWN),
                listOf(Direction.DOWN_RIGHT),
                listOf(Direction.DOWN_LEFT),
                listOf(Direction.LEFT),
                listOf(Direction.UP),
                listOf(Direction.UP_LEFT),
                listOf(Direction.UP_RIGHT),
            )
        val rules = groups.map { WordRule(it) }
        val introduced = WordRule(listOf(Direction.UP_RIGHT))
        val words = listOf("ՊԱՏ", "ԿԾԵԼ", "ԹԱՌՄԱ")
        val request =
            BoardGenerator(
                columns = 7,
                rows = 7,
                maxRows = 7,
                rules = rules,
                filler = LetterWeights(listOf("ա", "ե", "ի", "ո", "ւ", "ք").associateWith { 1.0 }),
                downWords = 1..2,
            ).request(words, firstRule = introduced)
        for (seed in 0uL until 16uL) {
            val board = request.board(seed)
            check(board, words, rules, 1..2)
            assertTrue(introduced.matches(board.placements[0]))
        }
    }

    @ParameterizedTest
    @MethodSource(TestArguments.DIRECTIONS_AND_TURNS)
    fun boundedGenerationHonoursEveryStartAndExactTurnCount(
        direction: Direction,
        turns: Int,
    ) {
        val rule = WordRule(listOf(direction), turns)
        val request =
            BoardGenerator(columns = 7, rows = 7, maxRows = 7, rules = listOf(rule), filler = filler())
                .request(listOf("ABCDEF"), firstRule = rule)
        for (seed in 0uL until 4uL) {
            val board = request.board(seed)
            assertEquals(7, board.columns)
            assertEquals(7, board.rows)
            assertTrue(rule.matches(board.placements[0]))
            assertEquals(1, board.occurrences("ABCDEF", rules = listOf(rule)))
        }
    }

    @Test
    fun cumulativeRulesKeepTheirQuotaAndIntroduction() {
        val rules = (0..3).map { WordRule(Direction.entries, it) }
        val introduced = WordRule(listOf(Direction.UP_RIGHT), 3)
        val available = rules + introduced
        val words = listOf("PLANET", "ORBIT", "SUN")
        val request =
            BoardGenerator(columns = 7, rows = 7, maxRows = 7, rules = available, filler = filler(), downWords = 1..2)
                .request(words, firstRule = introduced)
        for (seed in 0uL until 12uL) {
            val board = request.board(seed)
            check(board, words, available, 1..2)
            assertTrue(introduced.matches(board.placements[0]))
        }
    }

    @Test
    fun heightPruningUsesTheSpanBeforeNormalizingUpwardPaths() {
        val rule = WordRule(listOf(Direction.UP), 2)
        val paths = PathShapes(columns = 5, length = 6, rule = rule, maximumShapes = 16, maximumRows = 3).cells(9uL)
        assertTrue(paths.isNotEmpty())
        for (cells in paths) {
            assertTrue(rule.matches(cells))
            assertTrue(cells.all { it.row in 0 until 3 && it.column in 0 until 5 })
        }
    }

    @Test
    fun theOptionalMaximumIsPartOfRequestIdentity() {
        val straight = listOf(Direction.RIGHT, Direction.DOWN)
        val unrestricted = BoardGenerator(columns = 7, rows = 7, directions = straight, filler = filler())
        val explicitNil =
            BoardGenerator(columns = 7, rows = 7, maxRows = null, directions = straight, filler = filler())
        val bounded = BoardGenerator(columns = 7, rows = 7, maxRows = 7, directions = straight, filler = filler())
        assertEquals(unrestricted, explicitNil)
        assertNotEquals(unrestricted, bounded)
        assertEquals(
            unrestricted.request(listOf("CAT", "DOG")).board(8uL),
            explicitNil.request(listOf("CAT", "DOG")).board(8uL),
        )
    }

    private fun check(
        board: Board,
        words: List<String>,
        rules: List<WordRule>,
        quota: IntRange,
    ) {
        assertEquals(7, board.columns)
        assertEquals(7, board.rows)
        assertEquals(words, board.placements.map { it.word })
        assertTrue(board.placements.count { it.direction.isVertical } in quota)
        for (placement in board.placements) {
            assertEquals(placement.word, board.text(placement.cells))
            assertTrue(rules.any { it.matches(placement) })
            assertEquals(1, board.occurrences(placement.word, rules = rules))
        }
    }
}

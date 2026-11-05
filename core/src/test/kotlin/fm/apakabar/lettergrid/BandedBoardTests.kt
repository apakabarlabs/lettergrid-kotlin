package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BandedBoardTests {
    private val alphabet = "ABCDEFGH".cells
    private val directionSets =
        listOf(
            listOf(Direction.RIGHT),
            listOf(Direction.DOWN),
            listOf(Direction.RIGHT, Direction.DOWN),
            listOf(Direction.RIGHT, Direction.DOWN, Direction.DOWN_RIGHT),
            listOf(Direction.RIGHT, Direction.DOWN, Direction.DOWN_RIGHT, Direction.DOWN_LEFT),
            listOf(Direction.DOWN_RIGHT, Direction.DOWN_LEFT),
            listOf(Direction.LEFT),
            listOf(Direction.UP),
            listOf(Direction.RIGHT, Direction.LEFT),
            listOf(Direction.DOWN, Direction.UP),
            listOf(Direction.UP_LEFT, Direction.UP_RIGHT),
            Direction.entries,
        )

    private fun generator(
        directions: List<Direction>,
        downWords: IntRange,
        maxRows: Int? = null,
    ): BoardGenerator {
        val weights = alphabet.associateWith { 1.0 } + ("z" to 0.5)
        return BoardGenerator(
            size = BoardSize(7, 7, maxRows),
            rules = listOf(WordRule.straight(directions)),
            filler = Filler(LetterWeights(weights)),
            limits = PlacementLimits(downWords = downWords),
        )
    }

    private fun words(random: SeededRandom): List<String> =
        (0 until random.nextInt(1..3)).map {
            (0 until random.nextInt(2..6)).joinToString("") { random.pick(alphabet) ?: "A" }
        }

    private fun SeededRandom.nextBoolean(): Boolean = (next() shr 17) and 1uL == 0uL

    @Test
    fun everyAcceptedRequestGetsABoardWithEachWordExactlyOnce() {
        val random = SeededRandom(2026uL)
        var built = 0
        for (directions in directionSets) {
            for (downWords in listOf(0..2, 1..2)) {
                val generator = generator(directions, downWords)
                repeat(150) {
                    val words = words(random)
                    val first = if (random.nextBoolean()) null else random.pick(directions)
                    val request =
                        try {
                            generator.request(words, firstRule = first?.let { WordRule.straight(listOf(it)) })
                        } catch (_: BoardGenerator.Failure) {
                            return@repeat
                        }
                    val board = request.board(random.next())
                    built += 1
                    assertEquals(7, board.columns)
                    assertTrue(board.rows >= 7)
                    assertEquals(request.words, board.placements.map { it.word })
                    for (word in request.words) {
                        assertEquals(
                            1,
                            board.occurrences(word, along = directions),
                            "${request.words}",
                        )
                    }
                    for (placement in board.placements) {
                        assertTrue(placement.direction in directions)
                        assertEquals(placement.word, board.text(placement.cells))
                    }
                    if (first != null) assertEquals(first, board.placements.first().direction)
                    assertTrue(
                        board.placements.count { it.direction.isVertical } in downWords ||
                            directions.none { it.isVertical },
                    )
                }
            }
        }
        assertTrue(built > 500)
    }

    @Test
    fun everyAcceptedBoundedRequestKeepsItsWordsUniqueInsideTheSquare() {
        val random = SeededRandom(2027uL)
        var built = 0
        for (directions in directionSets) {
            for (downWords in listOf(0..2, 1..2)) {
                val generator = generator(directions, downWords, maxRows = 7)
                repeat(30) {
                    val words = words(random)
                    val first = random.pick(directions)
                    val request =
                        try {
                            generator.request(words, firstRule = first?.let { WordRule.straight(listOf(it)) })
                        } catch (_: BoardGenerator.Failure.NoRoom) {
                            return@repeat
                        }
                    val board = request.board(random.next())
                    built += 1
                    assertTrue(board.columns == 7 && board.rows == 7)
                    assertEquals(request.words, board.placements.map { it.word })
                    for (word in request.words) assertEquals(1, board.occurrences(word, along = directions))
                    assertTrue(board.placements.all { it.direction in directions })
                    assertEquals(first, board.placements.first().direction)
                    assertTrue(board.placements.count { it.direction.isVertical } in downWords)
                }
            }
        }
        assertTrue(built > 100)
    }

    @Test
    fun downWordsStandSideBySideSoTheBoardIsOnlyAsTallAsTheLongest() {
        val directions = listOf(Direction.RIGHT, Direction.DOWN, Direction.DOWN_RIGHT)
        val request =
            generator(
                directions,
                2..2,
            ).request(listOf("ABCDEF", "BADCFE", "CAFEBD"), firstRule = WordRule.straight(listOf(Direction.DOWN_RIGHT)))
        val board = request.board(7uL)
        assertTrue(board.rows <= 13)
        assertEquals(2, board.placements.count { it.direction == Direction.DOWN })
        for (word in request.words) assertEquals(1, board.occurrences(word, along = directions))
    }

    @Test
    fun aWordInsideAnotherIsRejectedBecauseItWouldReadTwice() {
        val generator = generator(listOf(Direction.RIGHT, Direction.DOWN), 0..2)
        assertFailsWith<BoardGenerator.Failure> { generator.request(listOf("CAB", "CABBA")) }
    }

    @Test
    fun wordsThatUseEveryFillerLetterAreRejected() {
        val generator =
            BoardGenerator(
                size = BoardSize(7, 7),
                rules = listOf(WordRule.straight(listOf(Direction.RIGHT, Direction.DOWN))),
                filler = Filler(LetterWeights(mapOf("a" to 1.0, "b" to 1.0))),
            )
        assertFailsWith<BoardGenerator.Failure> { generator.request(listOf("AB", "BBA")) }
    }
}

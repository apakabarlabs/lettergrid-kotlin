package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OneLetterWordTests {
    private val island = Cell(1, 4)

    private val board: Board
        get() =
            Board(
                listOf("HOUSEX", "QDOORÖ", "XXXXXX").map { it.cells },
                listOf(
                    Placement("HOUSE", Cell(0, 0), Direction.RIGHT),
                    Placement("Ö", listOf(Cell(1, 5))),
                ),
            )

    @Test
    fun aOneLetterWordTakesOneCell() {
        val placement = Placement.validating("Ö", listOf(island))

        assertEquals(listOf(island), placement.cells)
        assertEquals("Ö", placement.word)
    }

    @Test
    fun onlyARuleWithoutTurnsReadsOneCell() {
        val straight = WordRule(listOf(Direction.RIGHT, Direction.DOWN))
        val turned = WordRule(listOf(Direction.RIGHT, Direction.DOWN), 1)

        assertEquals(1, straight.minimumLength)
        assertTrue(straight.matches(listOf(island)))
        assertEquals(3, turned.minimumLength)
        assertFalse(turned.matches(listOf(island)))
    }

    @Test
    fun aBoardWithAOneLetterWordValidates() {
        Board.validating(board.letters, board.placements)
    }

    @Test
    fun aTapOnTheCellOfAOneLetterWordFindsIt() {
        val round = Round(board, listOf(Direction.RIGHT, Direction.DOWN))
        val word = board.placements[1]

        round.touch(Cell(1, 5))

        assertEquals(word, round.release())
        assertTrue(word in round.found)
        assertTrue(round.rejected.isEmpty())
    }

    @Test
    fun aTapElsewhereFindsNothingAndShowsNoRejection() {
        val round = Round(board, listOf(Direction.RIGHT, Direction.DOWN))

        round.touch(Cell(0, 0))

        assertNull(round.release())
        assertTrue(round.found.isEmpty())
        assertTrue(round.rejected.isEmpty())
    }

    @Test
    fun aOneLetterWordIsFoundOnceAndThenATapOnItDoesNothing() {
        val round = Round(board, listOf(Direction.RIGHT, Direction.DOWN))
        round.touch(Cell(1, 5))
        round.release()

        round.touch(Cell(1, 5))

        assertNull(round.release())
        assertTrue(round.rejected.isEmpty())
    }

    @Test
    fun aOneLetterWordIsSelectableWhateverTheRoundDirections() {
        val lone = Board(listOf("XÖ".cells), listOf(Placement("Ö", listOf(Cell(0, 1)))))
        val restored = Round.restoring(RoundProgress(), lone, listOf(Direction.DOWN))
        assertFalse(restored.isOver)

        val round = Round(lone, listOf(Direction.DOWN))
        round.touch(Cell(0, 1))
        assertEquals(lone.placements[0], round.release())
    }

    @Test
    fun aOneLetterWordTakesTheDirectionOfTheRuleThatPlacedIt() {
        val generator =
            BoardGenerator(
                columns = 5,
                rows = 5,
                rules = listOf(WordRule(listOf(Direction.DOWN))),
                filler = LetterWeights(mapOf("X" to 1.0, "Q" to 1.0)),
            )
        val board = generator.request(listOf("CAT", "I")).board(1uL)

        assertEquals(listOf(Direction.DOWN, Direction.DOWN), board.placements.map { it.direction })
    }
}

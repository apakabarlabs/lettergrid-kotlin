package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class BoardValidationTests {
    @Test
    fun aRaggedMatrixIsRejectedBeforeAnyCellCanBeRead() {
        val failure = assertFailsWith<Board.Failure> { Board.validating(listOf("CAT".cells, "X".cells), emptyList()) }
        assertEquals(Board.Failure.IrregularRows, failure)
    }

    @Test
    fun emptyRowsAreRejectedButAnEmptyBoardIsValid() {
        val failure = assertFailsWith<Board.Failure> { Board.validating(listOf(emptyList()), emptyList()) }
        assertEquals(Board.Failure.IrregularRows, failure)
        val empty = Board.validating(emptyList(), emptyList())
        assertEquals(0, empty.rows)
        assertEquals(0, empty.columns)
        assertFalse(empty.contains(Cell(0, 0)))
    }

    @ParameterizedTest
    @ValueSource(ints = [Int.MIN_VALUE, -1, 1, Int.MAX_VALUE])
    fun anOutOfBoundsPlacementIsRejectedWithoutIndexingTheMatrix(row: Int) {
        val failure =
            assertFailsWith<Board.Failure> {
                Board.validating(listOf("CAT".cells), listOf(Placement("CAT", Cell(row, 0), Direction.DOWN)))
            }
        assertEquals(Board.Failure.InvalidPlacement("CAT"), failure)
    }

    @Test
    fun placementsMustReadTheirWordsAndHaveUniqueNames() {
        val cat = Placement("CAT", Cell(0, 0), Direction.RIGHT)
        assertEquals(
            Board.Failure.InvalidPlacement("CAT"),
            assertFailsWith<Board.Failure> { Board.validating(listOf("DOG".cells), listOf(cat)) },
        )
        assertEquals(
            Board.Failure.DuplicateWord("CAT"),
            assertFailsWith<Board.Failure> { Board.validating(listOf("CAT".cells), listOf(cat, cat)) },
        )
    }

    @ParameterizedTest
    @MethodSource("invalidPaths")
    fun aSavedPathThatIsNoWordPathIsReportedInsteadOfTrapping(cells: List<Cell>) {
        val failure = assertFailsWith<Board.Failure> { Placement.validating("CAT", cells) }
        assertEquals(Board.Failure.InvalidPlacement("CAT"), failure)
    }

    @Test
    fun aValidSavedPathRestoresThePlacement() {
        val cells = listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1))
        assertEquals(Placement("CAT", cells), Placement.validating("CAT", cells))
    }

    companion object {
        @JvmStatic
        fun invalidPaths(): List<List<Cell>> =
            listOf(
                emptyList(),
                listOf(Cell(0, 0)),
                listOf(Cell(0, 0), Cell(0, 1)),
                listOf(Cell(0, 0), Cell(0, 2), Cell(0, 3)),
                listOf(Cell(0, 0), Cell(0, 1), Cell(0, 0)),
                listOf(Cell(0, 0), Cell(0, 1), Cell(1, 2)),
            )
    }
}

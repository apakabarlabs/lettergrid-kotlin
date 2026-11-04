package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SelectionTests {
    private val board = Board(listOf("CATS".cells, "ORXE".cells, "WEYA".cells, "ZZZL".cells), emptyList())
    private val straight = listOf(Direction.RIGHT, Direction.DOWN)
    private val forward = listOf(Direction.RIGHT, Direction.DOWN, Direction.DOWN_RIGHT, Direction.DOWN_LEFT)

    @Serializable
    private data class Case(
        val name: String,
        val rows: List<String>,
        val from: List<Int>,
        val toward: List<Int>,
        val ways: List<Direction>,
        val text: String,
    )

    @Test
    fun aDragAlongTheRowSelectsTheRow() {
        assertEquals("CAT", board.text(board.selection(Cell(0, 0), Cell(0, 2), straight)))
    }

    @Test
    fun aDragSnapsToTheAxisItMovedFurthestAlong() {
        assertEquals("COW", board.text(board.selection(Cell(0, 0), Cell(2, 1), straight)))
    }

    @Test
    fun dragsSelectTheCellsEveryPortAgreesOn() {
        for (item in Fixtures.decode("selections", ListSerializer(Case.serializer()))) {
            val board = Board(item.rows.map { it.cells }, emptyList())
            val cells =
                board.selection(
                    Cell(item.from[0], item.from[1]),
                    Cell(item.toward[0], item.toward[1]),
                    item.ways,
                )
            assertEquals(item.text, board.text(cells), item.name)
        }
    }

    @Test
    fun aDragBackwardsSelectsOnlyTheStartWhenOnlyForwardIsAllowed() {
        assertEquals(listOf(Cell(0, 3)), board.selection(Cell(0, 3), Cell(0, 0), straight))
    }

    @Test
    fun aDragPastTheEdgeStopsAtTheEdge() {
        assertEquals("SEAL", board.text(board.selection(Cell(0, 3), Cell(9, 3), straight)))
    }

    @Test
    fun aDragToAnExtremeCoordinateOnlyVisitsBoardCells() {
        assertEquals(
            (0 until 4).map { Cell(it, 3) },
            board.selection(Cell(0, 3), Cell(Int.MAX_VALUE, 3), listOf(Direction.DOWN)),
        )
        assertEquals(
            (0 until 4).map { Cell(3 - it, 3 - it) },
            board.selection(Cell(3, 3), Cell(Int.MIN_VALUE, Int.MIN_VALUE), listOf(Direction.UP_LEFT)),
        )
    }

    @Test
    fun bothForwardDiagonalsCanBeSelected() {
        assertEquals((0..3).map { Cell(it, it) }, board.selection(Cell(0, 0), Cell(3, 3), forward))
        assertEquals((0..3).map { Cell(it, 3 - it) }, board.selection(Cell(0, 3), Cell(3, 0), forward))
    }

    @Test
    fun anAlmostDiagonalDragSnapsToTheClosestDirection() {
        assertEquals((0..3).map { Cell(it, it) }, board.selection(Cell(0, 0), Cell(3, 2), forward))
    }

    @Test
    fun diagonalsStayUnavailableUnlessRequested() {
        assertEquals((0..3).map { Cell(it, 0) }, board.selection(Cell(0, 0), Cell(3, 2), straight))
    }

    @Test
    fun aBackwardDragDoesNotSnapToAForwardDiagonal() {
        val start = Cell(0, 3)
        assertEquals(listOf(start), board.selection(start, Cell(0, 0), forward))
    }
}

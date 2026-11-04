package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LetterRunsTests {
    @Serializable
    private data class Target(
        val word: String,
        val row: Int,
        val column: Int,
        val direction: Direction,
    ) {
        val placement: Placement get() = Placement(word, Cell(row, column), direction)
    }

    @Serializable
    private data class Case(
        val name: String,
        val letters: List<String>,
        val placements: List<Target>,
        val forbidden: Boolean,
    )

    @Test
    fun triplesAreAllowedOnlyAlongOneTargetPath() {
        for (item in Fixtures.decode("letter-runs", ListSerializer(Case.serializer()))) {
            val board = Board.validating(item.letters.map { it.cells }, item.placements.map { it.placement })
            val runs = LetterRuns(board.placements, board.columns, board.rows)
            assertEquals(item.forbidden, runs.containsForbiddenRun(board.letters), item.name)
        }
    }
}

package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TargetCopiesTests {
    @Serializable
    private data class Case(
        val name: String,
        val words: List<String>,
        val columns: Int,
        val rows: Int,
        val bounded: Boolean,
        val rejects: Boolean? = null,
        val grows: Boolean? = null,
        val alone: List<String>? = null,
        val turns: List<Int>? = null,
        val filler: Map<String, Double>? = null,
    )

    @Test
    fun everyTargetCharacterButAOneLetterWordStandsOnTheBoardAtLeastTwice() {
        for (item in Fixtures.decode("target-copies", ListSerializer(Case.serializer()))) {
            val generator =
                BoardGenerator(
                    size =
                        BoardSize(
                            columns = item.columns,
                            rows = item.rows,
                            maxRows = if (item.bounded) item.rows else null,
                        ),
                    rules = (item.turns ?: listOf(0)).map { WordRule(listOf(Direction.RIGHT, Direction.DOWN), it) },
                    filler = Filler(LetterWeights(item.filler ?: mapOf("X" to 1.0, "Q" to 1.0))),
                )
            if (item.rejects == true) {
                val failure = assertFailsWith<BoardGenerator.Failure>(item.name) { generator.request(item.words) }
                assertEquals(BoardGenerator.Failure.NoRoom(item.words, item.columns, item.rows), failure, item.name)
                continue
            }
            val request = generator.request(item.words)
            for (seed in 1uL..8uL) {
                val board = request.board(seed)
                assertEquals(item.grows == true, board.rows > item.rows, item.name)
                val onTargets = mutableMapOf<String, Int>()
                for (cell in board.placements.flatMap { it.cells }.toSet()) {
                    onTargets[board[cell]] = (onTargets[board[cell]] ?: 0) + 1
                }
                for ((character, count) in onTargets) {
                    val onBoard = board.letters.flatten().count { it == character }
                    val alone = item.alone?.contains(character) == true
                    assertEquals(if (alone) count else maxOf(2, count), onBoard, "${item.name}, seed $seed: $character")
                }
            }
        }
    }
}

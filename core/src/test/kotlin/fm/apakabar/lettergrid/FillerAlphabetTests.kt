package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FillerAlphabetTests {
    @Serializable
    private data class Case(
        val name: String,
        val words: List<String>,
        val weights: Map<String, Double>,
        val columns: Int,
        val rows: Int,
        val directions: List<Direction>,
        val vertical: List<Int>,
        val runDirections: List<Direction>,
        val seeds: List<Long>,
    )

    @Test
    fun targetWordsMayUseTheWholeFillerAlphabet() {
        for (item in Fixtures.decode("filler-alphabet", ListSerializer(Case.serializer()))) {
            val generator =
                BoardGenerator(
                    columns = item.columns,
                    rows = item.rows,
                    maxRows = item.rows,
                    directions = item.directions,
                    filler = LetterWeights(item.weights),
                    downWords = item.vertical[0]..item.vertical[1],
                )
            assertTrue(generator.accepts(item.words), item.name)
            val request = generator.request(item.words)
            for (seed in item.seeds) {
                val board = request.board(seed.toULong())
                assertEquals(item.words.toSet(), board.placements.map { it.word }.toSet(), item.name)
                for (word in item.words) assertEquals(1, board.occurrences(word, along = item.directions), item.name)
                for (letter in item.words.flatMap { it.cells }.toSet()) {
                    assertTrue(board.letters.flatten().count { it == letter } >= 2, item.name)
                    assertEquals(0, board.occurrences(letter.repeat(3), along = item.runDirections), item.name)
                }
            }
        }
    }
}

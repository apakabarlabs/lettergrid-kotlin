package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunFreeFillGoldenTests {
    @Serializable
    private data class Case(
        val name: String,
        val words: List<String>,
        val directions: List<Direction>,
        val symbols: Set<String>,
        val boards: Map<String, String>,
        val turns: List<Int>,
    )

    @Serializable
    private data class Fixture(
        val seeds: List<Long>,
        val filler: Map<String, Double>,
        val cases: List<Case>,
    )

    @Test
    fun incrementalFillingKeepsTheOriginalBoards() {
        val fixture = Fixtures.decode("run-free-fill", Fixture.serializer())
        for (test in fixture.cases) {
            val rules = test.turns.map { WordRule(test.directions, it) }
            val fill =
                RunFreeFill(
                    FillTestBoard.make(test.words),
                    LetterWeights(fixture.filler),
                    rules,
                    test.symbols.map { it.cells.first() }.toSet(),
                )
            for (seed in fixture.seeds) {
                val filled = fill.filled(seed.toULong())
                if (filled != null) {
                    val runs = LetterRuns(filled.placements, filled.columns, filled.rows)
                    assertFalse(runs.containsForbiddenRun(filled.letters), "${test.name}, seed $seed")
                    assertTrue(
                        filled.placements.all { filled.hasSingleOccurrence(it.word, rules) },
                        "${test.name}, seed $seed",
                    )
                }
                val signature = filled?.letters?.joinToString("/") { it.joinToString("") } ?: "<unfilled>"
                assertEquals(test.boards.getValue(seed.toString()), signature, "${test.name}, seed $seed")
            }
        }
    }
}

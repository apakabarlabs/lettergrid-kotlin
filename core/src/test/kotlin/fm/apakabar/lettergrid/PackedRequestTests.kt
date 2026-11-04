package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PackedRequestTests {
    @Serializable
    private data class Rule(
        val directions: List<Direction>,
        val turns: Int,
    ) {
        val wordRule: WordRule get() = WordRule(directions, turns)
    }

    @Serializable
    private data class Case(
        val name: String,
        val rules: List<Rule>,
        val firstRule: Rule? = null,
        val leadingRules: List<Rule>? = null,
        val grows: Boolean? = null,
        val failure: String? = null,
        val downWords: List<Int>? = null,
        val maxWordsPerDirection: Int? = null,
        val largestDirectionCap: Boolean? = null,
        val legacyEquivalent: Boolean? = null,
        val words: List<String>,
        val accepted: Boolean? = null,
        val fits: Boolean,
    ) {
        val directionCap: Int?
            get() = if (largestDirectionCap == true) Int.MAX_VALUE else maxWordsPerDirection

        val leading: List<WordRule>
            get() = leadingRules?.map { it.wordRule } ?: listOfNotNull(firstRule?.wordRule)

        fun request(generator: BoardGenerator): BoardRequest =
            if (leadingRules != null) {
                generator.request(words, leading)
            } else {
                generator.request(words, firstRule = firstRule?.wordRule)
            }

        fun legacyRequest(generator: BoardGenerator): BoardRequest = generator.request(words, firstRule = leading.firstOrNull())
    }

    @Test
    fun theSolverDecidesWhetherWordsShareTheField() {
        for (item in Fixtures.decode("packed-requests", ListSerializer(Case.serializer()))) {
            val rules = item.rules.map { it.wordRule }
            val generator =
                BoardGenerator(
                    columns = 7,
                    rows = 7,
                    maxRows = if (item.grows == true) null else 7,
                    rules = rules,
                    filler = LetterWeights(mapOf("q" to 1.0, "w" to 1.0)),
                    downWords = item.downWords?.let { it[0]..it[1] } ?: if (item.words.size == 1) 0..1 else 1..2,
                    maxWordsPerDirection = item.directionCap,
                )
            assertEquals(item.accepted ?: true, generator.accepts(item.words, leadingRules = item.leading), item.name)
            if (!item.fits) {
                val expected =
                    if (item.failure == "invalidConfiguration") {
                        BoardGenerator.Failure.InvalidConfiguration
                    } else {
                        BoardGenerator.Failure.NoRoom(item.words, 7, 7)
                    }
                val failure = assertFailsWith<BoardGenerator.Failure>(item.name) { item.request(generator) }
                assertEquals(expected, failure, item.name)
                continue
            }
            val request = item.request(generator)
            assertEquals(item.leading, request.leadingRules)
            assertEquals(item.directionCap, request.maxWordsPerDirection)
            if (item.legacyEquivalent == true) assertEquals(item.legacyRequest(generator), request, item.name)
            val layouts = mutableSetOf<List<Placement>>()
            for (seed in 1uL..4uL) {
                val board = request.board(seed)
                layouts += board.placements
                assertEquals(item.words, board.placements.map { it.word }, item.name)
                checkDirectionLimit(board, item)
                assertTrue(board.placements.count { it.direction.isVertical } in generator.downWords, item.name)
                for (placement in board.placements) {
                    assertTrue(rules.any { it.matches(placement) }, "${item.name}: ${placement.word}")
                    assertEquals(1, board.occurrences(placement.word, rules = rules), "${item.name}: ${placement.word}")
                }
                for ((rule, placement) in item.leading.zip(board.placements)) {
                    assertTrue(rule.matches(placement), "${item.name}: ${placement.word}")
                }
            }
            assertTrue(layouts.size > 1, "${item.name}: every seed gave the same layout")
        }
    }

    private fun checkDirectionLimit(
        board: Board,
        item: Case,
    ) {
        val maximum = item.directionCap ?: return
        val counts = board.placements.groupBy { it.direction }
        assertTrue(counts.values.all { it.size <= maximum }, item.name)
    }
}

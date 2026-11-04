package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class NoiseScoreTests {
    private val rules = (0..3).map { WordRule(Direction.entries, it) }

    @Test
    fun repeatedReadingsAreScoredOnceAndTheirSubtreesArePruned() {
        val board = Board(List(7) { "AAAAAAA".cells }, emptyList())
        val words = (3..20).map { "A".repeat(it) }.toSet() + "UNRELATED"
        assertEquals(18, board.strayWordCount(WordIndex(words), rules, maximumVisits = 10_000))
    }

    @Test
    fun anExhaustedSearchBudgetDoesNotClaimTheBoardIsClean() {
        val board = Board(List(7) { "AAAAAAA".cells }, emptyList())
        val words = setOf("A".repeat(40) + "B")
        assertEquals(Int.MAX_VALUE, board.strayWordCount(WordIndex(words), rules, maximumVisits = 10))
    }
}

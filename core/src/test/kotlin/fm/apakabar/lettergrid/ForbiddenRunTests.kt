package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class ForbiddenRunTests {
    @Test
    @Timeout(1, unit = TimeUnit.MINUTES)
    fun wordsWhoseFirstShapesLineUpALetterStillGetABoard() {
        val groups =
            listOf(
                listOf(Direction.RIGHT, Direction.DOWN),
                listOf(Direction.DOWN_RIGHT, Direction.DOWN_LEFT),
                listOf(Direction.LEFT, Direction.UP),
                listOf(Direction.UP_LEFT, Direction.UP_RIGHT),
            )
        val rules =
            listOf(0, 1).flatMap { turns -> groups.map { WordRule(it, turns) } } +
                groups.take(3).map { WordRule(it, 2) }
        val words = listOf("ᲞᲐᲢᲐᲠᲐ", "ᲙᲔᲓᲔᲚᲘ", "ᲛᲝᲛᲪᲠᲝ")
        val filler =
            LetterWeights(
                words
                    .flatMap { it.cells }
                    .groupingBy { it }
                    .eachCount()
                    .mapValues { it.value.toDouble() },
            )
        val generator =
            BoardGenerator(columns = 7, rows = 7, maxRows = 7, rules = rules, filler = filler, maxWordsPerDirection = 2)
        val request = generator.request(words, leadingRules = emptyList())
        assertEquals(words.toSet(), request.words.toSet())
    }
}

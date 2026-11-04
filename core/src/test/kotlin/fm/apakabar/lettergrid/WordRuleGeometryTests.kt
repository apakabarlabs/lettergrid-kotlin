package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordRuleGeometryTests {
    @Test
    fun straightPathsRespectBothBounds() {
        val right = WordRule(listOf(Direction.RIGHT))
        assertTrue(right.canFit(7, 7, 7))
        assertFalse(right.canFit(8, 7, 7))
        val down = WordRule(listOf(Direction.DOWN))
        assertFalse(down.canFit(8, 7, 7))
        assertTrue(down.canFit(8, 1))
    }

    @Test
    fun bendsFitLongWordsAndRequireTheExactTurnCount() {
        assertTrue(WordRule(listOf(Direction.RIGHT), 1).canFit(8, 7, 7))
        assertTrue(WordRule(listOf(Direction.RIGHT), 2).canFit(4, 2, 2))
        assertTrue(WordRule(listOf(Direction.RIGHT), 3).canFit(5, 2, 3))
        assertFalse(WordRule(listOf(Direction.RIGHT), 1).canFit(2, 7, 7))
        assertFalse(WordRule(listOf(Direction.RIGHT), 3).canFit(5, 2, 2))
    }

    @Test
    fun diagonalBendsNeedRoomInBothAxes() {
        val diagonal = WordRule(listOf(Direction.UP_RIGHT), 1)
        assertFalse(diagonal.canFit(3, 2, 2))
        assertTrue(diagonal.canFit(3, 3, 2))
        assertFalse(WordRule(listOf(Direction.UP_RIGHT)).canFit(4, 3))
    }

    @Test
    fun invalidGeometryIsRejected() {
        val rule = WordRule(listOf(Direction.RIGHT))
        assertFalse(rule.canFit(0, 7))
        assertFalse(rule.canFit(1, 7))
        assertFalse(rule.canFit(3, 0))
        assertFalse(rule.canFit(3, 7, 0))
        assertFalse(WordRule(emptyList()).canFit(3, 7))
        assertFalse(WordRule(listOf(Direction.RIGHT), 4).canFit(6, 7))
    }
}

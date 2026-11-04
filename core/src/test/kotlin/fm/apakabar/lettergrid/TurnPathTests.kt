package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val path = listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1), Cell(1, 2), Cell(2, 2))

private val bentBoardLetters = listOf("ABX".cells, "XCD".cells, "XXE".cells)

class TurnPathTests {
    @Test
    fun aPlacementRetainsEveryTurnAndItsReverse() {
        val placement = Placement("ABCDE", path)
        assertEquals(3, placement.turns)
        assertEquals(path[0], placement.start)
        assertEquals(Direction.RIGHT, placement.direction)
        assertEquals(path.asReversed(), placement.reversed.cells)
        assertTrue(WordRule(listOf(Direction.RIGHT), 3).matches(placement))
        assertFalse(WordRule(listOf(Direction.RIGHT), 2).matches(placement))
    }

    @Test
    fun pathsRequireAdjacentCellsAndRightAngleTurnsWithoutRevisits() {
        assertNull(PathGeometry(listOf(path[0], path[1], path[0])))
        assertNull(PathGeometry(listOf(path[0], path[1], Cell(1, 2))))
        assertNull(PathGeometry(listOf(path[0], Cell(0, 2))))
        assertEquals(1, PathGeometry(listOf(Cell(0, 1), Cell(1, 2), Cell(2, 1)))?.turns)
    }

    @Test
    fun selectionFollowsTurnsAndBacktracksWithoutRevisiting() {
        val board = Board(List(5) { "XXXXX".cells }, emptyList())
        val rule = WordRule(listOf(Direction.RIGHT), 3)
        var selected = listOf(path[0])
        for (cell in path.drop(1)) selected = board.selection(extending = selected, toward = cell, rules = listOf(rule))
        assertEquals(path, selected)
        assertEquals(selected, board.selection(extending = selected, toward = Cell(2, 3), rules = listOf(rule)))
        selected = board.selection(extending = selected, toward = path[1], rules = listOf(rule))
        assertEquals(path.take(2), selected)
    }

    @Test
    fun roundFindsABentWordAndHintsFollowItsCells() {
        val placement = Placement("ABCDE", path)
        val board = Board(bentBoardLetters, listOf(placement))
        val round = Round(board, rules = listOf(WordRule(listOf(Direction.RIGHT), 3)))
        for (expected in path.dropLast(1)) {
            round.reveal()
            assertEquals(expected, round.hintedCells.last())
        }
        round.touch(path[0])
        for (cell in path.drop(1)) round.drag(cell)
        assertEquals(placement, round.release())
        assertEquals(Round.Phase.WON, round.phase)
    }

    @Test
    fun straightOnlyRestorationRejectsABentPlacement() {
        val board = Board(bentBoardLetters, listOf(Placement("ABCDE", path)))
        val failure = assertFailsWith<Round.Failure> { Round.restoring(RoundProgress(), board, directions = Direction.entries) }
        assertEquals(Round.Failure.UnselectableWord("ABCDE"), failure)
    }

    @Test
    fun distinctReadingsThroughTheSameCellsCountSeparately() {
        val board = Board(listOf("AA".cells, "AA".cells), emptyList())
        assertEquals(4, board.occurrences("AAAA", rules = listOf(WordRule(Direction.entries, 2))))
    }

    @Test
    fun singleLettersRemainVisibleToRuleBasedSearch() {
        val board = Board(listOf("AX".cells, "XA".cells), emptyList())
        assertEquals(2, board.occurrences("A", rules = listOf(WordRule(Direction.entries))))
    }

    @Test
    fun wordSearchIncludesTurnsAndIgnoresTheIntendedSubpath() {
        val board = Board(bentBoardLetters, listOf(Placement("ABCDE", path)))
        val rules = (0..3).map { WordRule(Direction.entries, it) }
        assertEquals(1, board.strayWordCount(WordIndex(setOf("ABCDE", "BCD", "BXD")), rules))
        assertEquals(0, board.strayWordCount(WordIndex(setOf("ABCDE", "BCD")), rules))
    }

    @Test
    fun revealProgressHandlesEmptyPathsAndReducedMotion() {
        assertEquals(listOf(path[0]), PathReveal(path, elapsed = 0.0, duration = 1.0).cells)
        assertEquals(path, PathReveal(path, elapsed = 0.7, duration = 1.0).cells)
        assertEquals(path, PathReveal(path, elapsed = 0.0, duration = 1.0, reduceMotion = true).cells)
        assertTrue(PathReveal(emptyList(), elapsed = 0.0, duration = 0.0).cells.isEmpty())
    }
}

class TurnPathIntegrationTests {
    @ParameterizedTest
    @MethodSource(TestArguments.DIRECTIONS_AND_TURNS)
    fun generationHonoursEveryStartAndExactTurnCount(
        direction: Direction,
        turns: Int,
    ) {
        val rule = WordRule(listOf(direction), turns)
        val generator =
            BoardGenerator(columns = 7, rows = 7, rules = listOf(rule), filler = LetterWeights(mapOf("x" to 1.0, "q" to 1.0)))
        val board = generator.request(listOf("ABCDEF"), firstRule = rule).board(12uL)
        assertTrue(rule.matches(board.placements[0]))
        assertEquals(1, board.occurrences("ABCDEF", rules = listOf(rule)))
    }

    @Test
    fun tooShortATurnedWordIsRejectedBeforeBuilding() {
        val rule = WordRule(listOf(Direction.RIGHT), 3)
        val generator = BoardGenerator(columns = 7, rows = 7, rules = listOf(rule), filler = LetterWeights(mapOf("x" to 1.0)))
        assertFailsWith<BoardGenerator.Failure> { generator.request(listOf("ABCD"), firstRule = rule) }
    }

    @Test
    fun compactLayoutsReuseMatchingLetters() {
        val rule = WordRule(listOf(Direction.RIGHT, Direction.DOWN))
        val generator =
            BoardGenerator(
                columns = 4,
                rows = 4,
                rules = listOf(rule),
                filler = LetterWeights(mapOf("x" to 1.0, "y" to 1.0, "z" to 1.0, "q" to 1.0)),
                downWords = 1..1,
            )
        val board = generator.request(listOf("CAT", "TAR")).board(17uL)
        val shared = board.placements[0].cells intersect board.placements[1].cells.toSet()
        assertTrue(shared.isNotEmpty())
    }

    @Test
    fun allRuleSearchPrunesALargeDictionaryAtItsPrefixes() {
        val board = Board(List(7) { "ZZZZZZZ".cells }, emptyList())
        val dictionary = (0 until 20_000).map { "ABC$it" }.toSet()
        val rules = (0..3).map { WordRule(Direction.entries, it) }
        assertEquals(0, board.strayWordCount(WordIndex(dictionary), rules))
        assertTrue(PathShapes(columns = 7, length = 20, rule = WordRule(listOf(Direction.RIGHT), 3)).cells().size <= 256)
    }

    @Test
    fun cumulativeRulesBuildNaturalWordsAndKeepTheRequiredIntroduction() {
        val groups =
            listOf(
                listOf(Direction.RIGHT, Direction.DOWN),
                listOf(Direction.DOWN_RIGHT),
                listOf(Direction.DOWN_LEFT),
                listOf(Direction.LEFT),
                listOf(Direction.UP),
                listOf(Direction.UP_LEFT),
                listOf(Direction.UP_RIGHT),
            )
        val rules = (0..3).flatMap { turns -> groups.map { WordRule(it, turns) } }
        val required = WordRule(listOf(Direction.UP_RIGHT), 3)
        val words = listOf("PLANET", "ORBIT", "LUNAR")
        val generator =
            BoardGenerator(
                columns = 7,
                rows = 7,
                rules = rules,
                filler = LetterWeights(mapOf("x" to 1.0, "q" to 1.0, "z" to 1.0)),
                downWords = 1..2,
            )
        val board = generator.request(words, firstRule = required).board(73uL)
        assertTrue(required.matches(board.placements[0]))
        assertEquals(words, board.placements.map { it.word })
        assertTrue(words.all { board.occurrences(it, rules = rules) == 1 })
    }

    @Test
    fun straightRulesCanCorrectAnInitiallyOffAxisDrag() {
        val placement = Placement("CAT", Cell(0, 0), Direction.RIGHT)
        val board = Board(listOf("CAT".cells, "XXX".cells), listOf(placement))
        val round = Round(board, rules = listOf(WordRule(listOf(Direction.RIGHT, Direction.DOWN))))
        round.touch(placement.start)
        round.drag(Cell(1, 1))
        round.drag(Cell(0, 2))
        assertEquals(placement.cells, round.selection)
        assertEquals(placement, round.release())
    }

    @Test
    fun mixedRulesCanCorrectTheFirstSegmentBeforeATurn() {
        val placement = Placement("CAT", Cell(0, 0), Direction.RIGHT)
        val board = Board(listOf("CAT".cells, "XXX".cells), listOf(placement))
        val rules = listOf(WordRule(Direction.entries), WordRule(listOf(Direction.RIGHT, Direction.DOWN), 1))
        val round = Round(board, rules = rules)
        round.touch(placement.start)
        round.drag(Cell(1, 1))
        round.drag(Cell(0, 2))
        assertEquals(placement.cells, round.selection)
        assertEquals(placement, round.release())
    }

    @ParameterizedTest
    @MethodSource("turnEnds")
    fun mixedSelectionPrefersAnIntentionalTurnWhenBothEndpointsMatch(end: Cell) {
        val board = Board(List(5) { "XXXXX".cells }, emptyList())
        val rules = listOf(WordRule(Direction.entries), WordRule(listOf(Direction.RIGHT, Direction.DOWN), 1))
        val start = Cell(0, 0)
        val straight = listOf(start, Cell(0, 1), Cell(0, 2))
        val selected = board.selection(extending = straight, toward = end, rules = rules)
        assertEquals(straight + listOf(Cell(1, 2), Cell(2, 2)), selected)
    }

    @Test
    fun zeroTurnRulesUseTheExistingStraightGenerator() {
        val filler = LetterWeights(mapOf("x" to 1.0, "q" to 1.0, "z" to 1.0))
        val ways = listOf(Direction.RIGHT, Direction.DOWN, Direction.DOWN_RIGHT)
        val rules = listOf(WordRule(listOf(Direction.RIGHT, Direction.DOWN)), WordRule(listOf(Direction.DOWN_RIGHT)))
        val straight = BoardGenerator(columns = 7, rows = 7, directions = ways, filler = filler, downWords = 1..2)
        val generic = BoardGenerator(columns = 7, rows = 7, rules = rules, filler = filler, downWords = 1..2)
        val words = listOf("PLANET", "ORBIT", "STAR")
        val expected = straight.request(words, firstDirection = Direction.DOWN_RIGHT).board(19uL)
        val actual = generic.request(words, firstRule = rules[1]).board(19uL)
        assertEquals(expected, actual)
    }

    @Test
    fun boundedShapeSamplingVariesTheFirstTurnPosition() {
        val firstTurns = mutableSetOf<Int>()
        val rule = WordRule(listOf(Direction.RIGHT), 3)
        for (seed in 0uL until 32uL) {
            val shape = PathShapes(columns = 10, length = 10, rule = rule, maximumShapes = 1).cells(seed).first()
            val firstTurn = (1 until shape.size - 1).first { Direction.between(shape[it], shape[it + 1]) != Direction.RIGHT }
            firstTurns += firstTurn
        }
        assertTrue(firstTurns.size >= 3)
    }

    @Test
    fun aSeedRebuildsTheSameTurnedBoard() {
        val rule = WordRule(listOf(Direction.RIGHT), 3)
        val generator =
            BoardGenerator(columns = 7, rows = 7, rules = listOf(rule), filler = LetterWeights(mapOf("x" to 1.0, "q" to 1.0, "z" to 1.0)))
        for (word in listOf("PLANET", "GALAXY", "COMETS", "NEBULA")) {
            val first = generator.request(listOf(word), firstRule = rule).board(1uL)
            val repeated = generator.request(listOf(word), firstRule = rule).board(1uL)
            assertEquals(first, repeated)
        }
    }

    companion object {
        @JvmStatic
        fun turnEnds(): List<Cell> = listOf(Cell(2, 2), Cell(2, 3))
    }
}

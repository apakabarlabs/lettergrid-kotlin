package fm.apakabar.lettergrid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathPackingTests {
    @Serializable
    private data class PackingRule(
        val directions: List<Direction>,
        val turns: Int,
    ) {
        val wordRule: WordRule get() = WordRule(directions, turns)
    }

    @Serializable
    private data class PackingCase(
        val name: String,
        val columns: Int,
        val rows: Int,
        val rules: List<PackingRule>,
        val firstRule: PackingRule? = null,
        val downWords: List<Int>,
        val words: List<String>,
        val nodeLimit: Int? = null,
        val outcome: String,
        val goldens: Map<String, String>? = null,
        val avoidsTargetRuns: Boolean? = null,
        val checksShifts: Boolean? = null,
        val mixedShapes: Boolean? = null,
        val conditionalCharacters: List<String>? = null,
    ) {
        val requiredSymbols: Set<String>
            get() {
                val used = words.flatMap { it.cells }.toSet()
                return used.intersect(conditionalCharacters.orEmpty().flatMap { it.cells }.toSet()) +
                    used.filter { !it.isLetterCell }
            }

        val packing: PathPacking
            get() {
                val wordRules = rules.map { it.wordRule }
                val packing =
                    PathPacking(
                        columns = columns,
                        rows = rows,
                        rules = wordRules,
                        leadingRules = listOfNotNull(firstRule?.wordRule),
                        downWords = downWords[0]..downWords[1],
                    ).let { if (nodeLimit != null) it.copy(nodeLimit = nodeLimit) else it }
                val required = requiredSymbols
                val avoidsRuns = avoidsTargetRuns == true
                if (!avoidsRuns && required.isEmpty()) return packing
                return packing.copy(completes = { placements, budget ->
                    val layout = Layout(rows, placements)
                    if (avoidsRuns && layout.hasForbiddenTargetRun(columns)) {
                        PathPacking.Completion.DOES_NOT_FIT
                    } else {
                        ConditionalFill.roomForDecoys(required, layout, columns, wordRules, budget)
                    }
                })
            }
    }

    @Serializable
    private data class PackingFixture(
        @SerialName("baseline_revision") val baselineRevision: String,
        val cases: List<PackingCase>,
    )

    private fun cases() = Fixtures.decode("path-packing", PackingFixture.serializer()).cases

    @Test
    fun eachCaseEndsAsProvedByHand() {
        for (item in cases()) {
            val packing = item.packing
            when (val outcome = packing.pack(item.words)) {
                is PathPacking.Outcome.Placed -> {
                    assertEquals("placed", item.outcome, item.name)
                    expectBoard(outcome.placements, item, packing.rules, packing.downWords)
                }
                PathPacking.Outcome.Impossible -> assertEquals("impossible", item.outcome, item.name)
                PathPacking.Outcome.Undetermined -> assertEquals("undetermined", item.outcome, item.name)
            }
        }
    }

    @Test
    fun seededPackingKeepsTheRecordedPlacementOrder() {
        val cases = cases().filter { it.goldens != null }
        assertTrue(cases.isNotEmpty())
        for (item in cases) {
            val packing = item.packing
            for ((seed, expected) in checkNotNull(item.goldens).toSortedMap()) {
                assertEquals(expected, signature(packing.pack(item.words, seed.toULong())), "${item.name}, seed $seed")
            }
        }
    }

    @Test
    fun isolatedShapeCompatibilityDoesNotDependOnItsOffset() {
        val cases = cases().filter { it.checksShifts == true }
        assertTrue(cases.isNotEmpty())
        for (item in cases) {
            val blank = ""
            assertFalse(item.words.joinToString("").contains(blank))
            val compatibility =
                PlacementCompatibility(item.columns, item.rows, item.rules.map { it.wordRule }, item.words, blank)
            val verdicts =
                item.words
                    .withIndex()
                    .flatMap { (index, word) ->
                        item.rules
                            .map { it.wordRule }
                            .flatMap { rule -> shapes(word, rule, item) }
                            .map { shape -> checkOffsets(shape, index, item, compatibility) }
                    }.toSet()
            assertTrue(verdicts.isNotEmpty(), item.name)
            if (item.mixedShapes == true) assertEquals(setOf(true, false), verdicts, item.name)
        }
    }

    private fun shapes(
        word: String,
        rule: WordRule,
        item: PackingCase,
    ): List<List<Cell>> =
        PathShapes(
            columns = item.columns,
            length = word.cellCount,
            rule = rule,
            maximumShapes = Int.MAX_VALUE,
            maximumRows = item.rows,
            visitedLimit = null,
            randomized = false,
        ).cells()

    private fun checkOffsets(
        shape: List<Cell>,
        index: Int,
        item: PackingCase,
        compatibility: PlacementCompatibility,
    ): Boolean {
        fun accepts(cells: List<Cell>): Boolean {
            val chosen = MutableList<Placement?>(item.words.size) { null }
            chosen[index] = Placement(item.words[index], cells)
            return compatibility.accepts(chosen)
        }
        val height = (shape.maxOfOrNull { it.row } ?: 0) + 1
        val width = (shape.maxOfOrNull { it.column } ?: 0) + 1
        assertTrue(height <= item.rows && width <= item.columns)
        val expected = accepts(shape)
        for (row in 0..(item.rows - height)) {
            for (column in 0..(item.columns - width)) {
                val shifted = shape.map { Cell(it.row + row, it.column + column) }
                assertEquals(expected, accepts(shifted), "${item.name}, word ${item.words[index]}, offset $row,$column")
            }
        }
        return expected
    }

    private fun signature(outcome: PathPacking.Outcome): String =
        when (outcome) {
            PathPacking.Outcome.Impossible -> "impossible"
            PathPacking.Outcome.Undetermined -> "undetermined"
            is PathPacking.Outcome.Placed ->
                outcome.placements.joinToString("|") { placement ->
                    placement.word + "=" + placement.cells.joinToString(";") { "${it.row},${it.column}" }
                }
        }

    private fun expectBoard(
        placements: List<Placement>,
        item: PackingCase,
        rules: List<WordRule>,
        quota: IntRange,
    ) {
        assertEquals(item.words, placements.map { it.word }, item.name)
        val letters = List(item.rows) { MutableList(item.columns) { "." } }
        for (placement in placements) {
            assertTrue(rules.any { it.matches(placement) }, "${item.name}: ${placement.word}")
            for ((cell, letter) in placement.cells.zip(placement.word.cells)) letters[cell.row][cell.column] = letter
        }
        val first = item.firstRule
        val placement = placements.firstOrNull()
        if (first != null && placement != null) assertTrue(first.wordRule.matches(placement), item.name)
        if (item.avoidsTargetRuns ==
            true
        ) {
            assertFalse(Layout(item.rows, placements).hasForbiddenTargetRun(item.columns))
        }
        assertTrue(placements.count { it.direction.isVertical } in quota, item.name)
        val board = Board.validating(letters, placements)
        for (each in placements) assertTrue(board.hasSingleOccurrence(each.word, rules), item.name)
    }
}

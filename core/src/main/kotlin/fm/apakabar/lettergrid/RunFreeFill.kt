package fm.apakabar.lettergrid

internal class RunFreeFill(
    val board: Board,
    val weights: LetterWeights,
    val rules: List<WordRule>,
    val conditionalCharacters: Set<String>,
) {
    private class Template(
        val letters: List<List<String?>>,
        val empty: List<Cell>,
        val required: Set<String>,
        val targets: WordIndex,
    )

    private class CopyDemand(
        val missing: Set<String>,
        val remaining: Int,
        val isCopyCell: Boolean,
    )

    private class FillOrder(
        template: Template,
        random: SeededRandom,
    ) {
        val cells: List<Cell>
        val required: Set<String> = template.required

        init {
            val copyCells = random.shuffled(template.empty).take(template.required.size).toSet()
            cells = template.empty.filter { it in copyCells } + template.empty.filter { it !in copyCells }
        }

        fun demand(
            position: Int,
            letters: List<List<String?>>,
        ): CopyDemand =
            CopyDemand(
                missing = required - cells.take(position).mapNotNull { letters[it.row][it.column] }.toSet(),
                remaining = cells.size - position,
                isCopyCell = position < required.size,
            )
    }

    private class Strategy(
        val avoidsPairs: Boolean,
    ) {
        fun frame(
            letters: List<String>,
            neighbours: Map<String, Int>,
        ): Frame {
            val separate = letters.filter { it !in neighbours }
            val paired = if (avoidsPairs) emptyList() else letters.filter { it in neighbours }
            return Frame(separate + paired)
        }
    }

    private class Frame(
        val letters: List<String>,
    ) {
        private var next = 0

        fun take(): String? {
            if (next >= letters.size) return null
            return letters[next].also { next += 1 }
        }
    }

    fun filled(seed: ULong): Board? {
        val symbols = conditionalCharacters + board.placements.flatMap { it.word.cells }.filter { !it.isLetterCell }
        val ordinary = weights.letters(excluding = symbols) ?: return null
        val template = template()
        val runs =
            LetterRuns(board.placements, board.columns, board.rows).takeIf {
                template.required.size <= template.empty.size && !it.containsForbiddenRun(template.letters)
            }
        val random = SeededRandom(seed)
        return runs?.let {
            listOf(true, false).firstNotNullOfOrNull { avoidsPairs ->
                Search(template, ordinary, runs, Strategy(avoidsPairs), random).run()
            }
        }
    }

    private fun template(): Template {
        val letters = board.placementLetters
        val empty = mutableListOf<Cell>()
        for (row in 0 until board.rows) {
            for (column in 0 until board.columns) {
                if (letters[row][column] == null) empty += Cell(row, column)
            }
        }
        val counts = mutableMapOf<String, Int>()
        for (letter in letters.flatten().filterNotNull()) counts[letter] = (counts[letter] ?: 0) + 1
        return Template(
            letters = letters,
            empty = empty,
            required = counts.filter { it.value < 2 }.keys,
            targets = WordIndex(board.placements.map { it.word }.toSet()),
        )
    }

    private inner class Search(
        val template: Template,
        val weights: LetterWeights,
        val runs: LetterRuns,
        val strategy: Strategy,
        val random: SeededRandom,
    ) {
        private val letters = template.letters.map { it.toMutableList() }
        private val targets = FillWordCheck(board, template.targets, rules, template.letters)
        private val order = FillOrder(template, random)
        private val empty = order.cells
        private val frames = mutableListOf<Frame>()
        private val neighbours = LetterNeighbours(board.columns, board.rows)
        private var position = 0
        private var completions = 0
        private var remaining = maxOf(1, empty.size) * (weights.letterCount + 1) * stepsPerChoice
        private var result: Board? = null
        private var finished = false

        fun run(): Board? {
            while (!finished && (remaining > 0 || position == empty.size)) {
                if (position == empty.size) complete() else advance()
            }
            return result
        }

        private fun complete() {
            if (completions >= completedCandidates) {
                finished = true
            } else {
                completions += 1
                val candidate = completed(letters)
                if (readsTargetsOnce(candidate)) {
                    result = candidate
                    finished = true
                } else {
                    stepBack()
                }
            }
        }

        private fun advance() {
            val cell = empty[position]
            if (frames.size == position) {
                val counts = neighbours.counts(cell) { letters[it.row][it.column] }
                val ordered = choices(order.demand(position, letters), weights, counts, random)
                frames += strategy.frame(ordered, counts)
            }
            val letter = frames[position].take()
            if (letter == null) {
                clear(cell)
                frames.removeAt(frames.lastIndex)
                stepBack()
            } else {
                remaining -= 1
                letters[cell.row][cell.column] = letter
                if (!runs.hasForbiddenRun(cell, letters) && targets.isClean(cell, letters)) position += 1
            }
        }

        private fun stepBack() {
            if (position <= 0) {
                finished = true
            } else {
                position -= 1
                clear(empty[position])
            }
        }

        private fun clear(cell: Cell) {
            letters[cell.row][cell.column] = null
        }
    }

    private fun choices(
        demand: CopyDemand,
        weights: LetterWeights,
        neighbours: Map<String, Int>,
        random: SeededRandom,
    ): List<String> =
        if (demand.missing.size > demand.remaining) emptyList() else ordered(demand, weights, neighbours, random)

    private fun ordered(
        demand: CopyDemand,
        weights: LetterWeights,
        neighbours: Map<String, Int>,
        random: SeededRandom,
    ): List<String> {
        val missing = demand.missing
        val copies =
            random.shuffled(
                missing.sortedWith { first, second ->
                    when {
                        first.precedesInCodePoints(second) -> -1
                        second.precedesInCodePoints(first) -> 1
                        else -> 0
                    }
                },
            )
        if (missing.size >= demand.remaining) return copies
        val ordinary = weights.orderedLetters(random, neighbours).filter { it !in missing }
        return if (demand.isCopyCell || random.nextInt(below = demand.remaining) < missing.size) {
            copies + ordinary
        } else {
            ordinary + copies
        }
    }

    private fun readsTargetsOnce(candidate: Board): Boolean =
        candidate.placements.all {
            candidate.hasSingleOccurrence(it.word, rules)
        }

    private fun completed(letters: List<List<String?>>): Board =
        Board(
            letters.map { row -> row.map { checkNotNull(it) { "Every searched cell has a letter" } } },
            board.placements,
        )

    companion object {
        val completedCandidates: Int get() = Generation.settings.fill.completedCandidates
        val stepsPerChoice: Int get() = Generation.settings.fill.stepsPerChoice
    }
}

internal class LetterRuns(
    placements: List<Placement>,
    val columns: Int,
    val rows: Int,
) {
    private class Triple(
        val first: Cell,
        val second: Cell,
        val third: Cell,
    ) {
        fun matches(letters: List<List<String?>>): Boolean {
            val letter = letters[first.row][first.column] ?: return false
            return letters[second.row][second.column] == letter && letters[third.row][third.column] == letter
        }
    }

    private val triplesByCell: Map<Cell, List<Triple>>

    init {
        val triples = mutableSetOf<List<Cell>>()
        for (placement in placements) {
            val cells = placement.cells
            if (cells.size < 3) continue
            for (start in 0..(cells.size - 3)) {
                val run = cells.subList(start, start + 3).toList()
                triples += run
                triples += run.asReversed().toList()
            }
        }
        val indexed = mutableMapOf<Cell, MutableList<Triple>>()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val start = Cell(row, column)
                for (direction in Direction.lineAxes) {
                    val cells = (0 until 3).map { start.moved(direction, it) }
                    if (!cells.all { it.row in 0 until rows && it.column in 0 until columns } ||
                        cells in triples
                    ) {
                        continue
                    }
                    val triple = Triple(cells[0], cells[1], cells[2])
                    for (cell in cells) indexed.getOrPut(cell) { mutableListOf() } += triple
                }
            }
        }
        triplesByCell = indexed
    }

    fun containsForbiddenRun(letters: List<List<String?>>): Boolean =
        (0 until rows).any { row -> (0 until columns).any { column -> hasForbiddenRun(Cell(row, column), letters) } }

    fun hasForbiddenRun(
        through: Cell,
        letters: List<List<String?>>,
    ): Boolean = triplesByCell[through].orEmpty().any { it.matches(letters) }
}

internal fun Board.Companion.targets(
    layout: Layout,
    columns: Int,
): Board {
    val blank =
        checkNotNull(String.privateUse(avoiding = layout.placements.flatMap { it.word.cells }.toSet())) {
            "Target words cannot use every private-use character"
        }
    return Board(layout.placements.letters(columns, layout.rows, blank), layout.placements)
}

internal val Board.placementLetters: List<List<String?>>
    get() {
        val occupied = placements.flatMap { it.cells }.toSet()
        return letters.mapIndexed { row, line ->
            line.mapIndexed { column, letter ->
                letter.takeIf {
                    Cell(row, column) in
                        occupied
                }
            }
        }
    }

internal val Board.hasAddedEqualPair: Boolean
    get() {
        val occupied = placements.flatMap { it.cells }.toSet()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                val cell = Cell(row, column)
                for (direction in Direction.lineAxes) {
                    val next = cell.moved(direction, 1)
                    if (!contains(next) || (cell in occupied && next in occupied)) continue
                    if (this[cell] == this[next]) return true
                }
            }
        }
        return false
    }

internal fun Layout.hasForbiddenTargetRun(columns: Int): Boolean =
    LetterRuns(placements, columns, rows).containsForbiddenRun(placements.letters(columns, rows))

internal fun Layout.charactersToCopyIfRoom(columns: Int): Set<String>? {
    val letters = mutableMapOf<Cell, String>()
    for (placement in placements) {
        for ((cell, letter) in placement.cells.zip(placement.word.cells)) letters[cell] = letter
    }
    val counts = mutableMapOf<String, Int>()
    for (letter in letters.values) counts[letter] = (counts[letter] ?: 0) + 1
    val needing = counts.filter { it.value < 2 }.keys
    return if (needing.size <= rows * columns - letters.size) needing else null
}

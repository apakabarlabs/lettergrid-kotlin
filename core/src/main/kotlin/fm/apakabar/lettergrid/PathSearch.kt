package fm.apakabar.lettergrid

internal data class PathSearch(
    val board: Board,
    val rules: List<WordRule>,
    val maximumReadings: Int = Int.MAX_VALUE,
    val letters: ((Cell) -> String?)? = null,
) {
    private class Visitor(
        val visit: (Placement) -> Boolean,
        val skips: (Int) -> Boolean,
        val codes: List<Int?>,
    )

    private class AllowedTurns(
        val direction: Direction,
        val counts: Set<Int>,
    ) {
        val maximum: Int get() = counts.maxOrNull() ?: 0
    }

    private class Trail(
        size: Int,
    ) {
        val cells = mutableListOf<Cell>()
        val visited = BooleanArray(size)
        var stopped = false
    }

    private class Step(
        val direction: Direction,
        val turns: Int,
        val node: Int,
    )

    fun readings(index: WordIndex): List<Placement> {
        val found = linkedSetOf<Placement>()
        visitReadings(index) { placement ->
            found += placement
            found.size < maximumReadings
        }
        return found.toList()
    }

    fun visitReadings(
        index: WordIndex,
        skipping: (Int) -> Boolean = { false },
        visit: (Placement) -> Boolean,
    ) {
        if (index.longest <= 0 || maximumReadings <= 0) return
        val grouped =
            Direction.entries.mapNotNull { direction ->
                val counts = rules.filter { it.isValid && direction in it.startDirections }.map { it.turns }.toSet()
                if (counts.isEmpty()) null else AllowedTurns(direction, counts)
            }
        val trail = Trail(board.rows * board.columns)
        val codes = ArrayList<Int?>(board.rows * board.columns)
        for (row in 0 until board.rows) {
            for (column in 0 until board.columns) {
                codes += letter(Cell(row, column))?.let { index.code(of = it) }
            }
        }
        val visitor = Visitor(visit, skipping, codes)
        for (rule in grouped) {
            for (row in 0 until board.rows) {
                for (column in 0 until board.columns) {
                    val start = Cell(row, column)
                    val code = codes[slot(start)] ?: continue
                    val node = index.next(after = 0, code = code) ?: continue
                    if (visitor.skips(node)) continue
                    enter(start, trail)
                    walk(Step(rule.direction, 0, node), rule, index, trail, visitor)
                    leave(trail)
                    if (trail.stopped) return
                }
            }
        }
    }

    private fun walk(
        step: Step,
        rule: AllowedTurns,
        index: WordIndex,
        trail: Trail,
        visitor: Visitor,
    ) {
        val length = trail.cells.size
        val word = index.word(at = step.node)
        if (step.turns in rule.counts && word != null) {
            val placement =
                if (length == 1) {
                    Placement(word, trail.cells[0], rule.direction)
                } else {
                    Placement(word, trail.cells.toList())
                }
            if (!visitor.visit(placement)) {
                trail.stopped = true
                return
            }
        }
        if (length >= index.longest) return
        val last = trail.cells.lastOrNull() ?: return
        val canTurn = length > 1 && step.turns < rule.maximum
        for (way in ways(step.direction, canTurn)) {
            val next = last.moved(way, 1)
            if (!board.contains(next) || trail.visited[slot(next)]) continue
            val code = visitor.codes[slot(next)] ?: continue
            val child = index.next(after = step.node, code = code) ?: continue
            if (visitor.skips(child)) continue
            enter(next, trail)
            walk(Step(way, step.turns + if (way == step.direction) 0 else 1, child), rule, index, trail, visitor)
            leave(trail)
            if (trail.stopped) return
        }
    }

    private fun slot(cell: Cell): Int = cell.row * board.columns + cell.column

    private fun letter(cell: Cell): String? = if (letters != null) letters.invoke(cell) else board[cell]

    private fun enter(
        cell: Cell,
        trail: Trail,
    ) {
        trail.cells += cell
        trail.visited[slot(cell)] = true
    }

    private fun leave(trail: Trail) {
        val cell = trail.cells.removeAt(trail.cells.lastIndex)
        trail.visited[slot(cell)] = false
    }

    private companion object {
        fun position(direction: Direction): Int = (direction.rowStep + 1) * 3 + direction.columnStep + 1

        val turnings: List<List<Direction>> =
            (0 until 9).map { position ->
                Direction.entries.firstOrNull { position == position(it) }?.let { listOf(it) + it.perpendiculars }
                    ?: emptyList()
            }

        fun ways(
            direction: Direction,
            turning: Boolean,
        ): List<Direction> {
            val ways = turnings[position(direction)]
            return if (turning) ways else ways.take(1)
        }
    }
}

internal fun Board.hasSingleOccurrence(
    word: String,
    rules: List<WordRule>,
): Boolean {
    val palindrome = word == word.reversedCells()
    val readings =
        PathSearch(this, rules, maximumReadings = if (palindrome) 3 else 2).readings(WordIndex(setOf(word)))
    val first = readings.firstOrNull() ?: return false
    return readings.all { it.cells == first.cells || (palindrome && it.cells == first.cells.asReversed()) }
}

fun Board.occurrences(
    word: String,
    rules: List<WordRule>,
): Int {
    val readings = PathSearch(this, rules).readings(WordIndex(setOf(word)))
    val paths = mutableSetOf<List<Cell>>()
    for (reading in readings) {
        if (reading.isPalindrome && reading.cells.asReversed().toList() in paths) continue
        paths += reading.cells
    }
    return paths.size
}

internal fun Board.covers(reading: Placement): Boolean =
    placements.any { placement ->
        placement.containsReading(reading.cells) ||
            (placement.isPalindrome && placement.reversed.containsReading(reading.cells))
    }

internal fun Placement.containsReading(path: List<Cell>): Boolean {
    val cells = cells
    if (path.size > cells.size) return false
    return (0..(cells.size - path.size)).any { offset -> cells.subList(offset, offset + path.size) == path }
}

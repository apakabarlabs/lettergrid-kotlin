package fm.apakabar.lettergrid

internal object ConditionalFill {
    fun roomForDecoys(
        characters: Set<String>,
        beside: Layout,
        columns: Int,
        rules: List<WordRule>,
        budget: Budget,
    ): PathPacking.Completion {
        val used = beside.placements.flatMap { it.word.cells }.toSet()
        val required =
            characters.intersect(used).sortedWith { first, second ->
                when {
                    first.precedesInCodePoints(second) -> -1
                    second.precedesInCodePoints(first) -> 1
                    else -> 0
                }
            }
        val blank = String.privateUse(avoiding = used)
        return when {
            required.isEmpty() -> PathPacking.Completion.FITS
            blank == null -> PathPacking.Completion.DOES_NOT_FIT
            else -> DecoySearch(required, beside, columns, rules, blank).completion(budget)
        }
    }
}

private class DecoySearch(
    val required: List<String>,
    beside: Layout,
    columns: Int,
    val rules: List<WordRule>,
    val blank: String,
) {
    private val placements = beside.placements
    private val letters = placements.letters(columns, beside.rows, blank).map { it.toMutableList() }
    private val free: List<Cell>
    private val chosen = mutableSetOf<Cell>()
    private var budget = 0

    init {
        val occupied = placements.flatMap { it.cells }.toSet()
        val cells = (0 until beside.rows).flatMap { row -> (0 until columns).map { Cell(row, it) } }
        free = SeededRandom(0uL).shuffled(cells.filter { it !in occupied })
    }

    fun completion(budget: Budget): PathPacking.Completion {
        this.budget = budget.value
        val found = place(0)
        budget.value = this.budget
        return when {
            found -> PathPacking.Completion.FITS
            this.budget < 0 -> PathPacking.Completion.RAN_OUT
            else -> PathPacking.Completion.DOES_NOT_FIT
        }
    }

    private fun place(index: Int): Boolean =
        if (index >= required.size) {
            val board = Board(letters.map { it.toList() }, placements)
            placements.all { board.hasSingleOccurrence(it.word, rules) }
        } else {
            free.any { cell -> budget >= 0 && cell !in chosen && placed(cell, index) }
        }

    private fun placed(
        cell: Cell,
        index: Int,
    ): Boolean {
        budget -= 1
        if (budget < 0) return false
        chosen += cell
        letters[cell.row][cell.column] = required[index]
        val completed = place(index + 1)
        if (!completed) {
            chosen -= cell
            letters[cell.row][cell.column] = blank
        }
        return completed
    }
}

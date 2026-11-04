package fm.apakabar.lettergrid

internal object ConditionalFill {
    fun roomForDecoys(
        characters: Set<String>,
        beside: Layout,
        columns: Int,
        rules: List<WordRule>,
        budget: Budget,
    ): PathPacking.Completion {
        val placements = beside.placements
        val rows = beside.rows
        val used = placements.flatMap { it.word.cells }.toSet()
        val required =
            characters.intersect(used).sortedWith { first, second ->
                when {
                    first.precedesInCodePoints(second) -> -1
                    second.precedesInCodePoints(first) -> 1
                    else -> 0
                }
            }
        if (required.isEmpty()) return PathPacking.Completion.FITS
        val blank = String.privateUse(avoiding = used) ?: return PathPacking.Completion.DOES_NOT_FIT
        val letters = placements.letters(columns, rows, blank).map { it.toMutableList() }
        val occupied = placements.flatMap { it.cells }.toSet()
        val random = SeededRandom(0uL)
        val free =
            random.shuffled(
                (0 until rows).flatMap { row -> (0 until columns).map { Cell(row, it) } }.filter { it !in occupied },
            )
        val search = DecoySearch(required, placements, rules, blank, free, letters, budget.value)
        val found = search.place(0)
        budget.value = search.budget
        if (found) return PathPacking.Completion.FITS
        return if (search.budget < 0) PathPacking.Completion.RAN_OUT else PathPacking.Completion.DOES_NOT_FIT
    }
}

private class DecoySearch(
    val required: List<String>,
    val placements: List<Placement>,
    val rules: List<WordRule>,
    val blank: String,
    val free: List<Cell>,
    val letters: List<MutableList<String>>,
    var budget: Int,
) {
    private val chosen = mutableSetOf<Cell>()

    fun place(index: Int): Boolean {
        if (index >= required.size) {
            val board = Board(letters.map { it.toList() }, placements)
            return placements.all { board.hasSingleOccurrence(it.word, rules) }
        }
        for (cell in free) {
            if (cell in chosen) continue
            budget -= 1
            if (budget < 0) return false
            chosen += cell
            letters[cell.row][cell.column] = required[index]
            if (place(index + 1)) return true
            chosen -= cell
            letters[cell.row][cell.column] = blank
            if (budget < 0) return false
        }
        return false
    }
}

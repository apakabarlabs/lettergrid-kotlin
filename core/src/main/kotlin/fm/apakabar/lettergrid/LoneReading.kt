package fm.apakabar.lettergrid

internal fun Placement.readsOnceAlone(rules: List<WordRule>): Boolean {
    val search = LoneReading(cells, word.cells, word.cells, rules)
    for (start in search.cells.indices) {
        if (search.letters[start] != search.target[0]) continue
        search.walk(start)
        if (!search.single) return false
    }
    return search.first != null
}

internal fun Placement.holdsReading(
    of: String,
    rules: List<WordRule>,
): Boolean {
    val search = LoneReading(cells, word.cells, of.cells, rules, stopsAtFirst = true)
    for (start in search.cells.indices) {
        if (search.letters[start] != search.target[0]) continue
        search.walk(start)
        if (search.first != null) return true
    }
    return false
}

private class LoneReading(
    val cells: List<Cell>,
    val letters: List<String>,
    val target: List<String>,
    val rules: List<WordRule>,
    val stopsAtFirst: Boolean = false,
) {
    private val palindrome = target == target.asReversed()
    private val path = mutableListOf<Int>()
    private val used = BooleanArray(cells.size)
    var first: List<Cell>? = null
        private set
    var single = true
        private set

    private val searching: Boolean get() = single && !(stopsAtFirst && first != null)

    fun walk(index: Int) {
        path += index
        used[index] = true
        try {
            if (path.size >= target.size) read(path.map { cells[it] }) else continueFrom(index)
        } finally {
            path.removeAt(path.lastIndex)
            used[index] = false
        }
    }

    private fun continueFrom(index: Int) {
        val letter = target[path.size]
        for (next in cells.indices) {
            val follows =
                searching &&
                    !used[next] &&
                    letters[next] == letter &&
                    Direction.between(cells[index], cells[next]) != null
            if (follows) walk(next)
        }
    }

    private fun read(reading: List<Cell>) {
        val geometry = PathGeometry(reading)
        if (geometry == null || rules.none { it.matches(geometry) }) return
        val first = first
        if (first == null) {
            this.first = reading
        } else {
            single = reading == first || (palindrome && reading == first.asReversed())
        }
    }
}

package fm.apakabar.lettergrid

internal class FillWordCheck(
    private val board: Board,
    private val index: WordIndex,
    private val rules: List<WordRule>,
    fixed: List<List<String?>>,
) {
    private val directions: List<Direction>?
    private val fixedIsClean: Boolean

    init {
        val directions =
            Direction.entries.filter { direction ->
                rules.any { it.isValid && it.turns == 0 && direction in it.startDirections }
            }
        val straight = rules.all { !it.isValid || it.turns == 0 }
        val incremental = straight && fitsVisitLimit(board, directions, index.longest)
        if (incremental) {
            this.directions = directions
            fixedIsClean = isClean(board, index, rules, fixed)
        } else {
            this.directions = null
            fixedIsClean = true
        }
    }

    fun isClean(
        filling: Cell,
        letters: List<List<String?>>,
    ): Boolean {
        if (index.longest <= 0) return true
        val directions = directions ?: return isClean(board, index, rules, letters)
        if (!fixedIsClean) return false
        for (direction in directions) {
            for (distance in 0 until index.longest) {
                val start = filling.moved(direction, -distance)
                if (!board.contains(start)) break
                if (!isClean(start, direction, distance, letters)) return false
            }
        }
        return true
    }

    private fun isClean(
        start: Cell,
        direction: Direction,
        through: Int,
        letters: List<List<String?>>,
    ): Boolean {
        var cell = start
        var node: Int? = 0
        var length = 0
        while (board.contains(cell)) {
            val letter = letters[cell.row][cell.column] ?: break
            node = index.next(after = node, letter = letter)
            if (node == null) break
            length += 1
            if (length > through && index.word(at = node) != null) return false
            if (length >= index.longest) break
            cell = cell.moved(direction, 1)
        }
        return true
    }

    private companion object {
        fun isClean(
            board: Board,
            index: WordIndex,
            rules: List<WordRule>,
            letters: List<List<String?>>,
        ): Boolean =
            index.longest == 0 ||
                board.strayWordCount(index, rules, lessThan = 1, letters = { letters[it.row][it.column] }) == 0

        fun fitsVisitLimit(
            board: Board,
            directions: List<Direction>,
            longest: Int,
        ): Boolean {
            if (longest <= 0 || directions.isEmpty()) return true
            if (board.rows <= 0 || board.columns <= 0) return false
            var available = NoiseSearch.maximumVisits
            val longestLine = minOf(longest, maxOf(board.rows, board.columns))
            for (factor in listOf(board.rows, board.columns, directions.size, longestLine)) {
                if (factor > available) return false
                available /= factor
            }
            return true
        }
    }
}

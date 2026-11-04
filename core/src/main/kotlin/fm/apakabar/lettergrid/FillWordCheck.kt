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
        val directions = directions
        return when {
            index.longest <= 0 -> true
            directions == null -> isClean(board, index, rules, letters)
            !fixedIsClean -> false
            else ->
                directions.all { direction ->
                    (0 until index.longest)
                        .asSequence()
                        .map { distance -> filling.moved(direction, -distance) to distance }
                        .takeWhile { (start, _) -> board.contains(start) }
                        .all { (start, distance) -> isClean(start, direction, distance, letters) }
                }
        }
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
        var clean = true
        while (clean && length < index.longest) {
            val letter = cell.takeIf(board::contains)?.let { letters[it.row][it.column] }
            val next = letter?.let { index.next(after = node, letter = it) } ?: break
            node = next
            length += 1
            clean = length <= through || index.word(at = next) == null
            cell = cell.moved(direction, 1)
        }
        return clean
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
            val longestLine = minOf(longest, maxOf(board.rows, board.columns))
            val factors = listOf(board.rows, board.columns, directions.size, longestLine)
            return when {
                longest <= 0 || directions.isEmpty() -> true
                board.rows <= 0 || board.columns <= 0 -> false
                else ->
                    factors.fold(NoiseSearch.maximumVisits as Int?) { available, factor ->
                        available?.takeIf { factor <= it }?.div(factor)
                    } != null
            }
        }
    }
}

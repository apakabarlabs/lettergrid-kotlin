package fm.apakabar.lettergrid

internal data class PathShapes(
    val columns: Int,
    val length: Int,
    val rule: WordRule,
    val maximumShapes: Int = Generation.settings.shapes.shapeLimit,
    val maximumRows: Int? = null,
    val visitedLimit: Int? = Generation.settings.shapes.visitLimit,
    val randomized: Boolean = true,
) {
    private class Search(
        val random: SeededRandom,
    ) {
        var visited = 0
        val shapes = mutableListOf<List<Cell>>()

        fun visit(limit: Int?): Boolean {
            val allowed = limit == null || visited < limit
            if (allowed && limit != null) visited += 1
            return allowed
        }
    }

    fun cells(seed: ULong = 0uL): List<List<Cell>> {
        if (!rule.isValid || length < rule.minimumLength) return emptyList()
        val shapes = mutableListOf<List<Cell>>()
        for (direction in rule.startDirections) {
            val search = Search(SeededRandom(seed))
            val origin = Cell(0, 0)
            val path = ArrayList<Cell>(length).apply { add(origin) }
            walk(path, Bounds(origin), direction, 0, search)
            shapes += search.shapes
        }
        return shapes
    }

    private data class Bounds(
        val top: Int,
        val bottom: Int,
        val left: Int,
        val right: Int,
    ) {
        constructor(cell: Cell) : this(cell.row, cell.row, cell.column, cell.column)

        fun including(cell: Cell): Bounds =
            Bounds(minOf(top, cell.row), maxOf(bottom, cell.row), minOf(left, cell.column), maxOf(right, cell.column))
    }

    private fun walk(
        path: MutableList<Cell>,
        bounds: Bounds,
        direction: Direction,
        turns: Int,
        search: Search,
    ) {
        val proceeds =
            search.shapes.size < maximumShapes &&
                search.visit(visitedLimit) &&
                length - path.size >= rule.turns - turns
        if (!proceeds) return
        if (path.size == length) {
            if (turns == rule.turns) search.shapes += path.map { Cell(it.row - bounds.top, it.column - bounds.left) }
            return
        }
        val last = path.last()
        for (way in directions(direction, path.size, turns, search)) {
            val next = last.moved(way, 1)
            val widened = bounds.including(next)
            if (next !in path && fits(widened)) {
                path += next
                walk(path, widened, way, turns + if (way == direction) 0 else 1, search)
                path.removeAt(path.lastIndex)
            }
        }
    }

    private fun fits(bounds: Bounds): Boolean =
        bounds.right - bounds.left < columns && (maximumRows == null || bounds.bottom - bounds.top < maximumRows)

    private fun directions(
        after: Direction,
        count: Int,
        turns: Int,
        search: Search,
    ): List<Direction> {
        val ways = listOf(after) + if (count > 1 && turns < rule.turns) after.perpendiculars else emptyList()
        return if (randomized) search.random.shuffled(ways) else ways
    }
}

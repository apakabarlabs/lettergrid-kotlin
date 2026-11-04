package fm.apakabar.lettergrid

fun Board.selection(
    extending: List<Cell>,
    toward: Cell,
    rules: List<WordRule>,
): List<Cell> {
    val start = extending.firstOrNull()
    val index = extending.indexOf(toward)
    return when {
        start == null || !contains(toward) -> extending
        rules.all { it.turns == 0 } -> selection(start, toward, rules.startDirections)
        index >= 0 -> extending.take(index + 1)
        else ->
            firstSegmentSelection(extending, toward, rules)
                ?: continuationWays(extending, rules)?.let { continuedSelection(extending, toward, it) }
                ?: extending
    }
}

private fun continuationWays(
    extending: List<Cell>,
    rules: List<WordRule>,
): List<Direction>? =
    if (extending.size == 1) {
        rules.flatMap { it.startDirections }.toSet().sortedBy { it.serialName }
    } else {
        val geometry = PathGeometry(extending)
        val direction = Direction.between(extending[extending.size - 2], extending.last())
        if (geometry == null || direction == null) {
            null
        } else {
            val canTurn = rules.any { geometry.direction in it.startDirections && geometry.turns < it.turns }
            listOf(direction) + if (canTurn) direction.perpendiculars else emptyList()
        }
    }

private fun Board.firstSegmentSelection(
    cells: List<Cell>,
    toward: Cell,
    rules: List<WordRule>,
): List<Cell>? {
    val geometry = PathGeometry(cells)
    val start = cells.firstOrNull()
    if (geometry == null || geometry.turns != 0 || start == null) return null
    val straight = selection(start, toward, rules.startDirections)
    val canTurn = rules.any { geometry.direction in it.startDirections && it.turns > 0 }
    val turned =
        if (canTurn) {
            continuedSelection(cells, toward, listOf(geometry.direction) + geometry.direction.perpendiculars)
        } else {
            straight
        }
    return if (distance(straight, toward) < distance(turned, toward)) straight else turned
}

private fun distance(
    cells: List<Cell>,
    end: Cell,
): Double {
    val last = cells.lastOrNull() ?: return Double.POSITIVE_INFINITY
    val row = last.row.toDouble() - end.row.toDouble()
    val column = last.column.toDouble() - end.column.toDouble()
    return row * row + column * column
}

private fun Board.continuedSelection(
    cells: List<Cell>,
    toward: Cell,
    ways: List<Direction>,
): List<Cell> {
    val last = cells.lastOrNull() ?: return cells
    val continuation = selection(last, toward, ways).drop(1)
    val extended = cells.toMutableList()
    for (cell in continuation) {
        if (cell in extended) break
        extended += cell
    }
    return extended
}

internal val Direction.serialName: String
    get() = Direction.serializer().descriptor.getElementName(ordinal)

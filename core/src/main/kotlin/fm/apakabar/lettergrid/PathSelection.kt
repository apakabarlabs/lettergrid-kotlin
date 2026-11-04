package fm.apakabar.lettergrid

fun Board.selection(
    extending: List<Cell>,
    toward: Cell,
    rules: List<WordRule>,
): List<Cell> {
    val last = extending.lastOrNull()
    if (last == null || !contains(toward)) return extending
    val start = extending.firstOrNull()
    if (rules.all { it.turns == 0 } && start != null) return selection(start, toward, rules.startDirections)
    val index = extending.indexOf(toward)
    if (index >= 0) return extending.take(index + 1)
    firstSegmentSelection(extending, toward, rules)?.let { return it }
    val ways: List<Direction>
    if (extending.size == 1) {
        ways = rules.flatMap { it.startDirections }.toSet().sortedBy { it.serialName }
    } else {
        val geometry = PathGeometry(extending) ?: return extending
        val direction = Direction.between(extending[extending.size - 2], last) ?: return extending
        val canTurn = rules.any { geometry.direction in it.startDirections && geometry.turns < it.turns }
        ways = listOf(direction) + if (canTurn) direction.perpendiculars else emptyList()
    }
    return continuedSelection(extending, toward, ways)
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
    if (!canTurn) return straight
    val turned = continuedSelection(cells, toward, listOf(geometry.direction) + geometry.direction.perpendiculars)
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

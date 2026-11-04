package fm.apakabar.lettergrid

data class WordRule(
    val startDirections: List<Direction>,
    val turns: Int = 0,
) {
    fun matches(cells: List<Cell>): Boolean = PathGeometry(cells)?.let(::matches) ?: false

    internal fun matches(geometry: PathGeometry): Boolean =
        isValid && geometry.direction in startDirections && geometry.turns == turns

    fun matches(placement: Placement): Boolean = matches(placement.cells)

    fun canFit(
        length: Int,
        columns: Int,
        maximumRows: Int? = null,
    ): Boolean {
        val fits =
            isValid &&
                length >= minimumLength &&
                columns > 0 &&
                (maximumRows == null || (maximumRows > 0 && (length - 1) / columns < maximumRows))
        return fits &&
            PathShapes(
                columns = columns,
                length = length,
                rule = this,
                maximumShapes = 1,
                maximumRows = maximumRows,
                visitedLimit = null,
                randomized = false,
            ).cells().isNotEmpty()
    }

    val isValid: Boolean get() = startDirections.isNotEmpty() && turns in supportedTurns
    val minimumLength: Int get() = if (isValid) turns + 2 else Int.MAX_VALUE

    companion object {
        fun minimumTurns(
            toFit: Int,
            startDirections: List<Direction>,
            columns: Int,
            maximumRows: Int? = null,
        ): Int? =
            supportedTurns.firstOrNull {
                WordRule(startDirections, it).canFit(toFit, columns, maximumRows)
            }

        val supportedTurns: IntRange by lazy {
            Generation.settings.supportedTurns[0]..Generation.settings.supportedTurns[1]
        }
    }
}

internal class PathGeometry private constructor(
    val direction: Direction,
    val turns: Int,
) {
    companion object {
        operator fun invoke(cells: List<Cell>): PathGeometry? {
            val steps =
                cells
                    .takeIf { it.size > 1 && it.toSet().size == it.size }
                    ?.zipWithNext()
                    ?.mapNotNull { (start, end) -> Direction.between(start, end) }
                    ?.takeIf { it.size == cells.size - 1 }
            val bends = steps?.zipWithNext()?.filter { (previous, next) -> previous != next }
            val square = bends?.all { (previous, next) -> next in previous.perpendiculars } == true
            return if (steps != null && bends != null && square) PathGeometry(steps.first(), bends.size) else null
        }
    }
}

internal val Direction.perpendiculars: List<Direction>
    get() = Direction.entries.filter { it.rowStep * rowStep + it.columnStep * columnStep == 0 }

internal fun Direction.Companion.between(
    start: Cell,
    end: Cell,
): Direction? =
    Direction.entries.firstOrNull {
        val row = start.row.toLong() + it.rowStep
        val column = start.column.toLong() + it.columnStep
        row in Int.MIN_VALUE..Int.MAX_VALUE &&
            column in Int.MIN_VALUE..Int.MAX_VALUE &&
            end.row.toLong() == row &&
            end.column.toLong() == column
    }

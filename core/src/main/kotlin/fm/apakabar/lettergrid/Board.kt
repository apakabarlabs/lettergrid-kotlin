package fm.apakabar.lettergrid

import kotlin.math.floor
import kotlin.math.sqrt

class Placement private constructor(
    val word: String,
    val start: Cell,
    val direction: Direction,
    private val path: List<Cell>?,
) {
    constructor(word: String, start: Cell, direction: Direction) : this(word, start, direction, null)

    constructor(word: String, cells: List<Cell>) : this(checkedPath(word, cells))

    private constructor(copy: Placement) : this(copy.word, copy.start, copy.direction, copy.path)

    internal fun shifted(
        rows: Int,
        columns: Int,
    ): Placement {
        val move = { cell: Cell -> Cell(cell.row + rows, cell.column + columns) }
        return Placement(word, move(start), direction, path?.map(move))
    }

    val turns: Int get() = PathGeometry(cells)?.turns ?: 0

    val cells: List<Cell>
        get() = path ?: (0 until word.cellCount).map { start.moved(direction, it) }

    override fun equals(other: Any?): Boolean = other is Placement && other.word == word && other.cells == cells

    override fun hashCode(): Int = 31 * word.hashCode() + cells.hashCode()

    override fun toString(): String = "Placement(word=$word, cells=$cells)"

    val isPalindrome: Boolean get() = word == word.reversedCells()

    internal val reversed: Placement get() = Placement(word.reversedCells(), cells.asReversed().toList())

    internal fun reads(cells: List<Cell>): Boolean = cells == this.cells || (isPalindrome && cells == reversed.cells)

    companion object {
        fun validating(
            word: String,
            cells: List<Cell>,
        ): Placement {
            val direction =
                if (cells.size == word.cellCount && cells.size > 1 && PathGeometry(cells) != null) {
                    Direction.between(cells[0], cells[1])
                } else {
                    null
                }
            direction ?: throw Board.Failure.InvalidPlacement(word)
            return Placement(word, cells[0], direction, cells)
        }

        private fun checkedPath(
            word: String,
            cells: List<Cell>,
        ): Placement =
            try {
                validating(word, cells)
            } catch (failure: Board.Failure) {
                throw IllegalArgumentException("Invalid word path: ${failure.message}", failure)
            }
    }
}

class Board private constructor(
    val letters: List<List<String>>,
    val placements: List<Placement>,
    refuse: (Failure) -> Nothing,
) {
    sealed class Failure(
        message: String,
    ) : Exception(message) {
        data object IrregularRows : Failure("A board must have equally wide, nonempty rows.")

        data class DuplicateWord(
            val word: String,
        ) : Failure("The board contains more than one placement of $word.")

        data class InvalidPlacement(
            val word: String,
        ) : Failure("The placement of $word must contain at least two letters and match its board cells.")
    }

    val columns: Int = letters.firstOrNull()?.size ?: 0
    val rows: Int = letters.size

    init {
        try {
            validate()
        } catch (failure: Failure) {
            refuse(failure)
        }
    }

    constructor(letters: List<List<String>>, placements: List<Placement>) : this(letters, placements, { failure ->
        throw IllegalArgumentException("Invalid board: ${failure.message}", failure)
    })

    private fun fits(placement: Placement): Boolean =
        placement.word.cellCount > 1 &&
            contains(placement.start) &&
            placement.cells.size == placement.word.cellCount &&
            placement.cells.all(::contains) &&
            PathGeometry(placement.cells) != null

    private fun validate() {
        if (!(letters.isEmpty() || (columns > 0 && letters.all { it.size == columns }))) throw Failure.IrregularRows
        val words = mutableSetOf<String>()
        for (placement in placements) {
            if (!words.add(placement.word)) throw Failure.DuplicateWord(placement.word)
            if (!fits(placement) || text(placement.cells) != placement.word) throw Failure.InvalidPlacement(placement.word)
        }
    }

    operator fun get(cell: Cell): String = letters[cell.row][cell.column]

    fun contains(cell: Cell): Boolean = cell.row in 0 until rows && cell.column in 0 until columns

    fun text(along: List<Cell>): String = along.joinToString("") { this[it] }

    fun occurrences(
        word: String,
        along: List<Direction>,
    ): Int {
        val length = word.cellCount
        if (length <= 0) return 0
        val found = mutableSetOf<Set<Cell>>()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                for (direction in along) {
                    val start = Cell(row, column)
                    val cells = (0 until length).map { start.moved(direction, it) }
                    if (cells.all(::contains) && text(cells) == word) found += cells.toSet()
                }
            }
        }
        return found.size
    }

    fun selection(
        from: Cell,
        toward: Cell,
        along: List<Direction>,
    ): List<Cell> {
        if (!contains(from)) return emptyList()
        val row = toward.row.toDouble() - from.row.toDouble()
        val column = toward.column.toDouble() - from.column.toDouble()
        val reach =
            along.map { way ->
                val length = (way.rowStep * way.rowStep + way.columnStep * way.columnStep).toDouble()
                val projection = row * way.rowStep + column * way.columnStep
                Reach(way, projection / sqrt(length), projection / length)
            }
        val aligned = reach.filter { it.alignment > 0 }
        val closest = aligned.maxOfOrNull { it.alignment } ?: return listOf(from)
        val best = aligned.first { it.alignment == closest }
        if (along.any { it.isDiagonal }) {
            val travelSquared = row * row + column * column
            val snap = Generation.settings.selection.diagonalSnapCosineSquared
            if (best.alignment * best.alignment < snap * travelSquared) return listOf(from)
        }
        val nearest = floor(best.distance + 0.5)
        val distance = minOf((maxOf(rows, columns) - 1).toDouble(), nearest).toInt()
        return (0..distance).map { from.moved(best.way, it) }.takeWhile(::contains)
    }

    override fun equals(other: Any?): Boolean = other is Board && other.letters == letters && other.placements == placements

    override fun hashCode(): Int = 31 * letters.hashCode() + placements.hashCode()

    private class Reach(
        val way: Direction,
        val alignment: Double,
        val distance: Double,
    )

    companion object {
        fun validating(
            letters: List<List<String>>,
            placements: List<Placement>,
        ): Board = Board(letters, placements) { throw it }
    }
}

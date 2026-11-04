package fm.apakabar.lettergrid

internal class LetterNeighbours(
    val columns: Int,
    val rows: Int,
) {
    fun counts(
        around: Cell,
        letterAt: (Cell) -> String?,
    ): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val neighbours =
            (-1..1)
                .flatMap { rowStep -> (-1..1).map { Cell(around.row + rowStep, around.column + it) } }
                .filter { it != around && it.row in 0 until rows && it.column in 0 until columns }
        for (neighbour in neighbours) letterAt(neighbour)?.let { counts[it] = (counts[it] ?: 0) + 1 }
        return counts
    }
}

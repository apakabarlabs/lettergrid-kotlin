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
        for (rowStep in -1..1) {
            for (columnStep in -1..1) {
                if (rowStep == 0 && columnStep == 0) continue
                val neighbour = Cell(around.row + rowStep, around.column + columnStep)
                if (neighbour.row !in 0 until rows || neighbour.column !in 0 until columns) continue
                letterAt(neighbour)?.let { counts[it] = (counts[it] ?: 0) + 1 }
            }
        }
        return counts
    }
}

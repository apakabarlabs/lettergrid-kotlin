package fm.apakabar.lettergrid

object FillTestBoard {
    fun make(
        words: List<String>,
        columns: Int = 5,
        rows: Int = 5,
        rowSpacing: Int = 2,
    ): Board {
        val letters = List(rows) { MutableList(columns) { "X" } }
        val placements =
            words.mapIndexed { index, word ->
                val placement = Placement(word, Cell(index * rowSpacing, 0), Direction.RIGHT)
                for ((cell, letter) in placement.cells.zip(word.cells)) letters[cell.row][cell.column] = letter
                placement
            }
        return Board(letters, placements)
    }
}

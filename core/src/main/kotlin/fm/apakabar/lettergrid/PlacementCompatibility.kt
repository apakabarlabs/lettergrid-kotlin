package fm.apakabar.lettergrid

internal class PlacementCompatibility(
    val columns: Int,
    val rows: Int,
    val rules: List<WordRule>,
    val words: List<String>,
    val blank: String,
) {
    class Alone(
        val standsAlone: Boolean,
        val spelled: List<Int>,
    )

    fun alone(index: Int): Alone {
        val counts = mutableMapOf<String, Int>()
        for (letter in words[index].cells) counts[letter] = (counts[letter] ?: 0) + 1
        return Alone(
            standsAlone = standsAlone(words[index], counts),
            spelled = words.indices.filter { it != index && spells(words[it], counts) },
        )
    }

    fun accepts(
        placement: Placement,
        alone: Alone,
    ): Boolean {
        if (!alone.standsAlone && !placement.readsOnceAlone(rules)) return false
        return alone.spelled.none { placement.holdsReading(words[it], rules) }
    }

    fun accepts(chosen: List<Placement?>): Boolean {
        val placements = chosen.filterNotNull()
        val letters = letters(placements)
        val areas = placements.map { it.cells.toSet() }
        val nested = areas.indices.any { inner -> areas.indices.any { it != inner && areas[it].containsAll(areas[inner]) } }
        if (nested) return false
        val board = Board(letters, placements)
        val counts = letterCounts(letters)
        val alone =
            placements.all {
                standsAlone(it.word, counts) ||
                    if (placements.size == 1) it.readsOnceAlone(rules) else board.hasSingleOccurrence(it.word, rules)
            }
        if (!alone) return false
        return chosen.zip(words).all { (placement, word) ->
            placement != null || !spells(word, counts) || leavesRoom(word, board, areas)
        }
    }

    private fun letters(placements: List<Placement>): List<List<String>> = placements.letters(columns, rows, blank)

    private fun letterCounts(letters: List<List<String>>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (row in letters) {
            for (letter in row) if (letter != blank) counts[letter] = (counts[letter] ?: 0) + 1
        }
        return counts
    }

    private fun spells(
        word: String,
        counts: Map<String, Int>,
    ): Boolean {
        val needed = mutableMapOf<String, Int>()
        for (letter in word.cells) needed[letter] = (needed[letter] ?: 0) + 1
        return needed.all { (counts[it.key] ?: 0) >= it.value }
    }

    private fun standsAlone(
        word: String,
        counts: Map<String, Int>,
    ): Boolean {
        val letters = word.cells
        return letters.toSet().size == letters.size && letters.all { counts[it] == 1 }
    }

    private fun leavesRoom(
        word: String,
        board: Board,
        areas: List<Set<Cell>>,
    ): Boolean {
        val paths = linkedSetOf<List<Cell>>()
        for (reading in PathSearch(board, rules, maximumReadings = 3).readings(WordIndex(setOf(word)))) {
            if (reading.cells.asReversed().toList() !in paths) paths += reading.cells
        }
        val path = paths.firstOrNull() ?: return true
        return paths.size == 1 && areas.none { it.containsAll(path) }
    }
}

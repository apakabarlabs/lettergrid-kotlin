package fm.apakabar.lettergrid

internal object NoiseSearch {
    val maximumVisits: Int get() = Generation.settings.noise.visitLimit
}

internal fun Board.strayWordCount(
    dictionary: WordIndex,
    rules: List<WordRule>,
    lessThan: Int = Int.MAX_VALUE,
    maximumVisits: Int = NoiseSearch.maximumVisits,
    letters: ((Cell) -> String?)? = null,
): Int {
    val words = mutableSetOf<String>()
    val removed = mutableMapOf<Int, Int>()
    var visits = 0
    var exhausted = false
    val skip: (Int) -> Boolean = { node ->
        if (visits >= maximumVisits) {
            exhausted = true
            true
        } else {
            visits += 1
            (removed[node] ?: 0) >= dictionary.wordCount(below = node)
        }
    }
    PathSearch(this, rules, letters = letters).visitReadings(dictionary, skip) { reading ->
        if (covers(reading)) return@visitReadings true
        if (words.add(reading.word)) {
            for (node in dictionary.path(to = reading.word)) removed[node] = (removed[node] ?: 0) + 1
        }
        words.size < lessThan
    }
    return if (exhausted) Int.MAX_VALUE else words.size
}

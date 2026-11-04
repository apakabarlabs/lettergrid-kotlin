package fm.apakabar.lettergrid

internal class Budget(
    var value: Int,
)

internal data class PathPacking(
    val columns: Int,
    val rows: Int,
    val rules: List<WordRule>,
    val leadingRules: List<WordRule> = emptyList(),
    val downWords: IntRange = 0..Int.MAX_VALUE,
    val maxWordsPerDirection: Int? = null,
    val nodeLimit: Int = Generation.settings.packing.nodeLimit,
    val crossingRestarts: Int = Generation.settings.packing.crossingRestarts,
    val forbidsRuns: Boolean = false,
    val completes: (List<Placement>, Budget) -> Completion = { _, _ -> Completion.FITS },
) {
    sealed class Outcome {
        data class Placed(
            val placements: List<Placement>,
        ) : Outcome()

        data object Impossible : Outcome()

        data object Undetermined : Outcome()
    }

    enum class Completion { FITS, DOES_NOT_FIT, RAN_OUT }

    private class Candidate(
        val slots: List<Int>,
        val placement: Placement,
    )

    private class Search(
        val words: List<List<String>>,
        val choices: List<List<Candidate>>,
        val order: List<Int>,
        val runs: PlacedRuns?,
        val downWords: IntRange,
        val maxWordsPerDirection: Int?,
        val nodeLimit: Int,
        val standApart: (List<Placement?>) -> Boolean,
        val completes: (List<Placement>, Budget) -> Completion,
        size: Int,
        count: Int,
    ) {
        val letters = MutableList<String?>(size) { null }
        val uses = IntArray(size)
        val chosen = MutableList<Placement?>(count) { null }
        var vertical = 0
        val directionCounts = mutableMapOf<Direction, Int>()
        var nodes = 0
        var exhausted = false

        val sharedCells: Int get() = uses.sumOf { maxOf(it - 1, 0) }

        fun place(depth: Int): Boolean {
            if (depth >= order.size) {
                if (vertical !in downWords) return false
                val budget = Budget(nodeLimit - nodes)
                val completion = completes(chosen.filterNotNull(), budget)
                nodes = nodeLimit - budget.value
                return when (completion) {
                    Completion.FITS -> true
                    Completion.DOES_NOT_FIT -> false
                    Completion.RAN_OUT -> {
                        exhausted = true
                        false
                    }
                }
            }
            val index = order[depth]
            val remaining = order.size - depth - 1
            for (candidate in crossingFirst(choices[index], words[index])) {
                nodes += 1
                if (nodes > nodeLimit) {
                    exhausted = true
                    return false
                }
                val counted = vertical + if (candidate.placement.direction.isVertical) 1 else 0
                if (counted > downWords.last ||
                    counted + remaining < downWords.first ||
                    !hasDirectionRoom(candidate) ||
                    !fits(candidate, words[index])
                ) {
                    continue
                }
                apply(candidate, words[index])
                chosen[index] = candidate.placement
                vertical = counted
                countDirection(candidate, 1)
                val formsRun = runs?.formed(candidate.placement, letters, chosen) ?: false
                if (!formsRun && standApart(chosen) && place(depth + 1)) return true
                countDirection(candidate, -1)
                vertical -= if (candidate.placement.direction.isVertical) 1 else 0
                chosen[index] = null
                remove(candidate)
                if (exhausted) return false
            }
            return false
        }

        private fun hasDirectionRoom(candidate: Candidate): Boolean =
            maxWordsPerDirection?.let { (directionCounts[candidate.placement.direction] ?: 0) < it } ?: true

        private fun countDirection(
            candidate: Candidate,
            change: Int,
        ) {
            if (maxWordsPerDirection != null) {
                val direction = candidate.placement.direction
                directionCounts[direction] = (directionCounts[direction] ?: 0) + change
            }
        }

        private fun crossingFirst(
            candidates: List<Candidate>,
            word: List<String>,
        ): List<Candidate> {
            val shared =
                candidates.map { candidate ->
                    candidate.slots.zip(word).count { (slot, letter) -> letters[slot] == letter }
                }
            if (shared.none { it > 0 }) return candidates
            return candidates.indices
                .sortedWith { first, second ->
                    if (shared[first] != shared[second]) shared[second] - shared[first] else first - second
                }.map { candidates[it] }
        }

        private fun fits(
            candidate: Candidate,
            word: List<String>,
        ): Boolean = candidate.slots.zip(word).all { (slot, letter) -> letters[slot]?.let { it == letter } ?: true }

        private fun apply(
            candidate: Candidate,
            word: List<String>,
        ) {
            for ((slot, letter) in candidate.slots.zip(word)) {
                letters[slot] = letter
                uses[slot] += 1
            }
        }

        private fun remove(candidate: Candidate) {
            for (slot in candidate.slots) {
                uses[slot] -= 1
                if (uses[slot] == 0) letters[slot] = null
            }
        }
    }

    fun pack(
        words: List<String>,
        seed: ULong = 0uL,
    ): Outcome {
        require(
            leadingRules.all { first ->
                rules.any { it.turns == first.turns && it.startDirections.containsAll(first.startDirections) }
            },
        ) { "Every leading rule must lie within a rule every reading is checked against." }
        if (leadingRules.size > words.size) return Outcome.Impossible
        val random = SeededRandom(seed)
        if (words.isEmpty()) return Outcome.Impossible
        val blank = String.privateUse(avoiding = words.flatMap { it.cells }.toSet()) ?: return Outcome.Impossible
        val compatibility = PlacementCompatibility(columns, rows, rules, words, blank)
        val all = standingAlone(words, compatibility)
        if (!all.all { it.isNotEmpty() }) return Outcome.Impossible
        return restarting(words, all, compatibility, random)
    }

    private fun restarting(
        words: List<String>,
        all: List<List<Candidate>>,
        compatibility: PlacementCompatibility,
        random: SeededRandom,
    ): Outcome {
        val canCross =
            words.indices.any { first ->
                words.indices.any { it > first && words[first].cells.intersect(words[it].cells.toSet()).isNotEmpty() }
            }
        val runs = if (prunesRuns(words)) PlacedRuns(columns, rows) else null
        var best: Pair<List<Placement>, Int>? = null
        var spent = 0
        repeat(maxOf(1, crossingRestarts)) {
            val choices = all.map { random.shuffled(it) }
            val search =
                Search(
                    words = words.map { it.cells },
                    choices = choices,
                    order =
                        words.indices.sortedWith { first, second ->
                            if (choices[first].size != choices[second].size) {
                                choices[first].size - choices[second].size
                            } else {
                                first - second
                            }
                        },
                    runs = runs,
                    downWords = downWords,
                    maxWordsPerDirection = maxWordsPerDirection,
                    nodeLimit = nodeLimit - spent,
                    standApart = compatibility::accepts,
                    completes = completes,
                    size = rows * columns,
                    count = words.size,
                )
            if (!search.place(0)) {
                best?.let { return Outcome.Placed(it.first) }
                return if (search.exhausted) Outcome.Undetermined else Outcome.Impossible
            }
            spent += search.nodes
            val shared = search.sharedCells
            if (best?.let { shared > it.second } ?: true) best = search.chosen.filterNotNull() to shared
            if (shared > 0 || !canCross) return Outcome.Placed(best.first)
        }
        return best?.let { Outcome.Placed(it.first) } ?: Outcome.Impossible
    }

    private fun prunesRuns(words: List<String>): Boolean =
        forbidsRuns &&
            words.none { word ->
                val letters = word.cells
                (2 until letters.size).any { letters[it] == letters[it - 1] && letters[it] == letters[it - 2] }
            }

    private fun standingAlone(
        words: List<String>,
        compatibility: PlacementCompatibility,
    ): List<List<Candidate>> =
        words.indices.map { index ->
            val allowedRules = if (index < leadingRules.size) listOf(leadingRules[index]) else rules
            val alone = compatibility.alone(index)
            candidates(words[index], allowedRules) { compatibility.accepts(it, alone) }
        }

    private fun candidates(
        word: String,
        rules: List<WordRule>,
        accepting: (Placement) -> Boolean,
    ): List<Candidate> {
        val seen = mutableSetOf<List<Cell>>()
        val found = mutableListOf<Candidate>()
        for (rule in rules) {
            for (direction in rule.startDirections) {
                val shapes =
                    PathShapes(
                        columns = columns,
                        length = word.cellCount,
                        rule = WordRule(listOf(direction), rule.turns),
                        maximumShapes = Int.MAX_VALUE,
                        maximumRows = rows,
                        visitedLimit = null,
                        randomized = false,
                    ).cells()
                for (shape in shapes) {
                    val height = (shape.maxOfOrNull { it.row } ?: 0) + 1
                    val width = (shape.maxOfOrNull { it.column } ?: 0) + 1
                    if (height > rows || width > columns || !seen.add(shape)) continue
                    val lone = Placement(word, shape)
                    if (!accepting(lone)) continue
                    for (row in 0..(rows - height)) {
                        for (column in 0..(columns - width)) {
                            val placement = lone.shifted(row, column)
                            found += Candidate(placement.cells.map { it.row * columns + it.column }, placement)
                        }
                    }
                }
            }
        }
        return found
    }
}

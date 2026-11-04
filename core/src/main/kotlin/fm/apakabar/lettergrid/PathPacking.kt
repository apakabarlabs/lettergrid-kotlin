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

    private class DirectionCounts(
        val maximum: Int?,
    ) {
        private val counts = mutableMapOf<Direction, Int>()

        fun hasRoom(direction: Direction): Boolean = maximum?.let { (counts[direction] ?: 0) < it } ?: true

        fun count(
            direction: Direction,
            change: Int,
        ) {
            if (maximum != null) counts[direction] = (counts[direction] ?: 0) + change
        }
    }

    private inner class Search(
        val words: List<List<String>>,
        val choices: List<List<Candidate>>,
        val order: List<Int>,
        val runs: PlacedRuns?,
        val nodeLimit: Int,
        val compatibility: PlacementCompatibility,
    ) {
        val letters = MutableList<String?>(rows * columns) { null }
        val uses = IntArray(rows * columns)
        val chosen = MutableList<Placement?>(words.size) { null }
        var vertical = 0
        val directionCounts = DirectionCounts(maxWordsPerDirection)
        var nodes = 0
        var exhausted = false

        val sharedCells: Int get() = uses.sumOf { maxOf(it - 1, 0) }

        fun place(depth: Int): Boolean = if (depth >= order.size) complete() else placeNext(depth)

        private fun complete(): Boolean {
            if (vertical !in downWords) return false
            val budget = Budget(nodeLimit - nodes)
            val completion = completes(chosen.filterNotNull(), budget)
            nodes = nodeLimit - budget.value
            if (completion == Completion.RAN_OUT) exhausted = true
            return completion == Completion.FITS
        }

        private fun placeNext(depth: Int): Boolean {
            val index = order[depth]
            val remaining = order.size - depth - 1
            return crossingFirst(choices[index], words[index]).any { candidate ->
                !exhausted && tries(candidate, index, remaining, depth)
            }
        }

        private fun tries(
            candidate: Candidate,
            index: Int,
            remaining: Int,
            depth: Int,
        ): Boolean {
            nodes += 1
            if (nodes > nodeLimit) exhausted = true
            val counted = vertical + if (candidate.placement.direction.isVertical) 1 else 0
            val withinDownWords = counted <= downWords.last && counted + remaining >= downWords.first
            if (exhausted || !withinDownWords) return false
            return directionCounts.hasRoom(candidate.placement.direction) && fits(candidate, words[index]) &&
                descends(candidate, index, counted, depth)
        }

        private fun descends(
            candidate: Candidate,
            index: Int,
            counted: Int,
            depth: Int,
        ): Boolean {
            apply(candidate, words[index])
            chosen[index] = candidate.placement
            val previousVertical = vertical
            vertical = counted
            directionCounts.count(candidate.placement.direction, 1)
            val formsRun = runs?.formed(candidate.placement, letters, chosen) ?: false
            val placed = !formsRun && compatibility.accepts(chosen) && place(depth + 1)
            if (!placed) {
                directionCounts.count(candidate.placement.direction, -1)
                vertical = previousVertical
                chosen[index] = null
                remove(candidate)
            }
            return placed
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
        val sized = leadingRules.size <= words.size && words.isNotEmpty()
        val blank = if (sized) String.privateUse(avoiding = words.flatMap { it.cells }.toSet()) else null
        val compatibility = blank?.let { PlacementCompatibility(columns, rows, rules, words, it) }
        val all = compatibility?.let { standingAlone(words, it) }
        return if (compatibility != null && all != null && all.all { it.isNotEmpty() }) {
            restarting(words, all, compatibility, SeededRandom(seed))
        } else {
            Outcome.Impossible
        }
    }

    private fun restarting(
        words: List<String>,
        all: List<List<Candidate>>,
        compatibility: PlacementCompatibility,
        random: SeededRandom,
    ): Outcome {
        val canCross = canCross(words)
        val runs = if (prunesRuns(words)) PlacedRuns(columns, rows) else null
        var best: Pair<List<Placement>, Int>? = null
        var spent = 0
        var outcome: Outcome? = null
        var attempts = maxOf(1, crossingRestarts)
        while (outcome == null && attempts > 0) {
            attempts -= 1
            val choices = all.map { random.shuffled(it) }
            val search = Search(words.map { it.cells }, choices, order(choices), runs, nodeLimit - spent, compatibility)
            if (search.place(0)) {
                spent += search.nodes
                val shared = search.sharedCells
                val kept = better(best, search.chosen.filterNotNull() to shared)
                best = kept
                if (shared > 0 || !canCross) outcome = Outcome.Placed(kept.first)
            } else {
                outcome = best?.let { Outcome.Placed(it.first) }
                    ?: if (search.exhausted) Outcome.Undetermined else Outcome.Impossible
            }
        }
        return outcome ?: best?.let { Outcome.Placed(it.first) } ?: Outcome.Impossible
    }

    private fun canCross(words: List<String>): Boolean =
        words.indices.any { first ->
            words.indices.any { it > first && words[first].cells.intersect(words[it].cells.toSet()).isNotEmpty() }
        }

    private fun better(
        previous: Pair<List<Placement>, Int>?,
        found: Pair<List<Placement>, Int>,
    ): Pair<List<Placement>, Int> = if (previous == null || found.second > previous.second) found else previous

    private fun order(choices: List<List<Candidate>>): List<Int> =
        choices.indices.sortedWith { first, second ->
            if (choices[first].size != choices[second].size) {
                choices[first].size - choices[second].size
            } else {
                first - second
            }
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
        val oneCellDirections = mutableSetOf<Direction>()
        val shapes =
            rules.flatMap { rule ->
                rule.startDirections.flatMap { direction ->
                    PathShapes(
                        columns = columns,
                        length = word.cellCount,
                        rule = WordRule(listOf(direction), rule.turns),
                        maximumShapes = Int.MAX_VALUE,
                        maximumRows = rows,
                        visitedLimit = null,
                        randomized = false,
                    ).cells().map { direction to it }
                }
            }
        val found = mutableListOf<Candidate>()
        for ((direction, shape) in shapes) {
            val height = (shape.maxOfOrNull { it.row } ?: 0) + 1
            val width = (shape.maxOfOrNull { it.column } ?: 0) + 1
            val lone =
                when {
                    height > rows || width > columns -> null
                    shape.size == 1 -> Placement(word, shape[0], direction).takeIf { oneCellDirections.add(direction) }
                    else -> Placement(word, shape).takeIf { seen.add(shape) }
                }
            if (lone != null && accepting(lone)) found += shifts(lone, height, width)
        }
        return found
    }

    private fun shifts(
        lone: Placement,
        height: Int,
        width: Int,
    ): List<Candidate> =
        (0..(rows - height)).flatMap { row ->
            (0..(columns - width)).map { column ->
                val placement = lone.shifted(row, column)
                Candidate(placement.cells.map { it.row * columns + it.column }, placement)
            }
        }
}

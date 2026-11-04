package fm.apakabar.lettergrid

fun BoardGenerator.accepts(
    words: List<String>,
    firstRule: WordRule? = null,
): Boolean = accepts(words, leadingRules = listOfNotNull(firstRule))

fun BoardGenerator.accepts(
    words: List<String>,
    leadingRules: List<WordRule>,
): Boolean =
    hasValidDirectionLimit &&
        leadingRules.size <= words.size &&
        leadingRules.all(::enables) &&
        WordAcceptance(this, words.map { it.cellUppercased }, leadingRules).accepts()

private class WordAcceptance(
    val generator: BoardGenerator,
    val upper: List<String>,
    val leadingRules: List<WordRule>,
) {
    fun accepts(): Boolean =
        acceptsWords() &&
            fittingRules().let { fitting ->
                fitting.all { it.isNotEmpty() } && spreads(fitting) && standsUp(fitting)
            }

    private fun acceptsWords(): Boolean {
        val excluded = generator.conditionalCharacters + upper.flatMap { it.cells }.filter { !it.isLetterCell }
        return upper.isNotEmpty() &&
            upper.toSet().size == upper.size &&
            upper.all { it.cellCount > 1 } &&
            upper.none { word -> upper.any { it != word && it.containsCells(word) } } &&
            generator.filler.letters(excluding = excluded) != null
    }

    private fun fittingRules(): List<List<WordRule>> =
        upper.indices.map { index ->
            (if (index < leadingRules.size) listOf(leadingRules[index]) else generator.rules)
                .flatMap { rule -> rule.startDirections.map { WordRule(listOf(it), rule.turns) } }
                .filter { it.canFit(upper[index].cellCount, generator.columns, generator.maxRows) }
        }

    private fun spreads(fitting: List<List<WordRule>>): Boolean {
        val maximum = generator.maxWordsPerDirection ?: return true
        val possible = fitting.map { rules -> rules.map { it.startDirections[0] }.toSet() }
        val directions = possible.flatten().toSet()
        return (upper.size - 1) / maximum < directions.size &&
            directions.all { direction -> possible.count { it == setOf(direction) } <= maximum }
    }

    private fun standsUp(fitting: List<List<WordRule>>): Boolean {
        val mustStand = fitting.count { rules -> rules.all { it.startDirections[0].isVertical } }
        val canStand = fitting.count { rules -> rules.any { it.startDirections[0].isVertical } }
        return mustStand <= generator.downWords.last && canStand >= generator.downWords.first
    }
}

internal fun BoardGenerator.packedRequest(
    words: List<String>,
    firstDirection: Direction?,
    leadingRules: List<WordRule>,
    maxRows: Int,
): BoardRequest {
    if (!accepts(words, leadingRules)) throw BoardGenerator.Failure.NoRoom(words, columns, rows)
    return if (this.maxRows == null) {
        growingRequest(words, firstDirection, leadingRules, maxRows)
    } else {
        boundedRequest(words, firstDirection, leadingRules, maxRows)
    }
}

private fun BoardGenerator.boundedRequest(
    words: List<String>,
    firstDirection: Direction?,
    leadingRules: List<WordRule>,
    maxRows: Int,
): BoardRequest {
    var unsettled = false
    val layout =
        (rows..maxRows).asSequence().firstNotNullOfOrNull { height ->
            when (val outcome = packing(height, leadingRules, words).pack(words, BoardGenerator.LAYOUT_SEED)) {
                is PathPacking.Outcome.Placed -> Layout(height, outcome.placements)
                PathPacking.Outcome.Impossible -> null
                PathPacking.Outcome.Undetermined -> null.also { unsettled = true }
            }
        }
    return layout?.let { BoardRequest(words, firstDirection, this, leadingRules, it) }
        ?: throw if (unsettled) {
            BoardGenerator.Failure.Unsettled(words, columns, maxRows)
        } else {
            BoardGenerator.Failure.NoRoom(words, columns, rows)
        }
}

private fun BoardGenerator.growingRequest(
    words: List<String>,
    firstDirection: Direction?,
    leadingRules: List<WordRule>,
    tallest: Int,
): BoardRequest {
    fun outcome(height: Int) = packing(height, leadingRules, words).pack(words, BoardGenerator.LAYOUT_SEED)

    fun placed(height: Int) = (outcome(height) as? PathPacking.Outcome.Placed)?.placements

    placed(rows)?.let { return BoardRequest(words, firstDirection, this, leadingRules, Layout(rows, it)) }
    var found: Layout =
        when (val outcome = outcome(tallest)) {
            is PathPacking.Outcome.Placed -> Layout(tallest, outcome.placements)
            PathPacking.Outcome.Impossible -> throw BoardGenerator.Failure.NoRoom(words, columns, rows)
            PathPacking.Outcome.Undetermined -> throw BoardGenerator.Failure.Unsettled(words, columns, tallest)
        }
    var lowest = rows + 1
    while (lowest < found.rows) {
        val middle = (lowest + found.rows) / 2
        val placements = placed(middle)
        if (placements != null) found = Layout(middle, placements) else lowest = middle + 1
    }
    return BoardRequest(words, firstDirection, this, leadingRules, found)
}

internal fun BoardGenerator.packedLayout(
    request: BoardRequest,
    seed: ULong,
): Layout {
    val layout = request.layout
    val placed =
        if (seed == BoardGenerator.LAYOUT_SEED) {
            null
        } else {
            packing(layout.rows, request.leadingRules, request.words).pack(request.words, seed) as?
                PathPacking.Outcome.Placed
        }
    return placed?.let { Layout(layout.rows, it.placements) } ?: layout
}

private fun BoardGenerator.requiredSymbols(words: List<String>): Set<String> {
    val used = words.flatMap { it.cells }.toSet()
    return (conditionalCharacters + used.filter { !it.isLetterCell }).intersect(used)
}

private fun BoardGenerator.packing(
    height: Int,
    leadingRules: List<WordRule>,
    words: List<String>,
): PathPacking {
    val required = requiredSymbols(words)
    val columns = columns
    val rules = rules
    val avoidsRuns = requiresRunFreeTargets
    return PathPacking(
        columns = columns,
        rows = height,
        rules = rules,
        leadingRules = leadingRules,
        downWords = downWords,
        maxWordsPerDirection = maxWordsPerDirection,
        nodeLimit =
            if (maxRows ==
                null
            ) {
                BoardGenerator.growingHeightNodeLimit
            } else {
                Generation.settings.packing.nodeLimit
            },
        forbidsRuns = avoidsRuns,
        completes = { placements, budget ->
            val layout = Layout(height, placements)
            val needing = layout.charactersToCopyIfRoom(columns)
            if ((avoidsRuns && layout.hasForbiddenTargetRun(columns)) || needing == null) {
                PathPacking.Completion.DOES_NOT_FIT
            } else {
                ConditionalFill.roomForDecoys(required.intersect(needing), layout, columns, rules, budget)
            }
        },
    )
}

internal fun String.containsCells(other: String): Boolean {
    val mine = cells
    val theirs = other.cells
    if (theirs.isEmpty()) return true
    return (0..(mine.size - theirs.size)).any { mine.subList(it, it + theirs.size) == theirs }
}

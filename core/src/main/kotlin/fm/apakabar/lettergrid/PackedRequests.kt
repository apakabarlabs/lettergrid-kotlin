package fm.apakabar.lettergrid

fun BoardGenerator.accepts(
    words: List<String>,
    firstRule: WordRule? = null,
): Boolean = accepts(words, leadingRules = listOfNotNull(firstRule))

fun BoardGenerator.accepts(
    words: List<String>,
    leadingRules: List<WordRule>,
): Boolean {
    if (!hasValidDirectionLimit || leadingRules.size > words.size || !leadingRules.all(::enables)) return false
    val upper = words.map { it.cellUppercased }
    val excluded = conditionalCharacters + upper.flatMap { it.cells }.filter { !it.isLetterCell }
    val acceptable =
        upper.isNotEmpty() &&
            upper.toSet().size == upper.size &&
            upper.all { it.cellCount > 1 } &&
            upper.none { word -> upper.any { it != word && it.containsCells(word) } } &&
            filler.letters(excluding = excluded) != null
    if (!acceptable) return false
    val fitting =
        upper.indices.map { index ->
            (if (index < leadingRules.size) listOf(leadingRules[index]) else rules)
                .flatMap { rule -> rule.startDirections.map { WordRule(listOf(it), rule.turns) } }
                .filter { it.canFit(upper[index].cellCount, columns, maxRows) }
        }
    if (!fitting.all { it.isNotEmpty() }) return false
    val maximum = maxWordsPerDirection
    if (maximum != null) {
        val possible = fitting.map { rules -> rules.map { it.startDirections[0] }.toSet() }
        val directions = possible.flatten().toSet()
        val spread =
            (upper.size - 1) / maximum < directions.size &&
                directions.all { direction -> possible.count { it == setOf(direction) } <= maximum }
        if (!spread) return false
    }
    val mustStand = fitting.count { rules -> rules.all { it.startDirections[0].isVertical } }
    val canStand = fitting.count { rules -> rules.any { it.startDirections[0].isVertical } }
    return mustStand <= downWords.last && canStand >= downWords.first
}

internal fun BoardGenerator.packedRequest(
    words: List<String>,
    firstDirection: Direction?,
    leadingRules: List<WordRule>,
    maxRows: Int,
): BoardRequest {
    if (!accepts(words, leadingRules)) throw BoardGenerator.Failure.NoRoom(words, columns, rows)
    if (this.maxRows == null) return growingRequest(words, firstDirection, leadingRules, maxRows)
    var unsettled = false
    for (height in rows..maxRows) {
        when (val outcome = packing(height, leadingRules, words).pack(words, BoardGenerator.LAYOUT_SEED)) {
            is PathPacking.Outcome.Placed ->
                return BoardRequest(words, firstDirection, this, leadingRules, Layout(height, outcome.placements))
            PathPacking.Outcome.Impossible -> continue
            PathPacking.Outcome.Undetermined -> unsettled = true
        }
    }
    if (unsettled) throw BoardGenerator.Failure.Unsettled(words, columns, maxRows)
    throw BoardGenerator.Failure.NoRoom(words, columns, rows)
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
    if (seed == BoardGenerator.LAYOUT_SEED) return layout
    val outcome = packing(layout.rows, request.leadingRules, request.words).pack(request.words, seed)
    val placed = outcome as? PathPacking.Outcome.Placed ?: return layout
    return Layout(layout.rows, placed.placements)
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
        nodeLimit = if (maxRows == null) BoardGenerator.growingHeightNodeLimit else Generation.settings.packing.nodeLimit,
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

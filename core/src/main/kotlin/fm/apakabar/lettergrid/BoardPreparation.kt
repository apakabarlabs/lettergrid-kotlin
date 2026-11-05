package fm.apakabar.lettergrid

import fm.apakabar.lettergrid.BoardGenerator.Failure
import kotlin.time.Duration
import kotlin.time.TimeSource

internal fun BoardGenerator.placementRequest(
    words: List<String>,
    firstRule: WordRule?,
): BoardRequest {
    validateConfiguration()
    if (firstRule != null && !enables(firstRule)) throw Failure.InvalidConfiguration
    return checkedTargets(words, listOfNotNull(firstRule))
}

internal fun BoardGenerator.placementRequest(
    words: List<String>,
    leadingRules: List<WordRule>,
): BoardRequest {
    validateConfiguration()
    if (!leadingRules.all(::enables)) throw Failure.InvalidConfiguration
    return checkedTargets(words, leadingRules)
}

private fun BoardGenerator.checkedTargets(
    words: List<String>,
    leadingRules: List<WordRule>,
): BoardRequest {
    val upper = words.map { it.cellUppercased }
    return try {
        copy(requiresRunFreeTargets = true).targetRequest(upper, leadingRules)
    } catch (_: Failure.NoRoom) {
        copy(requiresRunFreeTargets = false).targetRequest(upper, leadingRules)
        throw Failure.Unsettled(upper, columns, maxRows ?: rows)
    }
}

internal fun BoardGenerator.finishPreparation(
    request: BoardRequest,
    observing: ((Duration, Duration) -> Unit)? = null,
): BoardRequest {
    val seeds = Generation.settings.fill.seeds
    for (seed in 0uL until seeds.toULong()) {
        val start = observing?.let { TimeSource.Monotonic.markNow() }
        val targets = request.targets(seed)
        val repacking = start?.elapsedNow()
        val fillStart = observing?.let { TimeSource.Monotonic.markNow() }
        val fallback = RunFreeFill(targets, weights, rules, conditionalCharacters).filled(seed)
        if (repacking != null && fillStart != null) observing.invoke(repacking, fillStart.elapsedNow())
        if (fallback != null) return request.avoidingRuns(fallback, weights, conditionalCharacters)
    }
    throw Failure.Unsettled(request.words, columns, maxRows ?: rows)
}

private fun BoardGenerator.targetRequest(
    words: List<String>,
    leadingRules: List<WordRule>,
): BoardRequest {
    val symbols = conditionalCharacters + words.flatMap { it.cells }.filter { !it.isLetterCell }
    val generator =
        if (symbols.isEmpty()) {
            this
        } else {
            val ordinary = weights.letters(excluding = symbols) ?: throw Failure.NoRoom(words, columns, rows)
            copy(weights = ordinary)
        }
    return generator.packedRequest(words, leadingRules, maxRows ?: (rows + words.sumOf { it.cellCount }))
}

private fun BoardGenerator.validateConfiguration() {
    val valid =
        columns > 0 && rows > 0 && directions.isNotEmpty() && downWords.first >= 0 &&
            rules.all { it.isValid } && (maxRows?.let { it >= rows } ?: true) && hasValidDirectionLimit
    if (!valid) throw Failure.InvalidConfiguration
}

internal fun BoardGenerator.targets(
    request: BoardRequest,
    seed: ULong,
): Board = Board.targets(packedLayout(request, seed), columns)

package fm.apakabar.lettergrid

import kotlin.time.Duration
import kotlin.time.TimeSource

class BoardGenerator private constructor(
    val columns: Int,
    val rows: Int,
    val maxRows: Int?,
    val directions: List<Direction>,
    filler: LetterWeights,
    val downWords: IntRange,
    val conditionalCharacters: Set<String>,
    val maxWordsPerDirection: Int?,
    rules: List<WordRule>?,
    internal val requiresRunFreeTargets: Boolean = false,
) {
    sealed class Failure(
        message: String,
    ) : Exception(message) {
        data class NoRoom(
            val words: List<String>,
            val columns: Int,
            val rows: Int,
        ) : Failure(
                "Could not prepare a checked grid $columns wide with minimum height $rows for " +
                    words.joinToString(", "),
            )

        data class Unsettled(
            val words: List<String>,
            val columns: Int,
            val rows: Int,
        ) : Failure(
                "No grid $columns wide and up to $rows high was settled for " + words.joinToString(", ") +
                    ": the search ran out, or every layout found leaves three equal letters in a line.",
            )

        data object InvalidConfiguration : Failure(
            "Grid dimensions must be positive, directions nonempty, rules valid and the vertical " +
                "minimum nonnegative. A maximum height must be at least the minimum. " +
                "A per-direction word limit must be positive.",
        )
    }

    var filler: LetterWeights = filler
        private set
    val rules: List<WordRule> = rules ?: listOf(WordRule(directions))
    internal val usesPathRules: Boolean = rules != null

    constructor(
        columns: Int,
        rows: Int,
        maxRows: Int? = null,
        directions: List<Direction>,
        filler: LetterWeights,
        downWords: IntRange = 0..Int.MAX_VALUE,
        conditionalCharacters: Set<String> = emptySet(),
        maxWordsPerDirection: Int? = null,
    ) : this(
        columns,
        rows,
        maxRows,
        directions,
        filler,
        downWords,
        conditionalCharacters.map { it.cellUppercased }.toSet(),
        maxWordsPerDirection,
        null,
    )

    private fun copy(
        filler: LetterWeights = this.filler,
        requiresRunFreeTargets: Boolean = this.requiresRunFreeTargets,
    ): BoardGenerator =
        BoardGenerator(
            columns,
            rows,
            maxRows,
            directions,
            filler,
            downWords,
            conditionalCharacters,
            maxWordsPerDirection,
            if (usesPathRules) rules else null,
            requiresRunFreeTargets,
        )

    fun request(
        words: List<String>,
        firstDirection: Direction? = null,
        firstRule: WordRule? = null,
    ): BoardRequest = finishPreparation(placementRequest(words, firstDirection, firstRule))

    fun checkPlacement(
        words: List<String>,
        firstDirection: Direction? = null,
        firstRule: WordRule? = null,
    ) {
        placementRequest(words, firstDirection, firstRule)
    }

    fun checkPlacement(
        words: List<String>,
        leadingRules: List<WordRule>,
    ) {
        placementRequest(words, leadingRules)
    }

    private fun placementRequest(
        words: List<String>,
        firstDirection: Direction?,
        firstRule: WordRule?,
    ): BoardRequest {
        validateConfiguration()
        val invalid =
            if (usesPathRules) {
                firstDirection != null || (firstRule != null && firstRule !in rules)
            } else {
                firstRule != null
            }
        if (invalid) throw Failure.InvalidConfiguration
        if (!usesPathRules && firstDirection != null && firstDirection !in directions) {
            throw Failure.NoRoom(words.map { it.cellUppercased }, columns, rows)
        }
        val leading =
            if (usesPathRules) listOfNotNull(firstRule) else listOfNotNull(firstDirection?.let { WordRule(listOf(it)) })
        return checkedTargets(words, firstDirection, leading)
    }

    fun request(
        words: List<String>,
        leadingRules: List<WordRule>,
    ): BoardRequest = finishPreparation(placementRequest(words, leadingRules))

    internal fun placementRequest(
        words: List<String>,
        leadingRules: List<WordRule>,
    ): BoardRequest {
        validateConfiguration()
        if (!usesPathRules || !leadingRules.all(::enables)) throw Failure.InvalidConfiguration
        return checkedTargets(words, null, leadingRules)
    }

    internal fun enables(rule: WordRule): Boolean =
        rule.isValid && rules.any { it.turns == rule.turns && it.startDirections.containsAll(rule.startDirections) }

    private fun checkedTargets(
        words: List<String>,
        firstDirection: Direction?,
        leadingRules: List<WordRule>,
    ): BoardRequest {
        val upper = words.map { it.cellUppercased }
        return try {
            copy(requiresRunFreeTargets = true).targetRequest(upper, firstDirection, leadingRules)
        } catch (_: Failure.NoRoom) {
            copy(requiresRunFreeTargets = false).targetRequest(upper, firstDirection, leadingRules)
            throw Failure.Unsettled(upper, columns, maxRows ?: rows)
        }
    }

    internal fun finishPreparation(
        request: BoardRequest,
        observing: ((Duration, Duration) -> Unit)? = null,
    ): BoardRequest {
        val seeds = Generation.settings.fill.seeds
        for (seed in 0uL until seeds.toULong()) {
            val start = observing?.let { TimeSource.Monotonic.markNow() }
            val targets = request.targets(seed)
            val repacking = start?.elapsedNow()
            val fillStart = observing?.let { TimeSource.Monotonic.markNow() }
            val fallback = RunFreeFill(targets, filler, rules, conditionalCharacters).filled(seed)
            if (repacking != null && fillStart != null) observing.invoke(repacking, fillStart.elapsedNow())
            if (fallback != null) return request.avoidingRuns(fallback, filler, conditionalCharacters)
        }
        throw Failure.Unsettled(request.words, columns, maxRows ?: rows)
    }

    private fun targetRequest(
        words: List<String>,
        firstDirection: Direction?,
        leadingRules: List<WordRule>,
    ): BoardRequest {
        val symbols = conditionalCharacters + words.flatMap { it.cells }.filter { !it.isLetterCell }
        var generator = this
        if (symbols.isNotEmpty()) {
            val ordinary = filler.letters(excluding = symbols) ?: throw Failure.NoRoom(words, columns, rows)
            generator = copy(filler = ordinary)
        }
        return generator.packedRequest(
            words,
            firstDirection,
            leadingRules,
            maxRows ?: (rows + words.sumOf { it.cellCount }),
        )
    }

    private fun validateConfiguration() {
        val valid =
            columns > 0 &&
                rows > 0 &&
                directions.isNotEmpty() &&
                downWords.first >= 0 &&
                rules.all { it.isValid } &&
                (maxRows == null || maxRows >= rows) &&
                hasValidDirectionLimit
        if (!valid) throw Failure.InvalidConfiguration
    }

    internal val hasValidDirectionLimit: Boolean get() = maxWordsPerDirection?.let { it > 0 } ?: true

    internal fun targets(
        request: BoardRequest,
        seed: ULong,
    ): Board = Board.targets(packedLayout(request, seed), columns)

    fun board(
        words: List<String>,
        seed: ULong,
        firstDirection: Direction? = null,
    ): Board = request(words, firstDirection).board(seed)

    override fun equals(other: Any?): Boolean =
        other is BoardGenerator &&
            other.columns == columns &&
            other.rows == rows &&
            other.maxRows == maxRows &&
            other.directions == directions &&
            other.filler == filler &&
            other.downWords == downWords &&
            other.rules == rules &&
            other.maxWordsPerDirection == maxWordsPerDirection &&
            other.usesPathRules == usesPathRules &&
            other.conditionalCharacters == conditionalCharacters &&
            other.requiresRunFreeTargets == requiresRunFreeTargets

    override fun hashCode(): Int = listOf(columns, rows, maxRows, directions, filler, downWords, rules).hashCode()

    companion object {
        operator fun invoke(
            columns: Int,
            rows: Int,
            maxRows: Int? = null,
            rules: List<WordRule>,
            filler: LetterWeights,
            downWords: IntRange = 0..Int.MAX_VALUE,
            conditionalCharacters: Set<String> = emptySet(),
            maxWordsPerDirection: Int? = null,
        ): BoardGenerator =
            BoardGenerator(
                columns,
                rows,
                maxRows,
                rules.startDirections,
                filler,
                downWords,
                conditionalCharacters.map { it.cellUppercased }.toSet(),
                maxWordsPerDirection,
                rules,
            )

        internal const val LAYOUT_SEED: ULong = 0uL

        internal val growingHeightNodeLimit: Int get() = Generation.settings.packing.growingHeightNodeLimit
    }
}

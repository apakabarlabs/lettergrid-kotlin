package fm.apakabar.lettergrid

class BoardGenerator private constructor(
    val size: BoardSize,
    val rules: List<WordRule>,
    val filler: Filler,
    val limits: PlacementLimits,
    internal val weights: LetterWeights,
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
            "Grid dimensions must be positive, rules nonempty and valid, and the vertical " +
                "minimum nonnegative. A maximum height must be at least the minimum. " +
                "A per-direction word limit must be positive. Leading rules must be enabled.",
        )
    }

    internal val columns: Int get() = size.columns
    internal val rows: Int get() = size.rows
    internal val maxRows: Int? get() = size.maxRows
    internal val directions: List<Direction> get() = rules.startDirections
    internal val downWords: IntRange get() = limits.downWords
    internal val conditionalCharacters: Set<String> get() = filler.conditionalCharacters
    internal val maxWordsPerDirection: Int? get() = limits.maxWordsPerDirection

    constructor(
        size: BoardSize,
        rules: List<WordRule>,
        filler: Filler,
        limits: PlacementLimits = PlacementLimits(),
    ) : this(size, rules, filler, limits, filler.weights)

    internal fun copy(
        weights: LetterWeights = this.weights,
        requiresRunFreeTargets: Boolean = this.requiresRunFreeTargets,
    ): BoardGenerator =
        BoardGenerator(
            size,
            rules,
            filler,
            limits,
            weights,
            requiresRunFreeTargets,
        )

    internal fun enables(rule: WordRule): Boolean =
        rule.isValid && rules.any { it.turns == rule.turns && it.startDirections.containsAll(rule.startDirections) }

    internal val hasValidDirectionLimit: Boolean get() = maxWordsPerDirection?.let { it > 0 } ?: true

    fun request(
        words: List<String>,
        firstRule: WordRule? = null,
    ): BoardRequest = finishPreparation(placementRequest(words, firstRule))

    fun request(
        words: List<String>,
        leadingRules: List<WordRule>,
    ): BoardRequest = finishPreparation(placementRequest(words, leadingRules))

    fun checkPlacement(
        words: List<String>,
        firstRule: WordRule? = null,
    ) {
        placementRequest(words, firstRule)
    }

    fun checkPlacement(
        words: List<String>,
        leadingRules: List<WordRule>,
    ) {
        placementRequest(words, leadingRules)
    }

    fun board(
        words: List<String>,
        seed: ULong,
        firstRule: WordRule? = null,
    ): Board = request(words, firstRule).board(seed)

    override fun equals(other: Any?): Boolean =
        other is BoardGenerator &&
            other.size == size &&
            other.filler == filler &&
            other.weights == weights &&
            other.limits == limits &&
            other.rules == rules &&
            other.requiresRunFreeTargets == requiresRunFreeTargets

    override fun hashCode(): Int = listOf(size, rules, filler, limits, weights, requiresRunFreeTargets).hashCode()

    companion object {
        internal const val LAYOUT_SEED: ULong = 0uL

        internal val growingHeightNodeLimit: Int get() = Generation.settings.packing.growingHeightNodeLimit
    }
}

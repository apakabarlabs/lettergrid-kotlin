package fm.apakabar.lettergrid

class BoardRequest internal constructor(
    val words: List<String>,
    val firstDirection: Direction?,
    private val generator: BoardGenerator,
    val leadingRules: List<WordRule>,
    internal val layout: Layout,
    private val runFree: RunFreeFallback? = null,
) {
    internal data class RunFreeFallback(
        val board: Board,
        val weights: LetterWeights,
        val symbols: Set<String>,
    )

    val firstRule: WordRule? get() = leadingRules.firstOrNull()
    val maxWordsPerDirection: Int? get() = generator.maxWordsPerDirection

    fun board(seed: ULong): Board {
        val runFree = checkNotNull(runFree) { "A request leaves preparation with its run-free fallback" }
        val candidate =
            RunFreeFill(targets(seed), runFree.weights, generator.rules, runFree.symbols).filled(seed)
                ?: return runFree.board
        return if (candidate.hasAddedEqualPair && !runFree.board.hasAddedEqualPair) runFree.board else candidate
    }

    internal fun targets(seed: ULong): Board = generator.targets(this, seed)

    internal fun avoidingRuns(
        fallback: Board,
        weights: LetterWeights,
        symbols: Set<String>,
    ): BoardRequest =
        BoardRequest(words, firstDirection, generator, leadingRules, layout, RunFreeFallback(fallback, weights, symbols))

    override fun equals(other: Any?): Boolean =
        other is BoardRequest &&
            other.words == words &&
            other.firstDirection == firstDirection &&
            other.generator == generator &&
            other.leadingRules == leadingRules &&
            other.layout == layout &&
            other.runFree == runFree

    override fun hashCode(): Int = listOf(words, firstDirection, generator, leadingRules, layout, runFree).hashCode()
}

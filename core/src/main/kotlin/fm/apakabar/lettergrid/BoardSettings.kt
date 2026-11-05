package fm.apakabar.lettergrid

data class BoardSize(
    val columns: Int,
    val rows: Int,
    val maxRows: Int? = null,
)

class Filler(
    val weights: LetterWeights,
    conditionalCharacters: Set<String> = emptySet(),
) {
    val conditionalCharacters: Set<String> = conditionalCharacters.map { it.cellUppercased }.toSet()

    override fun equals(other: Any?): Boolean =
        other is Filler && other.weights == weights && other.conditionalCharacters == this.conditionalCharacters

    override fun hashCode(): Int = 31 * weights.hashCode() + conditionalCharacters.hashCode()
}

data class PlacementLimits(
    val downWords: IntRange = 0..Int.MAX_VALUE,
    val maxWordsPerDirection: Int? = null,
)

data class RoundOptions(
    val seconds: Int? = null,
    val introduces: Boolean = false,
    val hintOrder: HintOrder = HintOrder.FROM_START,
)

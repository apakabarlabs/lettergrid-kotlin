package fm.apakabar.lettergrid

data class RoundProgress(
    val found: List<String> = emptyList(),
    val revealed: Map<String, Int> = emptyMap(),
    val secondsLeft: Int? = null,
    val isLost: Boolean = false,
) {
    sealed class Failure(
        message: String,
    ) : Exception(message) {
        data class UnknownWord(
            val word: String,
        ) : Failure("The saved word $word is not on this board.")

        data class DuplicateFoundWord(
            val word: String,
        ) : Failure("The saved word $word is marked found more than once.")

        data class InvalidRevealCount(
            val word: String,
            val count: Int,
        ) : Failure("The saved revealed-letter count $count is invalid for $word.")

        data class NegativeSeconds(
            val seconds: Int,
        ) : Failure("The saved countdown has negative seconds: $seconds.")
    }

    fun validate(board: Board) {
        failure(board)?.let { throw it }
    }

    private fun failure(board: Board): Failure? {
        val words = board.placements.associate { it.word to it.word.cellCount }
        val seen = mutableSetOf<String>()
        val foundFailure =
            found.firstNotNullOfOrNull { word ->
                when {
                    word !in words -> Failure.UnknownWord(word)
                    !seen.add(word) -> Failure.DuplicateFoundWord(word)
                    else -> null
                }
            }
        val revealedFailure =
            revealed.entries.firstNotNullOfOrNull { (word, count) ->
                val length = words[word]
                when {
                    length == null -> Failure.UnknownWord(word)
                    count !in 0 until length -> Failure.InvalidRevealCount(word, count)
                    else -> null
                }
            }
        return foundFailure ?: revealedFailure ?: secondsLeft?.takeIf { it < 0 }?.let { Failure.NegativeSeconds(it) }
    }
}

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
        val words = board.placements.associate { it.word to it.word.cellCount }
        val seen = mutableSetOf<String>()
        for (word in found) {
            if (word !in words) throw Failure.UnknownWord(word)
            if (!seen.add(word)) throw Failure.DuplicateFoundWord(word)
        }
        for ((word, count) in revealed) {
            val length = words[word] ?: throw Failure.UnknownWord(word)
            if (count !in 0 until length) throw Failure.InvalidRevealCount(word, count)
        }
        if (secondsLeft != null && secondsLeft < 0) throw Failure.NegativeSeconds(secondsLeft)
    }
}

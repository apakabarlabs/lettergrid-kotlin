package fm.apakabar.lettergrid

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class HintOrder {
    @SerialName("from_start")
    FROM_START,

    @SerialName("any_but_first")
    ANY_BUT_FIRST,
    ;

    internal fun letterIndices(word: String): List<Int> =
        when (this) {
            FROM_START -> (0 until word.cellCount).toList()
            ANY_BUT_FIRST -> {
                val seed =
                    word.codePoints().toArray().fold(0xCBF29CE484222325uL) { hash, scalar ->
                        (hash xor scalar.toULong()) * 0x100000001B3uL
                    }
                SeededRandom(seed).shuffled((1 until maxOf(word.cellCount, 1)).toList())
            }
        }
}

class Round(
    board: Board,
    private val rules: List<WordRule>,
    options: RoundOptions = RoundOptions(),
    progress: RoundProgress = RoundProgress(),
) {
    sealed class Failure(
        message: String,
    ) : Exception(message) {
        data class UnselectableWord(
            val word: String,
        ) : Failure("The enabled rules cannot select the word $word.")
    }

    enum class Phase { INTRODUCING, PLAYING, WON, LOST }

    var board: Board = board
        private set

    val id: UUID = UUID.randomUUID()
    val showsFirstWord: Boolean
    val hintOrder: HintOrder = options.hintOrder

    private val revision = MutableStateFlow(0L)

    val changes: StateFlow<Long> = revision.asStateFlow()

    var phase: Phase = Phase.PLAYING
        private set(value) {
            field = value
            revision.changed()
        }
    var secondsLeft: Int? = null
        private set(value) {
            field = value
            revision.changed()
        }
    private val foundPlacements: MutableSet<Placement>
    val found: Set<Placement> get() = foundPlacements.toSet()
    private val revealedCounts: MutableMap<Placement, Int>
    val revealed: Map<Placement, Int> get() = revealedCounts.toMap()
    var introduced: List<Cell> = emptyList()
        private set(value) {
            field = value
            revision.changed()
        }
    var selection: List<Cell> = emptyList()
        private set(value) {
            field = value
            revision.changed()
        }
    var rejected: List<Cell> = emptyList()
        private set(value) {
            field = value
            revision.changed()
        }

    init {
        val seconds = options.seconds
        val introduces = options.introduces
        require(seconds == null || seconds >= 0) { "Round seconds must be nonnegative" }
        try {
            progress.validate(board)
            validateRules(rules, board)
        } catch (failure: RoundProgress.Failure) {
            throw invalidProgress(failure)
        } catch (failure: Failure) {
            throw invalidProgress(failure)
        }
        showsFirstWord = introduces && board.placements.isNotEmpty()
        secondsLeft = seconds?.let { progress.secondsLeft ?: it }
        val byWord = mutableMapOf<String, Placement>()
        for (placement in board.placements) byWord.putIfAbsent(placement.word, placement)
        foundPlacements = progress.found.mapNotNull { byWord[it] }.toMutableSet()
        revealedCounts = mutableMapOf()
        for ((word, count) in progress.revealed) byWord[word]?.let { revealedCounts.putIfAbsent(it, count) }
        val startsWithIntroduction = introduces && progress.found.isEmpty() && board.placements.isNotEmpty()
        phase = if (startsWithIntroduction) Phase.INTRODUCING else Phase.PLAYING
        if (foundPlacements.size == board.placements.size) {
            phase = Phase.WON
        } else if (progress.isLost || secondsLeft == 0) {
            phase = Phase.LOST
        }
    }

    val isOver: Boolean get() = phase == Phase.WON || phase == Phase.LOST

    val progress: RoundProgress
        get() =
            RoundProgress(
                found = board.placements.filter { it in foundPlacements }.map { it.word },
                revealed = revealedCounts.entries.associate { it.key.word to it.value },
                secondsLeft = secondsLeft,
                isLost = phase == Phase.LOST,
            )

    val hintTarget: Placement?
        get() {
            val open = board.placements.filter { it !in foundPlacements }
            return open.firstOrNull { (revealedCounts[it] ?: 0) == 0 && hintLimit(it) > 0 }
                ?: open.firstOrNull { (revealedCounts[it] ?: 0) < hintLimit(it) }
        }

    val hintedCells: List<Cell>
        get() =
            board.placements.filter { it !in foundPlacements }.flatMap { placement ->
                revealedIndices(placement).map { placement.cells[it] }
            }

    fun shownLetters(placement: Placement): List<String?> {
        val letters = placement.word.cells
        if (placement in foundPlacements) return letters
        val opened = revealedIndices(placement).toSet()
        return letters.indices.map { if (it in opened) letters[it] else null }
    }

    fun introduceNextLetter(): Placement? {
        val first = board.placements.firstOrNull()?.takeIf { phase == Phase.INTRODUCING } ?: return null
        val unfinished = introduced.size < first.cells.size
        introduced = if (unfinished) first.cells.take(introduced.size + 1) else emptyList()
        return if (unfinished) {
            null
        } else {
            phase = Phase.PLAYING
            record(first)
        }
    }

    fun secondPassed() {
        val seconds = secondsLeft
        if (phase != Phase.PLAYING || seconds == null) return
        secondsLeft = maxOf(seconds - 1, 0)
        if (secondsLeft == 0) {
            selection = emptyList()
            phase = Phase.LOST
        }
    }

    fun touch(cell: Cell) {
        if (phase != Phase.PLAYING || !board.contains(cell)) return
        rejected = emptyList()
        selection = listOf(cell)
    }

    fun drag(to: Cell) {
        if (phase != Phase.PLAYING || selection.isEmpty()) return
        selection = board.selection(extending = selection, toward = to, rules = rules)
    }

    fun release(): Placement? {
        val released = selection
        selection = emptyList()
        if (phase != Phase.PLAYING || released.isEmpty()) return null
        val placement = acceptedPlacement(released)
        if (placement == null && released.size > 1) rejected = released
        return placement?.let(::record)
    }

    private fun acceptedPlacement(path: List<Cell>): Placement? {
        val open = board.placements.filter { it !in foundPlacements }
        open.firstOrNull { it.reads(path) }?.let { return it }
        val text = board.text(path)
        val target = open.firstOrNull { it.word == text } ?: return null
        if (rules.none { it.matches(path) }) return null
        val drawn = Placement(target.word, path)
        board = Board(board.letters, board.placements.map { if (it == target) drawn else it })
        revealedCounts.remove(target)?.let { revealedCounts[drawn] = it }
        return drawn
    }

    fun reveal(): Placement? {
        val target = hintTarget
        if (phase != Phase.PLAYING || target == null) return null
        revealedCounts[target] = (revealedCounts[target] ?: 0) + 1
        revision.changed()
        return target
    }

    fun clearRejection() {
        rejected = emptyList()
    }

    private fun revealedIndices(placement: Placement): List<Int> =
        hintOrder.letterIndices(placement.word).take(revealedCounts[placement] ?: 0)

    private fun record(placement: Placement): Placement {
        foundPlacements += placement
        revision.changed()
        if (foundPlacements.size == board.placements.size) phase = Phase.WON
        return placement
    }

    companion object {
        fun restoring(
            progress: RoundProgress,
            board: Board,
            rules: List<WordRule>,
            options: RoundOptions = RoundOptions(),
        ): Round {
            val seconds = options.seconds
            if (seconds != null && seconds < 0) throw RoundProgress.Failure.NegativeSeconds(seconds)
            progress.validate(board)
            validateRules(rules, board)
            return Round(board, rules, options, progress)
        }

        private fun invalidProgress(failure: Exception) =
            IllegalArgumentException("Invalid round progress: ${failure.message}", failure)

        private fun validateRules(
            rules: List<WordRule>,
            board: Board,
        ) {
            for (placement in board.placements) {
                val selectable =
                    rules.all { it.isValid } &&
                        rules.any {
                            it.matches(
                                placement,
                            ) || (placement.isPalindrome && it.matches(placement.reversed))
                        }
                if (!selectable) throw Failure.UnselectableWord(placement.word)
            }
        }
    }
}

private fun hintLimit(placement: Placement): Int = placement.word.cellCount - 1

private fun MutableStateFlow<Long>.changed() {
    value += 1
}

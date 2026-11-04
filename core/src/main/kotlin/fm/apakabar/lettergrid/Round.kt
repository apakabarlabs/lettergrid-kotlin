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

class Round private constructor(
    val board: Board,
    private val directions: List<Direction>,
    seconds: Int?,
    introduces: Boolean,
    val hintOrder: HintOrder,
    progress: RoundProgress,
    private val pathRules: List<WordRule>,
) {
    sealed class Failure(
        message: String,
    ) : Exception(message) {
        data class UnselectableWord(
            val word: String,
        ) : Failure("The enabled directions cannot select the word $word.")
    }

    enum class Phase { INTRODUCING, PLAYING, WON, LOST }

    val id: UUID = UUID.randomUUID()
    val showsFirstWord: Boolean

    private val revision = MutableStateFlow(0L)

    val changes: StateFlow<Long> = revision.asStateFlow()

    var phase: Phase = Phase.PLAYING
        private set(value) {
            field = value
            changed()
        }
    var secondsLeft: Int? = null
        private set(value) {
            field = value
            changed()
        }
    private val foundPlacements: MutableSet<Placement>
    val found: Set<Placement> get() = foundPlacements.toSet()
    private val revealedCounts: MutableMap<Placement, Int>
    val revealed: Map<Placement, Int> get() = revealedCounts.toMap()
    var introduced: List<Cell> = emptyList()
        private set(value) {
            field = value
            changed()
        }
    var selection: List<Cell> = emptyList()
        private set(value) {
            field = value
            changed()
        }
    var rejected: List<Cell> = emptyList()
        private set(value) {
            field = value
            changed()
        }

    init {
        require(seconds == null || seconds >= 0) { "Round seconds must be nonnegative" }
        try {
            progress.validate(board)
            if (pathRules.isEmpty()) validateDirections(directions, board) else validateRules(pathRules, board)
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

    constructor(
        board: Board,
        directions: List<Direction>,
        seconds: Int? = null,
        introduces: Boolean = false,
        hintOrder: HintOrder = HintOrder.FROM_START,
        progress: RoundProgress = RoundProgress(),
    ) : this(board, directions, seconds, introduces, hintOrder, progress, emptyList())

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
        val start = selection.firstOrNull()
        if (phase != Phase.PLAYING || start == null) return
        selection =
            if (pathRules.isEmpty()) {
                board.selection(start, to, directions)
            } else {
                board.selection(extending = selection, toward = to, rules = pathRules)
            }
    }

    fun release(): Placement? {
        val released = selection
        selection = emptyList()
        if (phase != Phase.PLAYING || released.isEmpty()) return null
        val placement = board.placements.firstOrNull { it.reads(released) }?.takeIf { it !in foundPlacements }
        if (placement == null && released.size > 1) rejected = released
        return placement?.let(::record)
    }

    fun reveal(): Placement? {
        val target = hintTarget
        if (phase != Phase.PLAYING || target == null) return null
        revealedCounts[target] = (revealedCounts[target] ?: 0) + 1
        changed()
        return target
    }

    fun clearRejection() {
        rejected = emptyList()
    }

    private fun hintLimit(placement: Placement): Int = placement.word.cellCount - 1

    private fun revealedIndices(placement: Placement): List<Int> =
        hintOrder.letterIndices(placement.word).take(revealedCounts[placement] ?: 0)

    private fun record(placement: Placement): Placement {
        foundPlacements += placement
        changed()
        if (foundPlacements.size == board.placements.size) phase = Phase.WON
        return placement
    }

    private fun changed() {
        revision.value += 1
    }

    companion object {
        operator fun invoke(
            board: Board,
            rules: List<WordRule>,
            seconds: Int? = null,
            introduces: Boolean = false,
            hintOrder: HintOrder = HintOrder.FROM_START,
            progress: RoundProgress = RoundProgress(),
        ): Round = Round(board, Direction.entries, seconds, introduces, hintOrder, progress, rules)

        fun restoring(
            progress: RoundProgress,
            board: Board,
            rules: List<WordRule>,
            seconds: Int? = null,
            introduces: Boolean = false,
            hintOrder: HintOrder = HintOrder.FROM_START,
        ): Round {
            if (seconds != null && seconds < 0) throw RoundProgress.Failure.NegativeSeconds(seconds)
            progress.validate(board)
            validateRules(rules, board)
            return Round(board, rules, seconds, introduces, hintOrder, progress)
        }

        @JvmName("restoringDirections")
        fun restoring(
            progress: RoundProgress,
            board: Board,
            directions: List<Direction>,
            seconds: Int? = null,
            introduces: Boolean = false,
            hintOrder: HintOrder = HintOrder.FROM_START,
        ): Round {
            if (seconds != null && seconds < 0) throw RoundProgress.Failure.NegativeSeconds(seconds)
            progress.validate(board)
            validateDirections(directions, board)
            return Round(board, directions, seconds, introduces, hintOrder, progress)
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

        private fun validateDirections(
            directions: List<Direction>,
            board: Board,
        ) {
            for (placement in board.placements) {
                val oneCell = placement.cells.size == 1 && directions.isNotEmpty()
                val selectable =
                    oneCell ||
                        placement.turns == 0 &&
                        (
                            placement.direction in directions ||
                                (placement.isPalindrome && placement.direction.opposite in directions)
                        )
                if (!selectable) throw Failure.UnselectableWord(placement.word)
            }
        }
    }
}

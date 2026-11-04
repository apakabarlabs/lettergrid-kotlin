package fm.apakabar.lettergrid

class LetterWeights private constructor(
    private val letters: List<String>,
    private val weights: List<Double>,
    val sameNeighbour: Double,
) {
    private class Candidate(
        val letter: String,
        var weight: Double,
        val wide: WideNumber,
    )

    private data class WideNumber(
        val significand: Double,
        val exponent: Int,
    ) {
        fun times(
            factor: Double,
            count: Int,
        ): WideNumber {
            val wideFactor = of(factor)
            var result = this
            repeat(count) {
                val product = of(result.significand * wideFactor.significand)
                result = WideNumber(product.significand, result.exponent + wideFactor.exponent + product.exponent)
            }
            return result
        }

        fun scaled(below: Int): Double = Math.scalb(significand, exponent - below)

        companion object {
            fun of(value: Double): WideNumber {
                val exponent = exponentOf(value)
                return WideNumber(Math.scalb(value, -exponent), exponent)
            }

            private fun exponentOf(value: Double): Int {
                val exponent = Math.getExponent(value)
                if (exponent >= java.lang.Double.MIN_EXPONENT) return exponent
                return Math.getExponent(Math.scalb(value, SUBNORMAL_SHIFT)) - SUBNORMAL_SHIFT
            }

            private const val SUBNORMAL_SHIFT = 64
        }
    }

    sealed class Failure(
        message: String,
    ) : Exception(message) {
        data object Empty : Failure("There are no filler letters.")

        data class NotALetter(
            val key: String,
        ) : Failure("$key is not a single letter.")

        data class NotPositive(
            val key: String,
        ) : Failure("The weight of $key is not positive.")

        data class NotFinite(
            val key: String,
        ) : Failure("The weight of $key is not finite.")

        data class SameNeighbourOutsideFraction(
            val value: Double,
        ) : Failure("The same-neighbour factor $value is not in (0, 1].")
    }

    internal val letterCount: Int get() = letters.size

    constructor(weights: Map<String, Double>, sameNeighbour: Double = 1.0) : this(prepared(weights, sameNeighbour))

    private constructor(copy: LetterWeights) : this(copy.letters, copy.weights, copy.sameNeighbour)

    internal fun letters(excluding: Set<String>): LetterWeights? {
        val kept = letters.zip(weights).filter { it.first !in excluding }
        if (kept.isEmpty()) return null
        return LetterWeights(kept.map { it.first }, kept.map { it.second }, sameNeighbour)
    }

    internal fun orderedLetters(
        random: SeededRandom,
        neighbours: Map<String, Int> = emptyMap(),
    ): List<String> {
        val remaining = candidates(neighbours)
        val ordered = mutableListOf<String>()
        while (remaining.isNotEmpty()) {
            val chosen = draw(remaining, random)
            ordered += chosen
            remaining.removeAll { it.letter == chosen }
        }
        return ordered
    }

    private fun candidates(neighbours: Map<String, Int>): MutableList<Candidate> =
        letters
            .zip(weights)
            .map { (letter, weight) ->
                val count = neighbours[letter] ?: 0
                var product = weight
                repeat(count) { product *= sameNeighbour }
                Candidate(letter, product, WideNumber.of(weight).times(sameNeighbour, count))
            }.toMutableList()

    private fun draw(
        candidates: MutableList<Candidate>,
        random: SeededRandom,
    ): String {
        var total = candidates.fold(0.0) { sum, candidate -> sum + candidate.weight }
        if (!total.isFinite() || total == 0.0) {
            val largest = candidates.maxOfOrNull { it.wide.exponent } ?: 0
            for (candidate in candidates) candidate.weight = candidate.wide.scaled(largest)
            total = candidates.fold(0.0) { sum, candidate -> sum + candidate.weight }
        }
        var point = random.nextDouble(below = total)
        for (candidate in candidates) {
            point -= candidate.weight
            if (point < 0) return candidate.letter
        }
        return candidates[candidates.size - 1].letter
    }

    override fun equals(other: Any?): Boolean =
        other is LetterWeights && other.letters == letters && other.weights == weights && other.sameNeighbour == sameNeighbour

    override fun hashCode(): Int = (letters.hashCode() * 31 + weights.hashCode()) * 31 + sameNeighbour.hashCode()

    private companion object {
        fun prepared(
            weights: Map<String, Double>,
            sameNeighbour: Double,
        ): LetterWeights {
            if (weights.isEmpty()) throw Failure.Empty
            if (!(sameNeighbour > 0 && sameNeighbour <= 1)) throw Failure.SameNeighbourOutsideFraction(sameNeighbour)
            val letters = mutableListOf<String>()
            val orderedWeights = mutableListOf<Double>()
            val sorted = weights.entries.sortedWith { first, second -> codePointOrder(first.key, second.key) }
            for ((key, weight) in sorted) {
                val letter = key.takeIf { it.cellCount == 1 }?.cellUppercased
                if (letter == null || !letter.isLetterCell) throw Failure.NotALetter(key)
                if (!weight.isFinite()) throw Failure.NotFinite(key)
                if (weight <= 0) throw Failure.NotPositive(key)
                val index = letters.indexOf(letter)
                if (index >= 0) {
                    orderedWeights[index] += weight
                    if (!orderedWeights[index].isFinite()) throw Failure.NotFinite(key)
                } else {
                    letters += letter
                    orderedWeights += weight
                }
            }
            val order = letters.indices.sortedWith { first, second -> codePointOrder(letters[first], letters[second]) }
            return LetterWeights(order.map { letters[it] }, order.map { orderedWeights[it] }, sameNeighbour)
        }

        fun codePointOrder(
            first: String,
            second: String,
        ): Int =
            when {
                first.precedesInCodePoints(second) -> -1
                second.precedesInCodePoints(first) -> 1
                else -> 0
            }
    }
}

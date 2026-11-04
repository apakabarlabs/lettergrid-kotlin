package fm.apakabar.lettergrid

class SeededRandom(
    seed: ULong,
) {
    private var state: ULong = seed

    fun next(): ULong {
        state += GOLDEN_GAMMA
        var value = state
        value = (value xor (value shr FIRST_SHIFT)) * FIRST_MIX
        value = (value xor (value shr SECOND_SHIFT)) * SECOND_MIX
        return value xor (value shr FINAL_SHIFT)
    }

    fun nextLong(below: Long): Long {
        require(below > 0) { "A bound must be positive" }
        val upper = below.toULong()
        var draw = next()
        var low = draw * upper
        if (low < upper) {
            val threshold = (0uL - upper) % upper
            while (low < threshold) {
                draw = next()
                low = draw * upper
            }
        }
        return unsignedMultiplyHigh(draw, upper).toLong()
    }

    fun nextInt(below: Int): Int = nextLong(below.toLong()).toInt()

    fun nextInt(range: IntRange): Int = range.first + nextInt(below = range.last - range.first + 1)

    fun nextDouble(below: Double): Double {
        require(below > 0 && below.isFinite()) { "An upper bound must be positive and finite" }
        while (true) {
            val unit = (next() and ((1uL shl DOUBLE_SIGNIFICAND_BITS) - 1uL)).toDouble() * (Math.ulp(1.0) / 2)
            val value = below * unit
            if (value != below) return value
        }
    }

    fun <Element> shuffled(elements: List<Element>): List<Element> {
        val result = elements.toMutableList()
        var remaining = result.size
        var index = 0
        while (remaining > 1) {
            val offset = nextInt(below = remaining)
            remaining -= 1
            val swapped = result[index]
            result[index] = result[index + offset]
            result[index + offset] = swapped
            index += 1
        }
        return result
    }

    fun <Element> pick(elements: List<Element>): Element? =
        if (elements.isEmpty()) null else elements[nextInt(below = elements.size)]

    private companion object {
        const val GOLDEN_GAMMA = 0x9E3779B97F4A7C15uL
        const val FIRST_MIX = 0xBF58476D1CE4E5B9uL
        const val SECOND_MIX = 0x94D049BB133111EBuL
        const val FIRST_SHIFT = 30
        const val SECOND_SHIFT = 27
        const val FINAL_SHIFT = 31
        const val DOUBLE_SIGNIFICAND_BITS = 53
        const val SIGN_SHIFT = Long.SIZE_BITS - 1

        fun unsignedMultiplyHigh(
            first: ULong,
            second: ULong,
        ): ULong {
            val a = first.toLong()
            val b = second.toLong()
            val signed = Math.multiplyHigh(a, b)
            return (signed + ((a shr SIGN_SHIFT) and b) + ((b shr SIGN_SHIFT) and a)).toULong()
        }
    }
}

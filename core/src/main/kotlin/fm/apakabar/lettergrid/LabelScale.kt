package fm.apakabar.lettergrid

data class LabelScale(
    val longest: Int,
) {
    val fontToCell: Double get() = LARGEST_FONT_TO_CELL * minOf(1.0, 2.0 / maxOf(longest, 1))

    private companion object {
        const val LARGEST_FONT_TO_CELL = 0.5
    }
}

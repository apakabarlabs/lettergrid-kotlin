package fm.apakabar.lettergrid

data class LabelScale(
    val longest: Int,
) {
    val fontToCell: Double get() = 0.5 * minOf(1.0, 2.0 / maxOf(longest, 1))
}

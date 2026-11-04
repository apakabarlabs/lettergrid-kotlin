package fm.apakabar.lettergrid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class Cell(
    val row: Int,
    val column: Int,
) {
    internal fun moved(
        direction: Direction,
        distance: Int,
    ): Cell = Cell(row + direction.rowStep * distance, column + direction.columnStep * distance)
}

@Serializable
enum class Direction {
    @SerialName("right")
    RIGHT,

    @SerialName("down")
    DOWN,

    @SerialName("down_right")
    DOWN_RIGHT,

    @SerialName("down_left")
    DOWN_LEFT,

    @SerialName("left")
    LEFT,

    @SerialName("up")
    UP,

    @SerialName("up_left")
    UP_LEFT,

    @SerialName("up_right")
    UP_RIGHT,
    ;

    val rowStep: Int
        get() =
            when (this) {
                RIGHT, LEFT -> 0
                DOWN, DOWN_RIGHT, DOWN_LEFT -> 1
                UP, UP_LEFT, UP_RIGHT -> -1
            }

    val columnStep: Int
        get() =
            when (this) {
                RIGHT, DOWN_RIGHT, UP_RIGHT -> 1
                DOWN, UP -> 0
                LEFT, DOWN_LEFT, UP_LEFT -> -1
            }

    val opposite: Direction
        get() =
            when (this) {
                RIGHT -> LEFT
                DOWN -> UP
                DOWN_RIGHT -> UP_LEFT
                DOWN_LEFT -> UP_RIGHT
                LEFT -> RIGHT
                UP -> DOWN
                UP_LEFT -> DOWN_RIGHT
                UP_RIGHT -> DOWN_LEFT
            }

    val isVertical: Boolean get() = columnStep == 0
    val isDiagonal: Boolean get() = rowStep != 0 && columnStep != 0

    internal companion object {
        val lineAxes: List<Direction> =
            entries.filter { it.rowStep > 0 || (it.rowStep == 0 && it.columnStep > 0) }
    }
}

package fm.apakabar.lettergrid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import fm.apakabar.lettergrid.Cell

@Composable
fun LetterHighlight(
    start: Cell,
    end: Cell,
    cellSize: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    LetterHighlight(listOf(start, end), cellSize, color, modifier)
}

@Composable
fun LetterHighlight(
    cells: List<Cell>,
    cellSize: Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val first = cells.firstOrNull() ?: return
    Canvas(modifier.fillMaxSize()) {
        val side = cellSize.toPx()
        val thickness = side * THICKNESS

        fun center(cell: Cell) = Offset((cell.column + HALF_CELL) * side, (cell.row + HALF_CELL) * side)
        if (cells.all { it == first }) {
            drawCircle(color, radius = thickness / 2, center = center(first))
        } else {
            val path =
                Path().apply {
                    val start = center(first)
                    moveTo(start.x, start.y)
                    for (cell in cells.drop(1)) {
                        val point = center(cell)
                        lineTo(point.x, point.y)
                    }
                }
            drawPath(path, color, style = Stroke(width = thickness, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private const val THICKNESS = 0.84f
private const val HALF_CELL = 0.5f

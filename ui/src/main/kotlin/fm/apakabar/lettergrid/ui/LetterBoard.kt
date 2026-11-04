package fm.apakabar.lettergrid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import fm.apakabar.lettergrid.Board
import fm.apakabar.lettergrid.Cell
import fm.apakabar.lettergrid.LabelScale
import fm.apakabar.lettergrid.Placement
import fm.apakabar.lettergrid.Round
import fm.apakabar.lettergrid.cells
import kotlinx.coroutines.delay

@Composable
fun LetterBoard(
    round: Round,
    style: LetterGridStyle,
    onTouch: (Cell) -> Unit,
    onDrag: (Cell) -> Unit,
    onRelease: () -> Unit,
    onRejectionShown: () -> Unit,
    modifier: Modifier = Modifier,
    missed: Placement? = null,
    traceCells: List<Cell> = emptyList(),
    label: (String) -> String = { it },
) {
    round.changes.collectAsState().value
    val board = round.board
    val currentOnRejectionShown by rememberUpdatedState(onRejectionShown)
    Feedback(round)
    LaunchedEffect(round.rejected) {
        if (round.rejected.isEmpty()) return@LaunchedEffect
        delay(style.rejectionDelay)
        currentOnRejectionShown()
    }
    BoxWithConstraints(modifier.aspectRatio(maxOf(board.columns, 1).toFloat() / maxOf(board.rows, 1))) {
        val side = maxWidth / maxOf(board.columns, 1)
        val sidePx = with(LocalDensity.current) { side.toPx() }
        Box(Modifier.pointerInput(board, sidePx) { dragCells(board, sidePx, onTouch, onDrag, onRelease) }) {
            for ((cells, color) in bars(round, style)) LetterHighlight(cells, side, color)
            Rejection(round.rejected, side, style.rejected)
            if (missed != null) LetterHighlight(missed.cells, side, style.rejected)
            LetterHighlight(traceCells, side, style.selected)
            Letters(board, side, label)
        }
    }
}

@Composable
private fun Feedback(round: Round) {
    val haptics = LocalHapticFeedback.current
    val selected = round.selection.size
    var lastSelected by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected > 0 && selected != lastSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        lastSelected = selected
    }
    val found = round.found.size
    var lastFound by remember { mutableStateOf(found) }
    LaunchedEffect(found) {
        if (found > lastFound) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        lastFound = found
    }
    LaunchedEffect(round.rejected) {
        if (round.rejected.isNotEmpty()) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }
}

@Composable
private fun Rejection(
    rejected: List<Cell>,
    side: Dp,
    color: Color,
) {
    var shown by remember { mutableStateOf(rejected) }
    if (rejected.isNotEmpty()) shown = rejected
    AnimatedVisibility(
        visible = rejected.isNotEmpty(),
        enter = fadeIn(tween(REJECTION_FADE_MILLIS, easing = EaseOut)),
        exit = fadeOut(tween(REJECTION_FADE_MILLIS, easing = EaseOut)),
    ) {
        LetterHighlight(shown, side, color)
    }
}

private fun bars(
    round: Round,
    style: LetterGridStyle,
): List<Pair<List<Cell>, Color>> {
    val found =
        round.board.placements
            .filter { it in round.found }
            .map { it.cells } + listOf(round.introduced)
    return found.filter { it.isNotEmpty() }.map { it to style.found } +
        round.hintedCells.map { listOf(it) to style.hinted } +
        listOf(round.selection to style.selected)
}

@Composable
private fun Letters(
    board: Board,
    side: Dp,
    label: (String) -> String,
) {
    val longest = board.letters.flatten().maxOfOrNull { label(it).cells.size } ?: 1
    val scale = LabelScale(longest).fontToCell
    val fontSize = with(LocalDensity.current) { (side * scale.toFloat()).toSp() }
    Column {
        for (row in 0 until board.rows) {
            Row {
                for (column in 0 until board.columns) {
                    Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                        Text(label(board[Cell(row, column)]), fontSize = fontSize, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.dragCells(
    board: Board,
    side: Float,
    onTouch: (Cell) -> Unit,
    onDrag: (Cell) -> Unit,
    onRelease: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        cell(down.position, side, board)?.let(onTouch)
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            cell(change.position, side, board)?.let(onDrag)
        }
        onRelease()
    }
}

private fun cell(
    point: Offset,
    side: Float,
    board: Board,
): Cell? {
    if (!side.isFinite() || side <= 0 || !point.x.isFinite() || !point.y.isFinite() || board.rows <= 0 || board.columns <= 0) {
        return null
    }
    return Cell(
        row = (point.y / side).coerceIn(0f, (board.rows - 1).toFloat()).toInt(),
        column = (point.x / side).coerceIn(0f, (board.columns - 1).toFloat()).toInt(),
    )
}

private const val REJECTION_FADE_MILLIS = 600

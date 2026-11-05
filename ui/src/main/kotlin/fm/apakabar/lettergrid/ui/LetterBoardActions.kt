package fm.apakabar.lettergrid.ui

import fm.apakabar.lettergrid.Cell

data class LetterBoardActions(
    val onTouch: (Cell) -> Unit,
    val onDrag: (Cell) -> Unit,
    val onRelease: () -> Unit,
    val onRejectionShown: () -> Unit,
)

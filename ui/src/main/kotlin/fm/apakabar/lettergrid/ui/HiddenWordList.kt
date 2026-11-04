package fm.apakabar.lettergrid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fm.apakabar.lettergrid.Placement
import fm.apakabar.lettergrid.Round

@Composable
fun HiddenWordList(
    round: Round,
    style: LetterGridStyle,
    hiddenWordLabel: @Composable (Int) -> String,
    modifier: Modifier = Modifier,
) {
    round.changes.collectAsState().value
    ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)) {
        Column(
            modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SPACING),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(SPACING)) {
                round.board.placements.forEachIndexed { index, placement ->
                    Row(horizontalArrangement = Arrangement.spacedBy(SPACING), verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Word(round, placement, style, hiddenWordLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun Word(
    round: Round,
    placement: Placement,
    style: LetterGridStyle,
    hiddenWordLabel: @Composable (Int) -> String,
) {
    val shown = round.shownLetters(placement)
    if (placement in round.found) {
        CompositionLocalProvider(LocalContentColor provides style.foundText) {
            Letters(shown, placement.word)
        }
    } else {
        Letters(shown, hiddenWordLabel(shown.size))
    }
}

@Composable
private fun Letters(
    letters: List<String?>,
    description: String,
) {
    val letterWidth = with(LocalDensity.current) { LETTER_WIDTH.toDp() }
    Row(Modifier.clearAndSetSemantics { contentDescription = description }) {
        for (letter in letters) {
            Text(letter ?: "•", Modifier.width(letterWidth), textAlign = TextAlign.Center)
        }
    }
}

private val SPACING = 10.dp
private val LETTER_WIDTH = 22.sp

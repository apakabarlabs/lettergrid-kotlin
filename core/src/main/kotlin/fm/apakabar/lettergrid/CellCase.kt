package fm.apakabar.lettergrid

import java.text.BreakIterator
import java.text.Normalizer

val String.cellUppercased: String
    get() =
        Normalizer.normalize(this, Normalizer.Form.NFC).cells.joinToString("") { letter ->
            val upper = Normalizer.normalize(letter.uppercase(), Normalizer.Form.NFC)
            if (upper.isOneCell) upper else letter
        }

val String.cells: List<String>
    get() {
        val boundaries = characterBoundaries.get()
        boundaries.setText(this)
        val cells = mutableListOf<String>()
        var start = boundaries.first()
        var end = boundaries.next()
        while (end != BreakIterator.DONE) {
            cells += substring(start, end)
            start = end
            end = boundaries.next()
        }
        return cells
    }

private val String.isOneCell: Boolean
    get() {
        val boundaries = characterBoundaries.get()
        boundaries.setText(this)
        boundaries.first()
        return boundaries.next() != BreakIterator.DONE && boundaries.next() == BreakIterator.DONE
    }

private val characterBoundaries: ThreadLocal<BreakIterator> =
    ThreadLocal.withInitial { BreakIterator.getCharacterInstance() }

internal val String.cellCount: Int get() = cells.size

internal val String.isLetterCell: Boolean get() = isNotEmpty() && Character.isAlphabetic(codePointAt(0))

internal fun String.reversedCells(): String = cells.asReversed().joinToString("")

internal fun String.precedesInCodePoints(other: String): Boolean {
    val mine = codePoints().toArray()
    val theirs = other.codePoints().toArray()
    for (index in 0 until minOf(mine.size, theirs.size)) {
        if (mine[index] != theirs[index]) return mine[index] < theirs[index]
    }
    return mine.size < theirs.size
}

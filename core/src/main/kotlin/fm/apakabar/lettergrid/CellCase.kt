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
        if (hasOneCellPerChar) return map(Char::toString)
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
        if (hasOneCellPerChar) return length == 1
        val boundaries = characterBoundaries.get()
        boundaries.setText(this)
        boundaries.first()
        return boundaries.next() != BreakIterator.DONE && boundaries.next() == BreakIterator.DONE
    }

private val String.hasOneCellPerChar: Boolean
    get() = all(Char::standsAlone)

private val Char.standsAlone: Boolean
    get() = this in ' '..'~' || (Character.getType(this) !in joiningTypes && code.script in separateScripts)

private val Int.script: Character.UnicodeScript get() = Character.UnicodeScript.of(this)

private val joiningTypes =
    setOf(
        Character.NON_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt(),
        Character.COMBINING_SPACING_MARK.toInt(),
        Character.FORMAT.toInt(),
        Character.CONTROL.toInt(),
    )

private val separateScripts =
    setOf(
        Character.UnicodeScript.LATIN,
        Character.UnicodeScript.CYRILLIC,
        Character.UnicodeScript.GREEK,
        Character.UnicodeScript.ARMENIAN,
        Character.UnicodeScript.GEORGIAN,
    )

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

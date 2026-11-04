package fm.apakabar.lettergrid

internal fun String.Companion.privateUse(avoiding: Set<String>): String? =
    (0xE000..0xF8FF).asSequence().map { String(Character.toChars(it)) }.firstOrNull { it !in avoiding }

internal fun Iterable<Placement>.letters(
    columns: Int,
    rows: Int,
    blank: String,
): List<List<String>> = letters(columns, rows, blank) { it }

internal fun Iterable<Placement>.letters(
    columns: Int,
    rows: Int,
): List<List<String?>> = letters<String?>(columns, rows, null) { it }

private fun <Letter> Iterable<Placement>.letters(
    columns: Int,
    rows: Int,
    empty: Letter,
    letter: (String) -> Letter,
): List<List<Letter>> {
    val letters = List(rows) { MutableList(columns) { empty } }
    for (placement in this) {
        for ((cell, character) in placement.cells.zip(placement.word.cells)) {
            letters[cell.row][cell.column] = letter(character)
        }
    }
    return letters
}

internal val List<WordRule>.startDirections: List<Direction>
    get() = Direction.entries.filter { way -> any { way in it.startDirections } }

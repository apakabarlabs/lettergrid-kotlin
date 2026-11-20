package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import java.text.BreakIterator
import java.text.Normalizer
import kotlin.test.assertEquals

class GraphemeCellsTests {
    @Test
    fun everyCharacterOfTheAlphabetsCutsAsGraphemeClustersDo() {
        val characters = alphabetCharacters()
        val texts = characters.flatMap { listOf("$it", "a$it", "${it}a", "й$it") } + words
        for (text in texts) {
            assertEquals(graphemes(text), text.cells, text)
            assertEquals(uppercasedByGraphemes(text), text.cellUppercased, text)
        }
    }

    @Test
    fun textBeyondTheAlphabetsStillCutsAsGraphemeClustersDo() {
        for (text in beyondAlphabets) {
            assertEquals(graphemes(text), text.cells, text)
            assertEquals(uppercasedByGraphemes(text), text.cellUppercased, text)
        }
    }

    private fun alphabetCharacters(): List<Char> =
        (0..0xFFFF)
            .map { it.toChar() }
            .filter { !it.isSurrogate() && Character.UnicodeScript.of(it.code) in alphabets }

    private fun graphemes(text: String): List<String> {
        val boundaries = BreakIterator.getCharacterInstance()
        boundaries.setText(text)
        val cells = mutableListOf<String>()
        var start = boundaries.first()
        var end = boundaries.next()
        while (end != BreakIterator.DONE) {
            cells += text.substring(start, end)
            start = end
            end = boundaries.next()
        }
        return cells
    }

    private fun uppercasedByGraphemes(text: String): String =
        graphemes(Normalizer.normalize(text, Normalizer.Form.NFC)).joinToString("") { letter ->
            val upper = Normalizer.normalize(letter.uppercase(), Normalizer.Form.NFC)
            if (graphemes(upper).size == 1) upper else letter
        }

    private companion object {
        val alphabets =
            setOf(
                Character.UnicodeScript.LATIN,
                Character.UnicodeScript.CYRILLIC,
                Character.UnicodeScript.GREEK,
                Character.UnicodeScript.ARMENIAN,
                Character.UnicodeScript.GEORGIAN,
                Character.UnicodeScript.COMMON,
            )

        val words =
            listOf(
                "straße",
                "երևան",
                "ու",
                "ǰ",
                "ŉ",
                "ﬁ",
                "rock-'n'-roll",
                "dziękuję",
                "љубав",
                "ძაღლი",
                "ἀγάπη",
                "ő ű",
            )

        val beyondAlphabets =
            listOf(
                "été",
                "ǟ",
                "กัน",
                "नमस्ते",
                "한국어",
                "각",
                "👍🏽",
                "🇷🇸",
                "1️⃣",
                "a‍b",
                "\r\n",
                "بَاب",
            )
    }
}

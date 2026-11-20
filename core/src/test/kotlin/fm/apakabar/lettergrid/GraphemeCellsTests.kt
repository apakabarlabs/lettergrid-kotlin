package fm.apakabar.lettergrid

import org.junit.jupiter.api.Test
import java.text.Normalizer
import kotlin.test.assertEquals
import com.ibm.icu.text.BreakIterator as UnicodeBreaks
import java.text.BreakIterator as PlatformBreaks

class GraphemeCellsTests {
    @Test
    fun textOfTheSeparateAlphabetsCutsAsUnicodeGraphemeClustersDo() {
        val characters = charactersOf(separateScripts) + (' '..'~')
        val texts = characters.flatMap { listOf("$it", "a$it", "${it}a", "й$it") } + words
        for (text in texts) {
            assertEquals(unicodeGraphemes(text), text.cells, text)
            assertEquals(uppercased(text, ::unicodeGraphemes), text.cellUppercased, text)
        }
    }

    @Test
    fun otherTextStillCutsAsThePlatformCutsIt() {
        val characters = charactersOf(setOf(Character.UnicodeScript.COMMON, Character.UnicodeScript.INHERITED))
        val texts = characters.flatMap { listOf("$it", "a$it", "${it}a") } + beyondAlphabets
        for (text in texts) {
            assertEquals(platformGraphemes(text), text.cells, text)
            assertEquals(uppercased(text, ::platformGraphemes), text.cellUppercased, text)
        }
    }

    private fun charactersOf(scripts: Set<Character.UnicodeScript>): List<Char> =
        (0..0xFFFF)
            .map { it.toChar() }
            .filter { !it.isSurrogate() && Character.UnicodeScript.of(it.code) in scripts }

    private fun unicodeGraphemes(text: String): List<String> {
        val boundaries = UnicodeBreaks.getCharacterInstance()
        boundaries.setText(text)
        return cut(text, boundaries.first()) { boundaries.next() }
    }

    private fun platformGraphemes(text: String): List<String> {
        val boundaries = PlatformBreaks.getCharacterInstance()
        boundaries.setText(text)
        return cut(text, boundaries.first()) { boundaries.next() }
    }

    private fun cut(
        text: String,
        first: Int,
        next: () -> Int,
    ): List<String> {
        val cells = mutableListOf<String>()
        var start = first
        var end = next()
        while (end != PlatformBreaks.DONE) {
            cells += text.substring(start, end)
            start = end
            end = next()
        }
        return cells
    }

    private fun uppercased(
        text: String,
        graphemes: (String) -> List<String>,
    ): String =
        graphemes(Normalizer.normalize(text, Normalizer.Form.NFC)).joinToString("") { letter ->
            val upper = Normalizer.normalize(letter.uppercase(), Normalizer.Form.NFC)
            if (graphemes(upper).size == 1) upper else letter
        }

    private companion object {
        val separateScripts =
            setOf(
                Character.UnicodeScript.LATIN,
                Character.UnicodeScript.CYRILLIC,
                Character.UnicodeScript.GREEK,
                Character.UnicodeScript.ARMENIAN,
                Character.UnicodeScript.GEORGIAN,
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
                "rock’n’roll",
                "пʼять",
            )
    }
}

package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import java.text.Normalizer
import kotlin.test.assertEquals

class CellTextTests {
    @Serializable
    private data class Case(
        val name: String,
        val text: String,
        val cells: List<String>,
        val letters: List<Boolean>,
        val hintOrder: List<Int>,
    )

    @Test
    fun textBecomesTheCellsEveryPortAgreesOn() {
        for (item in Fixtures.decode("cell-text", ListSerializer(Case.serializer()))) {
            val word = item.text.cellUppercased
            val cells = word.cells
            assertEquals(item.cells, cells, item.name)
            assertEquals(cells.map { Normalizer.normalize(it, Normalizer.Form.NFC) }, cells, item.name)
            assertEquals(item.letters, cells.map { it.isLetterCell }, item.name)
            assertEquals(item.hintOrder, HintOrder.ANY_BUT_FIRST.letterIndices(word), item.name)
        }
    }
}

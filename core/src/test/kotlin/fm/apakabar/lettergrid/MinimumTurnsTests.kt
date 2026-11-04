package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MinimumTurnsTests {
    @Serializable
    private data class Field(
        val name: String,
        val directions: List<Direction>,
        val columns: Int,
        val maximumRows: Int? = null,
        val turns: Map<Int, Int>,
        val unfit: List<Int>,
    )

    @Test
    fun eachLengthNeedsTheFewestTurnsThatFitTheField() {
        for (field in Fixtures.decode("minimum-turns", ListSerializer(Field.serializer()))) {
            for ((length, turns) in field.turns) {
                assertEquals(
                    turns,
                    WordRule.minimumTurns(length, field.directions, field.columns, field.maximumRows),
                    "${field.name}, length $length",
                )
            }
            for (length in field.unfit) {
                assertNull(
                    WordRule.minimumTurns(length, field.directions, field.columns, field.maximumRows),
                    "${field.name}, length $length",
                )
            }
        }
    }
}

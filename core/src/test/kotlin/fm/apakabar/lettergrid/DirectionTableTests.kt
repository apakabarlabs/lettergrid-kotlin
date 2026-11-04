package fm.apakabar.lettergrid

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DirectionTableTests {
    @Serializable
    private data class Row(
        val name: Direction,
        val rowStep: Int,
        val columnStep: Int,
        val opposite: Direction,
    )

    @Test
    fun everyDirectionStepsAsTheTableSays() {
        val rows = Fixtures.decode("directions", ListSerializer(Row.serializer()))
        assertEquals(Direction.entries, rows.map { it.name })
        for (row in rows) {
            assertEquals(row.rowStep, row.name.rowStep, "${row.name}")
            assertEquals(row.columnStep, row.name.columnStep, "${row.name}")
            assertEquals(row.opposite, row.name.opposite, "${row.name}")
        }
    }
}

package fm.apakabar.lettergrid

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals

class LabelScaleTests {
    @ParameterizedTest
    @ValueSource(ints = [1, 2])
    fun oneOrTwoLettersKeepTheUsualSize(longest: Int) {
        assertEquals(0.5, LabelScale(longest).fontToCell)
    }

    @Test
    fun aThreeLetterLabelShrinksEveryCellAlike() {
        assertEquals(0.5 * 2 / 3, LabelScale(3).fontToCell)
    }
}

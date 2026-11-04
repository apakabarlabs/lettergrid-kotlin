package fm.apakabar.lettergrid

import org.junit.jupiter.params.provider.Arguments

object TestArguments {
    const val DIRECTIONS_AND_TURNS = "fm.apakabar.lettergrid.TestArguments#directionsAndTurns"

    @JvmStatic
    fun directionsAndTurns(): List<Arguments> =
        Direction.entries.flatMap { direction ->
            (0..3).map { Arguments.of(direction, it) }
        }
}

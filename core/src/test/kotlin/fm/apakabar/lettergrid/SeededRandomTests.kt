package fm.apakabar.lettergrid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SeededRandomTests {
    @Serializable
    private data class Case(
        val name: String,
        val seed: String,
        val next: List<String>? = null,
        val below: Long? = null,
        val integers: List<Long>? = null,
        @SerialName("double_below") val doubleBelow: Double? = null,
        val doubles: List<Double>? = null,
        val shuffle: Int? = null,
        val order: List<Int>? = null,
        val from: Int? = null,
        val through: Int? = null,
        @SerialName("pick_from") val pickFrom: Int? = null,
        val picks: List<Int>? = null,
    )

    @Test
    fun everyDrawFollowsTheRecordedSequence() {
        for (item in Fixtures.decode("seeded-random", ListSerializer(Case.serializer()))) {
            val random = SeededRandom(item.seed.toULong())
            item.next?.let { next ->
                assertEquals(next.map { it.toULong() }, next.map { random.next() }, item.name)
            }
            if (item.below != null && item.integers != null) {
                assertEquals(item.integers, item.integers.map { random.nextLong(item.below) }, item.name)
            }
            if (item.doubleBelow != null && item.doubles != null) {
                assertEquals(item.doubles, item.doubles.map { random.nextDouble(item.doubleBelow) }, item.name)
            }
            if (item.shuffle != null && item.order != null) {
                assertEquals(item.order, random.shuffled((0 until item.shuffle).toList()), item.name)
            }
            if (item.from != null && item.through != null && item.integers != null) {
                assertEquals(
                    item.integers,
                    item.integers.map { random.nextInt(item.from..item.through).toLong() },
                    item.name,
                )
            }
            if (item.pickFrom != null && item.picks != null) {
                assertEquals(item.picks, item.picks.map { random.pick((0 until item.pickFrom).toList()) }, item.name)
            }
        }
    }
}

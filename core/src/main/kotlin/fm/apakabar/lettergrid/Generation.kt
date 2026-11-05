package fm.apakabar.lettergrid

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable

@Serializable
internal data class Generation(
    val supportedTurns: List<Int>,
    val packing: Packing,
    val shapes: Shapes,
    val noise: Noise,
    val fill: Fill,
    val reveal: Reveal,
    val selection: Selection,
) {
    @Serializable
    data class Packing(
        val nodeLimit: Int,
        val growingHeightNodeLimit: Int,
        val crossingRestarts: Int,
    )

    @Serializable
    data class Shapes(
        val shapeLimit: Int,
        val visitLimit: Int,
    )

    @Serializable
    data class Noise(
        val visitLimit: Int,
    )

    @Serializable
    data class Fill(
        val runLength: Int,
        val seeds: Int,
        val stepsPerChoice: Int,
        val completedCandidates: Int,
    )

    @Serializable
    data class Reveal(
        val frameSeconds: Double,
        val travelShare: Double,
    )

    @Serializable
    data class Selection(
        val diagonalSnapCosineSquared: Double,
    )

    companion object {
        val settings: Generation by lazy {
            val text =
                checkNotNull(Generation::class.java.getResourceAsStream("/fm/apakabar/lettergrid/generation.yaml")) {
                    "generation.yaml is missing from the LetterGrid resources"
                }.use { it.readBytes().decodeToString() }
            val settings = Yaml.default.decodeFromString(serializer(), text)
            check(settings.supportedTurns.size == 2) { "supportedTurns names a first and last" }
            check(settings.fill.runLength >= 2) { "fill.runLength names a run of two or more" }
            settings
        }
    }
}

package fm.apakabar.lettergrid

internal object NoiseSearch {
    val maximumVisits: Int get() = Generation.settings.noise.visitLimit
}

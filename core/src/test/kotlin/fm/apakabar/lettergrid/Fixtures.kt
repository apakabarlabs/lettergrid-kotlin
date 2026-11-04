package fm.apakabar.lettergrid

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.DeserializationStrategy
import java.io.File

object Fixtures {
    private const val DIRECTORY = "/fm/apakabar/lettergrid/fixtures"

    fun bytes(name: String): ByteArray =
        checkNotNull(Fixtures::class.java.getResourceAsStream("$DIRECTORY/$name")) {
            "$name is missing: run `make sync-yaml` in lettergrid-swift"
        }.use { it.readBytes() }

    fun <T> decode(
        name: String,
        deserializer: DeserializationStrategy<T>,
    ): T = Yaml.default.decodeFromString(deserializer, bytes("$name.yaml").decodeToString())

    fun copied(): Set<String> {
        val directory =
            File(
                checkNotNull(Fixtures::class.java.getResource(DIRECTORY)) {
                    "no fixtures: run `make sync-yaml` in lettergrid-swift"
                }.toURI(),
            )
        return directory
            .listFiles { file -> file.isFile && file.name.endsWith(".yaml") }
            .orEmpty()
            .map { it.name }
            .toSet()
    }
}

package fm.apakabar.lettergrid

import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class FixtureCopyTests {
    private fun fetch(path: String): ByteArray {
        val address = "$LEAD/$path"
        val request =
            HttpRequest
                .newBuilder(URI.create(address))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build()
        val answer = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(
            OK,
            answer.statusCode(),
            "$address answered ${answer.statusCode()}: push lettergrid-swift if the file is new there, " +
                "or run `make sync-yaml` in lettergrid-swift if it is gone",
        )
        return answer.body()
    }

    @Test
    fun everyLocalCopyIsASharedFixtureNamedInTheList() {
        assertEquals(
            SHARED.toSet(),
            Fixtures.copied(),
            "the copied fixtures differ from the list of shared ones: run `make sync-yaml` in lettergrid-swift and update the list",
        )
    }

    @TestFactory
    fun everySharedFileIsTheLeadingPortsOwnOnMainByteForByte(): List<DynamicTest> =
        SHARED.map { name ->
            DynamicTest.dynamicTest(name) {
                assertContentEquals(fetch("$FIXTURES/$name"), Fixtures.bytes(name), differs(name))
            }
        } +
            DynamicTest.dynamicTest(GENERATION) {
                val local =
                    checkNotNull(javaClass.getResourceAsStream("/fm/apakabar/lettergrid/$GENERATION")).use { it.readBytes() }
                assertContentEquals(fetch("Sources/LetterGrid/$GENERATION"), local, differs(GENERATION))
            }

    private fun differs(name: String) =
        "$name differs from lettergrid-swift main: run `make sync-yaml` there, or push lettergrid-swift " +
            "if the change is there only locally"

    companion object {
        private val SHARED =
            listOf(
                "cell-text.yaml",
                "directions.yaml",
                "filler-alphabet.yaml",
                "letter-runs.yaml",
                "letter-weights.yaml",
                "minimum-turns.yaml",
                "packed-requests.yaml",
                "path-packing.yaml",
                "placement-checks.yaml",
                "repeated-letters.yaml",
                "run-free-fill.yaml",
                "seeded-boards.yaml",
                "seeded-random.yaml",
                "selections.yaml",
                "separator-boards.yaml",
                "target-copies.yaml",
            )
        private const val GENERATION = "generation.yaml"
        private const val FIXTURES = "Tests/LetterGridTests/Fixtures"
        private const val LEAD = "https://raw.githubusercontent.com/apakabarlabs/lettergrid-swift/main"
        private const val OK = 200
        private const val TIMEOUT_SECONDS = 10L
        private val CLIENT: HttpClient = HttpClient.newHttpClient()
    }
}

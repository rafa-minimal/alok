package pl.minimal.alok

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.asStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class AlokTest {

    private val results: MutableMap<Path, StringBuilder> = mutableMapOf()

    private val jira = object : JiraApi {
        override var cookie: String = ""
        override fun putWorklog(issue: String, date: LocalDate, timeSeconds: Int): String =
            "Uploaded " + Worklog(date.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime(), timeSeconds)
    }

    @AfterAll
    fun dumpActualResults() {
        // Returns 1 if content is modified
        val process = ProcessBuilder("git diff-index --quiet HEAD -- cases/".split(" ").toList())
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        process
            .waitFor(5, TimeUnit.SECONDS)
        val modified = process.exitValue() == 1
        val base = if (modified) {
            println("Dumping actual results to ./actual/ (./cases/ files seem to be modified)")
            Path.of("actual")
        } else {
            Path.of("cases")
        }
        Files.createDirectories(base)
        results.forEach { (file, result) ->
            Files.writeString(base.resolve(file.fileName), result.toString())
        }
    }

    @ParameterizedTest
    @MethodSource("cases")
    fun test(file: Path, input: String, expected: String) {
        val mockToday = LocalDate.of(2020, 9, 27)

        val actual = process(input.lines(), jira, mockToday)
            .joinToString("\n")
        results.getOrPut(file){ StringBuilder() }.let {
            it.append("-- TEST\n")
            it.append(input)
            it.append("-- EXPECTED\n")
            it.append(actual)
        }
        assertEquals(expected, actual)
    }

    private fun cases(): Stream<Arguments> =
        Files.list(Path.of("cases")).asSequence()
            .filter { Files.isReadable(it) }
            .mapNotNull { file ->
                Files.readString(file)
                    .split("-- TEST\n")
                    .filter { it.isNotBlank() }
                    .mapIndexed { index, testCase ->
                        val inputAndExpected = testCase.split("-- EXPECTED\n")
                        if (inputAndExpected.size != 2) {
                            System.err.println("Skipping malformed test case: \n$testCase")
                            null
                        } else {
                            val (input, expected) = inputAndExpected
                            Arguments.of(file, input, expected)
                        }
                    }.filterNotNull()
            }.flatten()
            .asStream()
}
package pl.minimal.alok

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.streams.asSequence
import kotlin.streams.asStream

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlokTest {

    @ParameterizedTest
    @MethodSource("cases")
    fun test(input: String, expected: String) {
        val actual = process(input.lines())
            .joinToString("\n").trim()
        assertEquals(expected, actual)
    }

    private fun cases(): Stream<Arguments> =
        Files.list(Path.of("cases")).asSequence()
            .filter { Files.isReadable(it) }
            .mapNotNull { file ->
                Files.readString(file)
                    .split("-- TEST")
                    .filter { it.isNotBlank() }
                    .mapIndexed { index, testCase ->
                        val inputAndExpected = testCase.split("-- EXPECTED")
                        if (inputAndExpected.size != 2) {
                            System.err.println("Skipping malformed test case: \n$testCase")
                            null
                        } else {
                            val (input, expected) = inputAndExpected
                            Arguments.of(input.trim(), expected.trim())
                        }
                    }.filterNotNull()
            }.flatten()
            .asStream()
}
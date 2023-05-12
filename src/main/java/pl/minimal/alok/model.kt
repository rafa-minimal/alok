package pl.minimal.alok

import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class Level(val char: Char, val priority: Int) {
    Error('❌', 1),
    Warn('❗', 2),
    Info('✔', 3),
    Trace('ⓘ', 4);
    companion object {
        val chars = values().map { it.char }.joinToString("", "", "")
    }
}

const val UPLOADED = "★"

class Context(
    val today: LocalDate,
) {
    var flags: MutableSet<Flag> = mutableSetOf()
    var date: LocalDate? = null
    val logs: MutableList<Pair<Level, String>> = mutableListOf()
    val aliases: MutableMap<String, Entry> = mutableMapOf()
    val entries: MutableList<Entry> = mutableListOf()
    var dateLine: Line? = null
}

enum class Flag {
    dry, quiet, verbose;

    companion object {
        val valuesString = values().map { "'$it'" }.joinToString(", ", "", "")
    }
}

data class Line(
    var content: String,
    val logs: MutableList<Pair<Level, String>> = mutableListOf()
) {
    private val verboseLevels = Level.values().toSet()
    private val defaultLevels = setOf(Level.Error, Level.Warn, Level.Info)

    fun toString(flags: Set<Flag>, margin: Int): String =
        when {
            flags.contains(Flag.quiet) ->
                content
            flags.contains(Flag.verbose) ->
                joinWithMargin(content, logs(verboseLevels), margin)
            else ->
                joinWithMargin(content, logs(defaultLevels), margin)
        }

    fun trace(msg: String) {
        logs.add(Level.Trace to msg)
    }

    fun info(msg: String) {
        logs.add(Level.Info to msg)
    }

    fun warn(msg: String) {
        logs.add(Level.Warn to msg)
    }

    fun error(msg: String) {
        logs.add(Level.Error to msg)
    }

    private fun logs(levels: Set<Level>) = logs
        .filter { (level, _) -> level in levels }
        .sortedBy { (level, _) -> level.priority }
        .map { (level, msg) -> "${level.char} $msg" }
        .joinToString(" ")

    private fun String.fill(length: Int) = this + " ".repeat(Math.abs(length - this.length))

    private fun joinWithMargin(first: String, second: String, margin: Int) =
        if (second.isBlank()) {
            first
        } else {
            first.fill(margin) + second
        }
}

private val dateFormat = DateTimeFormatter.ISO_LOCAL_DATE

data class Entry(
    val date: LocalDate,
    val typ: String,
    val task: String,
    val centrum: String,
    val time: Double
) {
    fun toCsvLine() = "${date.format(dateFormat)}\t$typ\t$task\t$centrum\trgl\t${time.toString().replace('.',',')}"
}

fun playEspEntry(date: LocalDate, task: String, time: Double) = Entry(date, "P4", task, "P4 ESP", time)
fun playEsbEntry(date: LocalDate, task: String, time: Double) = Entry(date, "P4", task, "P4 ESB", time)
fun unknownEntry(date: LocalDate, task: String, time: Double) = Entry(date, "P4", task, "P4 ???", time)

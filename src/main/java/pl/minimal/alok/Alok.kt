package pl.minimal.alok

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val MIN_MARGIN = 40

private val logsSplitPattern = """\s*[${Level.chars}]""".toRegex()

private fun stripLogs(line: String) = line.split(logsSplitPattern, 2)[0]

private const val SEPARATOR = "------"

fun process(rawLines: List<String>, today: LocalDate = LocalDate.now()): List<String> {
    val lines = rawLines.takeWhile { !it.startsWith(SEPARATOR) }.map { Line(stripLogs(it)) }
    return try {
        val ctx = Context(today = today)
        lines.forEach { process(ctx, it) }
        finalize(ctx)

        val margin = min(80, max(lines.maxOfOrNull { it.content.length + 1 } ?: 0, MIN_MARGIN))
        lines.map { it.toString(ctx.flags, margin) } +
                summaryLines(ctx) +
                allocationLines(ctx)
    } catch (e: Exception) {
        rawLines + listOf(
            SEPARATOR,
            e.message ?: e.toString(),
            ""
        )
    }
}

fun finalize(ctx: Context) {
    // Add day summary to the last day
    ctx.dateLine?.let { addDaySummary(ctx, it) }
}

fun summaryLines(ctx: Context): List<String> =
    listOf(SEPARATOR) +
    listOf(ctx.entries.sumOf { it.time }.let { "Total: ${it}h (${it/8}d)" }) +
    ctx.entries.groupBy { it.task }.mapValues { entry -> entry.value.sumOf { it.time } }.map { (task, time) -> "$task\t${time}h (${time/8}d)" }

fun allocationLines(ctx: Context): List<String> =
    listOf(SEPARATOR) + ctx.entries.map { it.toCsvLine() } + listOf("")


private val flagPattern = """^>\s?(\w+)""".toRegex()

fun process(ctx: Context, line: Line) {
    when {
        line.content.isBlank() -> return
        processComment(line) -> return
        processFlag(ctx, line) -> return
        processDate(ctx, line) -> return
        processAlok(ctx, line) -> return
        processAlias(ctx, line) -> return
        else ->
            line.error("Don't know what to do with this line")
    }
}

fun processComment(line: Line): Boolean =
    if (line.content.startsWith('#')) {
        line.trace("Comment")
        true
    } else {
        false
    }

fun processFlag(ctx: Context, line: Line): Boolean {
    val match = flagPattern.find(line.content) ?: return false
    val maybeFlag = match.groupValues[1]
    val flag = Flag.values().find { it.name == maybeFlag }
    if (flag != null) {
        line.trace("Using $flag")
        ctx.flags.add(flag)
    } else {
        line.warn("Invalid flag: '$maybeFlag', expected one of ${Flag.valuesString}")
    }
    return true
}

private val dateRegexp = """^\s*(\d?\d)\.(\d\d)""".toRegex()

fun addDaySummary(ctx: Context, dateLine: Line) {
    dateLine.info(ctx.entries.filter { it.date == ctx.date }.sumOf { it.time }.toString() + "h")
}

fun processDate(ctx: Context, line: Line): Boolean {
    val match = dateRegexp.find(line.content) ?: return false

    // If previous date line is there, let's add summary
    ctx.dateLine?.let { addDaySummary(ctx, it) }

    val (day, month) = match.destructured
    var date = LocalDate.of(ctx.today.year, month.toInt(), day.toInt())
    // Handle January case
    if (ctx.today.plusMonths(6).isBefore(date)) {
        date = date.minusYears(1)
    }
    ctx.date = date
    line.trace(ctx.date.toString())
    // Add day of week name
    val dayName = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("pl"))
    if (!line.content.contains(dayName)) {
        line.content = line.content + " " + dayName
    }
    // todo: check holidays
    // To be used for summary
    ctx.dateLine = line
    return true
}

private val alokRegexp = """^\s*$UPLOADED?\s*([\w -]+?)\s+-\s+(\d+(?:[,.]\d+)?)h""".toRegex()
private val jiraRegex = """[A-Z]+-[0-9]+""".toRegex()

fun processAlok(ctx: Context, line: Line): Boolean {
    val match = alokRegexp.find(line.content) ?: return false
    val date = ctx.date
    if (date == null) {
        line.error("What date is it? No date found before this line.")
        return true
    }
    val (task, timeStr) = match.destructured
    val timeDouble = timeStr.replace(',', '.').toDouble()
    when {
        task.matches(jiraRegex) -> {
            val entry = if (task.startsWith("ITDEVESP-")) {
                playEspEntry(date, task, timeDouble)
            } else if (task.startsWith("ITDEVBEN-")) {
                playEsbEntry(date, task, timeDouble)
            } else {
                line.error("I don't recognize this JIRA prefix, where to allocate? Should be one of 'ITDEVESP-', 'ITDEVBEN-'")
                unknownEntry(date, task, timeDouble)
            }

            ctx.entries.add(entry)
            line.trace(entry.toString())
        }
        task in ctx.aliases -> {
            val entry = ctx.aliases.getValue(task).copy(date = date, time = timeDouble)
            ctx.entries += entry
            line.trace(entry.toString())
        }
        else -> {
            line.error("Task '$task' doesn't look like Jira (e.g. 'ABCD-123'), neither is on the aliases list: " + ctx.aliases.keys.joinToString())
        }
    }
    return true
}

private val aliasRegexp = """^(.*)=(.*);(.*)""".toRegex()

fun processAlias(ctx: Context, line: Line): Boolean {
    val match = aliasRegexp.find(line.content) ?: return false
    val (alias, centrum, task) = match.destructured
    val entryTemplate = Entry(ctx.today, task, centrum, 0.0)
    ctx.aliases[alias] = entryTemplate
    line.trace("Alias: $alias -> $entryTemplate")
    return true
}

package pl.minimal.alok

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_MARGIN = 40

private val logsSplitPattern = """\s*[${Level.chars}]""".toRegex()

private fun stripLogs(line: String) = line.split(logsSplitPattern, 2)[0]

private const val SEPARATOR = "------"

fun process(rawLines: List<String>, jira: JiraApi, today: LocalDate = LocalDate.now()): List<String> {
    val lines = rawLines.takeWhile { !it.startsWith(SEPARATOR) }.map { Line(stripLogs(it)) }
    return try {
        val ctx = Context(today = today, jira = jira)
        lines.forEach { process(ctx, it) }

        val margin = min(80, max(lines.map { it.content.length + 1 }.maxOrNull() ?: 0, MIN_MARGIN))
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

fun summaryLines(ctx: Context): List<String> =
    listOf(SEPARATOR) +
    listOf(ctx.entries.sumByDouble { it.time }.let { "Total: ${it}h (${it/8}d)" }) +
    ctx.entries.groupBy { it.task }.mapValues { it.value.sumByDouble { it.time } }.map { (task, time) -> "$task\t${time}h (${time/8}d)" }

fun allocationLines(ctx: Context): List<String> =
    listOf(SEPARATOR) + ctx.entries.map { it.toCsvLine() } + listOf("")


private val flagPattern = """^>\s?(\w+)""".toRegex()

fun process(ctx: Context, line: Line) {
    when {
        line.content.isBlank() -> return
        processCookie(ctx, line) -> return
        processComment(line) -> return
        processFlag(ctx, line) -> return
        processDate(ctx, line) -> return
        processAlok(ctx, line) -> return
        processAlias(ctx, line) -> return
        else ->
            line.error("Don't know what to do with this line")
    }
}

fun processCookie(ctx: Context, line: Line): Boolean =
    if (line.content.startsWith("cookie: ")) {
        ctx.jira.cookie = line.content.substring("cookie: ".length).trim()
        line.trace("Cookie set")
        true
    } else {
        false
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

fun processDate(ctx: Context, line: Line): Boolean {
    val match = dateRegexp.find(line.content) ?: return false
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

private val alokRegexp = """^\s*$UPLOADED?\s*([\w\d -]+?)\s+-\s+(\d+(?:[,.]\d+)?)h""".toRegex()
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
    val timeSeconds = (timeDouble * 3600).roundToInt()
    when {
        task.matches(jiraRegex) -> {
            val entry = playEntry(date, task, timeDouble)
            ctx.entries.add(entry)
            line.trace(entry.toString())

            if (Flag.upload in ctx.flags && !line.content.contains(UPLOADED)) {
                try {
                    val result= ctx.jira.putWorklog(entry.task, date, timeSeconds)
                    line.info(result)
                    line.content = if (line.content.startsWith("  ")) {
                        line.content.replaceFirst(" ", UPLOADED)
                    } else {
                        UPLOADED + ' ' + line.content
                    }
                } catch (e: JiraError) {
                    line.error(e.message ?: e.toString())
                }
            }
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

private val aliasRegexp = """^(.*)=(.*);(.*);(.*)""".toRegex()

fun processAlias(ctx: Context, line: Line): Boolean {
    val match = aliasRegexp.find(line.content) ?: return false
    val (alias, typ, task, centrum) = match.destructured
    val entryTemplate = Entry(ctx.today, typ, task, centrum, 0.0)
    ctx.aliases[alias] = entryTemplate
    line.trace("Alias: $alias -> $entryTemplate")
    return true
}

package pl.minimal.alok

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max

private const val MIN_MARGIN = 40

private val logsSplitPattern = """\s*[${Level.chars}]""".toRegex()

private fun stripLogs(line: String) = line.split(logsSplitPattern, 2)[0]

fun process(rawLines: List<String>, today: LocalDate = LocalDate.now()): List<String> {
    val lines = rawLines.map { line ->
        val content = stripLogs(line)
        Line(content, content.contains(UPLOADED))
    }
    val ctx = Context(today = today)
    lines.forEach { process(ctx, it) }

    val margin = max(lines.map { it.content.length + 1 }.maxOrNull() ?: 0, MIN_MARGIN)
    return lines.map { it.toString(ctx.flags, margin) }
}


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
        line.info("Comment")
        true
    } else {
        false
    }

fun processFlag(ctx: Context, line: Line): Boolean {
    val match = flagPattern.find(line.content) ?: return false
    val maybeFlag = match.groupValues[1]
    val flag = Flag.values().find { it.name == maybeFlag }
    if (flag != null) {
        line.info("Using $flag")
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
    if (ctx.today.isBefore(date)) {
        date = date.minusYears(1)
    }
    ctx.date = date
    line.info(ctx.date.toString())
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

private val alokRegexp = """^\s*([\w\d -]+?)\s+-\s+(\d+(?:[,.]\d+)?)h""".toRegex()

fun processAlok(ctx: Context, line: Line): Boolean {
    val match = alokRegexp.find(line.content) ?: return false
    val date = ctx.date
    if (date == null) {
        line.error("What date is it? No date found before this entry.")
        return true
    }
    val (task, timeStr) = match.destructured
    val time = timeStr.replace(',', '.').toDouble()
    when {
        task.startsWith("ITDEVESP-") -> {
            val entry = playEntry(date, task, time)
            ctx.entries.add(entry)
            line.info(entry.toString())

            if (Flag.upload in ctx.flags) {
                ctx.logs.add(Level.Info to "Uploading $entry")
            }
        }
        task in ctx.aliases -> {
            val entry = ctx.aliases.getValue(task).copy(date = date, time = time)
            ctx.entries += entry
            line.info(entry.toString())
        }
        else -> {
            line.error("Task '$task' doesn't look like Jira 'ITDEVESP-XXX', neither is on the aliases list: " + ctx.aliases.keys.joinToString())
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
    line.info("Alias: $alias -> $entryTemplate")
    return true
}

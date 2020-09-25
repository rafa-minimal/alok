package pl.minimal.alok

private const val MIN_MARGIN = 40

private val logsSplitPattern = """\s*[${Level.chars}]""".toRegex()

private fun stripLogs(line: String) = line.split(logsSplitPattern, 2)[0]

fun process(rawLines: List<String>): List<String> {
    val lines = rawLines.map { line ->
        val content = stripLogs(line)
        Line(content, content.contains(UPLOADED))
    }
    val flags = extractFlags(lines)
    val ctx = Context(flags.toSet())

    val margin = Math.max(lines.map { it.content.length + 1}.max() ?: 0, MIN_MARGIN)
    return lines.map { it.toString(ctx.flags, margin) }
}

private val flagPattern = """^\s*>\s?(\w+).*$""".toRegex()

fun extractFlags(lines: List<Line>): Set<Flag> =
    lines.mapNotNull { line ->
        // todo: how to chain all conditions?
        flagPattern.find(line.content)?.let { match ->
            val maybeFlag = match.groupValues[1]
            val flag = Flag.values().find { it.name == maybeFlag }
            if (flag != null) {
                line.info("Using $flag")
                flag
            } else {
                line.warn("Invalid flag: '$maybeFlag', expected one of ${Flag.valuesString}")
                null
            }
        }
    }.toSet()
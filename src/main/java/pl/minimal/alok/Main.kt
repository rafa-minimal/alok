package pl.minimal.alok

import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println("Usage:\nalok file-to-watch")
        return
    }
    val filePath = args[0]
    val path = Path.of(filePath)
    if (!Files.exists(path)) {
        System.err.println("File not found: $filePath (${path.toAbsolutePath()})")
        return
    }
    if (!Files.isReadable(path)) {
        System.err.println("Can't read file: $filePath (${path.toAbsolutePath()})")
        return
    }

    JiraApiImpl().use { jira ->
        val loop = ReadWriteLoop(path, 1000) { lines ->
            process(lines, jira)
        }
        Runtime.getRuntime().addShutdownHook(Thread { loop.stop() })
        loop.run()
    }
}
package pl.minimal.alok

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.Instant
import kotlin.streams.toList

class ReadWriteLoop(private val path: Path,
                    private val intervalMs: Long,
                    private val process: (List<String>) -> List<String>) {
    @Volatile
    private var running = true

    fun run() {
        var lastModified = FileTime.fromMillis(0).toInstant()

        while (running) {
            // would be nice to use file change notifications instead
            if (lastModified.isBefore(path.modifiedDate())) {
                val processedLines = process(Files.lines(path).toList())
                Files.write(path, processedLines)
                lastModified = path.modifiedDate()
            }
            Thread.sleep(intervalMs)
        }
    }

    fun stop() {
        running = false
    }

    private fun Path.modifiedDate(): Instant {
        val attrs = Files.readAttributes(this, BasicFileAttributes::class.java)
        return attrs.lastModifiedTime().toInstant()
    }
}
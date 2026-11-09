package io.github.romulomga.corekit

import java.io.File
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratedFilesTest {
    private val cache: File = Files.createTempDirectory("corekit-cache").toFile()

    @Test
    fun removesOnlyTheGeneratedFilesOfEachFolder() {
        val report = file("shared/App-Relatorio-1.pdf")
        val other = file("shared/logo.png")
        val backup = file("backups/App-backup-1.json")
        val image = file("image_cache/App-backup-2.json")

        val removed = GeneratedFiles.purge(cache, mapOf("shared" to "App-Relatorio-", "backups" to "App-backup-"))

        assertEquals(2, removed)
        assertFalse(report.exists())
        assertFalse(backup.exists())
        assertTrue(other.exists())
        assertTrue(image.exists())
    }

    @Test
    fun aMissingFolderIsNotAnError() {
        assertEquals(0, GeneratedFiles.purge(File(cache, "absent"), mapOf("shared" to "")))
    }

    @Test
    fun withAMinimumAgeAFreshFileStays() {
        val now = Instant.parse("2026-11-09T12:00:00Z")
        val old = file("shared/old.pdf").apply { setLastModified(now.minus(Duration.ofHours(2)).toEpochMilli()) }
        val fresh = file("shared/fresh.pdf").apply { setLastModified(now.minus(Duration.ofMinutes(5)).toEpochMilli()) }

        val removed = GeneratedFiles.purge(cache, mapOf("shared" to ""), olderThan = Duration.ofHours(1), now = now)

        assertEquals(1, removed)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    private fun file(path: String): File = File(cache, path).apply {
        parentFile?.mkdirs()
        writeText("x")
    }
}

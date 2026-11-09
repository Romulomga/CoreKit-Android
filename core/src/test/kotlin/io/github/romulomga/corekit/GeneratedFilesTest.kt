package io.github.romulomga.corekit

import java.io.File
import java.nio.file.Files
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

    private fun file(path: String): File = File(cache, path).apply {
        parentFile?.mkdirs()
        writeText("x")
    }
}

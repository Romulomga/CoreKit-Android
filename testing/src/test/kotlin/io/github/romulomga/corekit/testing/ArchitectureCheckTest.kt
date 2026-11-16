package io.github.romulomga.corekit.testing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchitectureCheckTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val root: File get() = folder.root

    private val noEntityInScreens = ArchitectureRule(
        "no entity in screens",
        files = { it.endsWith("Screen.kt") },
        forbidding = """\bClientRecord\b"""
    )

    @Test
    fun aForbiddenLineIsReportedWithPathLineAndRule() {
        write("feature/clients/list/ClientsScreen.kt", "package a\n\nfun show(client: ClientRecord) = Unit\n")

        val found = ArchitectureCheck.violations(root, listOf(noEntityInScreens))

        assertEquals(
            listOf("feature/clients/list/ClientsScreen.kt:3: no entity in screens: fun show(client: ClientRecord) = Unit"),
            found
        )
    }

    @Test
    fun commentLinesAreIgnored() {
        write(
            "feature/clients/list/ClientsScreen.kt",
            """
            // Never a ClientRecord here.
            /** Shows a ClientRecord as a row,
             * never the ClientRecord itself.
             */
            /* ClientRecord */
            fun show(row: ClientRow) = Unit
            """.trimIndent()
        )

        assertEquals(emptyList<String>(), ArchitectureCheck.violations(root, listOf(noEntityInScreens)))
    }

    @Test
    fun aRuleOnlyAppliesToTheFilesItAccepts() {
        write("data/ClientRepository.kt", "fun load(): ClientRecord = TODO()\n")
        write("feature/clients/list/ClientsViewModel.kt", "fun load(): ClientRecord = TODO()\n")

        assertEquals(emptyList<String>(), ArchitectureCheck.violations(root, listOf(noEntityInScreens)))
    }

    @Test
    fun theFilterSeesThePathRelativeToTheRoot() {
        val paths = mutableListOf<String>()
        write("feature/home/HomeScreen.kt", "")
        write("App.kt", "")

        val recording = ArchitectureRule("any", files = { path -> paths.add(path).not() }, forbidding = "x")

        ArchitectureCheck.violations(root, listOf(recording))

        assertEquals(listOf("App.kt", "feature/home/HomeScreen.kt"), paths)
    }

    @Test
    fun aForbiddenFolderIsReported() {
        write("feature/home/screens/HomeScreen.kt", "")
        write("feature/home/HomeViewModel.kt", "")

        val found = ArchitectureCheck.violations(root, emptyList(), forbiddenFolders = setOf("screens", "viewmodels"))

        assertEquals(listOf("feature/home/screens/HomeScreen.kt: folder screens/ is not allowed"), found)
    }

    @Test
    fun onlyKotlinFilesAreRead() {
        write("feature/home/HomeScreen.java", "ClientRecord record;\n")
        write("feature/home/screens/notes.md", "ClientRecord\n")

        val found = ArchitectureCheck.violations(root, listOf(ArchitectureRule("all", { true }, "ClientRecord")), setOf("screens"))

        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun violationsAreSortedAndUnique() {
        write("b/BScreen.kt", "ClientRecord ClientRecord\n")
        write("a/AScreen.kt", "val x = 1\nval y: ClientRecord\n")
        val duplicate = ArchitectureRule("no entity in screens", { it.endsWith("Screen.kt") }, "ClientRecord")

        val found = ArchitectureCheck.violations(root, listOf(noEntityInScreens, duplicate))

        assertEquals(
            listOf(
                "a/AScreen.kt:2: no entity in screens: val y: ClientRecord",
                "b/BScreen.kt:1: no entity in screens: ClientRecord ClientRecord"
            ),
            found
        )
    }

    @Test
    fun entityTypeNamesReadsRoomEntitiesFromTheCode() {
        write(
            "data/database/Records.kt",
            """
            @Entity(tableName = "clients")
            data class ClientRecord(@PrimaryKey val id: String)

            @Entity(
                tableName = "documents",
                foreignKeys = [
                    ForeignKey(
                        entity = ClientRecord::class,
                        parentColumns = ["id"],
                        childColumns = ["clientId"]
                    )
                ]
            )
            data class DocumentRecord(@PrimaryKey val id: String, val clientId: String)

            // @Entity
            class NotAnEntity

            @Database(entities = [ClientRecord::class, DocumentRecord::class], version = 1)
            abstract class AppDatabase : RoomDatabase()
            """.trimIndent()
        )
        write("data/database/Other.kt", "@Entity class TagRecord(val id: String)\n")

        assertEquals(listOf("ClientRecord", "DocumentRecord", "TagRecord"), ArchitectureCheck.entityTypeNames(root))
    }

    private fun write(path: String, text: String) {
        val file = root.resolve(path)
        file.parentFile.mkdirs()
        file.writeText(text)
    }
}

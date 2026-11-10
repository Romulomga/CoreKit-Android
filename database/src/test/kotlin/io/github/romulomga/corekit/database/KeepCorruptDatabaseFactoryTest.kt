package io.github.romulomga.corekit.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeepCorruptDatabaseFactoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val file: File = context.getDatabasePath(NAME).also { it.parentFile?.mkdirs() }
    private val garbage = ByteArray(GARBAGE_BYTES) { (it % 251).toByte() }

    private val schema = object : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) = db.execSQL("CREATE TABLE notes (text TEXT NOT NULL)")

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun helper(factory: SupportSQLiteOpenHelper.Factory): SupportSQLiteOpenHelper =
        factory.create(SupportSQLiteOpenHelper.Configuration.builder(context).name(NAME).callback(schema).build())

    @Test
    fun `a file that is not a database fails to open and is left untouched`() {
        file.writeBytes(garbage)

        val helper = helper(KeepCorruptDatabaseFactory())

        assertThrows(Exception::class.java) { helper.writableDatabase }
        assertArrayEquals(garbage, file.readBytes())
    }

    /** O controle: é isto que a fábrica evita. Se o padrão do Android mudar, este teste avisa. */
    @Test
    fun `the default factory silently replaces the same file with an empty database`() {
        file.writeBytes(garbage)

        helper(FrameworkSQLiteOpenHelperFactory()).use { it.writableDatabase }

        assertFalse(garbage.contentEquals(file.readBytes()))
    }

    @Test
    fun `a healthy database opens, is created and keeps its rows`() {
        helper(KeepCorruptDatabaseFactory()).use { it.writableDatabase.execSQL("INSERT INTO notes VALUES ('kept')") }

        val count = helper(KeepCorruptDatabaseFactory()).use { reopened ->
            reopened.readableDatabase.query("SELECT COUNT(*) FROM notes").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }

        assertEquals(1, count)
    }

    private companion object {
        const val NAME = "corrupt-test.db"
        const val GARBAGE_BYTES = 8192
    }
}

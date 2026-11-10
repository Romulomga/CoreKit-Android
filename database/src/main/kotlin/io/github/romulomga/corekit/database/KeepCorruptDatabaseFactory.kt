package io.github.romulomga.corekit.database

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * O Android, por padrão, apaga o arquivo de um banco que o SQLite acusa de corrompido e abre outro vazio no lugar: a
 * pessoa encontraria o app zerado, sem aviso e sem chance de recuperar o arquivo. Com esta fábrica o arquivo fica onde
 * está e a abertura lança; cabe ao app transformar isso numa tela de recuperação, nunca em dados apagados.
 *
 * ```
 * Room.databaseBuilder(context, AppDatabase::class.java, "app.db").openHelperFactory(KeepCorruptDatabaseFactory()).build()
 * ```
 */
public class KeepCorruptDatabaseFactory : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(KeepOnCorruption(configuration.callback))
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(false)
                .build()
        )
}

private class KeepOnCorruption(private val wrapped: SupportSQLiteOpenHelper.Callback) : SupportSQLiteOpenHelper.Callback(wrapped.version) {
    override fun onConfigure(db: SupportSQLiteDatabase) = wrapped.onConfigure(db)

    override fun onCreate(db: SupportSQLiteDatabase) = wrapped.onCreate(db)

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = wrapped.onUpgrade(db, oldVersion, newVersion)

    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = wrapped.onDowngrade(db, oldVersion, newVersion)

    override fun onOpen(db: SupportSQLiteDatabase) = wrapped.onOpen(db)

    // O padrão (`super.onCorruption`) é o que apaga o arquivo.
    override fun onCorruption(db: SupportSQLiteDatabase) {
        Log.e("CoreKit.database", "sqlite reported corruption; the file is kept")
    }
}

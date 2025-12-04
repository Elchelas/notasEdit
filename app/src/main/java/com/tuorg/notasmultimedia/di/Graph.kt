package com.tuorg.notasmultimedia.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tuorg.notasmultimedia.data.DefaultNoteRepository
import com.tuorg.notasmultimedia.data.NoteRepository
import com.tuorg.notasmultimedia.data.local.LocalNoteDataSource
import com.tuorg.notasmultimedia.data.remote.RemoteNoteDataSource
import com.tuorg.notasmultimedia.data.sync.SyncStateStore
import com.tuorg.notasmultimedia.model.db.AppDatabase
import android.content.ContentResolver

object Graph {
    lateinit var db: AppDatabase
        private set

    lateinit var notes: NoteRepository
        private set

    lateinit var contentResolver: ContentResolver
        private set

    lateinit var appContext: Context
        private set
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE notes ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE notes ADD COLUMN dirty INTEGER NOT NULL DEFAULT 0")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS sync_outbox(
                    id TEXT NOT NULL PRIMARY KEY,
                    type TEXT NOT NULL,
                    payloadJson TEXT NOT NULL,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext

        db = Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
            .addMigrations(MIGRATION_1_2)
            .build()
        contentResolver = context.contentResolver
        db = Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
            .addMigrations(MIGRATION_1_2)
            .build()

        val local = LocalNoteDataSource(db)
        val remote = RemoteNoteDataSource() // stub
        val syncState = SyncStateStore(context)

        notes = DefaultNoteRepository(local, remote, db, syncState)
    }
}

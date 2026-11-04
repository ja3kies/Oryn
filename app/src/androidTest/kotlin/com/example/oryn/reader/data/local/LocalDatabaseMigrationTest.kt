package com.example.oryn.reader.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Opens isolated historical databases through the real Room migration chain. Never opens user data. */
@RunWith(AndroidJUnit4::class)
class LocalDatabaseMigrationTest {
    @Test fun versionOneBookSurvivesEveryMigrationWithItsIdentityAndReadingState() = migrate(1)
    @Test fun versionTwoCoverSurvivesAnnotationAndHashMigrations() = migrate(2)
    @Test fun versionThreeAnnotationAndCoverSurviveHashMigration() = migrate(3)

    private fun migrate(version: Int) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "oryn-migration-test-${UUID.randomUUID()}.db"
        val path = context.getDatabasePath(name)
        path.parentFile?.mkdirs()
        var room: OrynDatabase? = null
        try {
            SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
                db.execSQL("CREATE TABLE books (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, author TEXT NOT NULL, filePath TEXT NOT NULL, format TEXT NOT NULL, progress REAL NOT NULL, isFavorite INTEGER NOT NULL, lastReadTimestamp INTEGER NOT NULL${if (version >= 2) ", coverPath TEXT" else ""})")
                db.execSQL("INSERT INTO books (id,title,author,filePath,format,progress,isFavorite,lastReadTimestamp${if (version >= 2) ",coverPath" else ""}) VALUES (41,'Livre é📚','Auteur','content://original/book','EPUB',0.375,1,123456${if (version >= 2) ",'private-cover.png'" else ""})")
                if (version >= 3) {
                    db.execSQL("CREATE TABLE annotations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, bookId INTEGER NOT NULL, startOffset INTEGER NOT NULL, endOffset INTEGER NOT NULL, kind TEXT NOT NULL, color INTEGER NOT NULL, note TEXT, quote TEXT NOT NULL, createdAt INTEGER NOT NULL)")
                    db.execSQL("CREATE INDEX index_annotations_bookId ON annotations (bookId)")
                    db.execSQL("INSERT INTO annotations VALUES (7,41,3,8,'HIGHLIGHT',2,'Note conservée','livre',654321)")
                }
                db.version = version
            }
            // Production keeps these migration objects private. Reflect only our own class, not Android APIs.
            val migrations = OrynDatabase::class.java.declaredFields
                .filter { Migration::class.java.isAssignableFrom(it.type) }
                .map { field -> field.isAccessible = true; field.get(null) as Migration }
                .toTypedArray()
            assertEquals("All historical migrations must be exercised", 3, migrations.size)
            room = Room.databaseBuilder(context, OrynDatabase::class.java, name)
                .addMigrations(*migrations).build()
            val db = room.openHelper.writableDatabase // Room validates the complete resulting schema.
            db.query("SELECT id,title,author,filePath,format,progress,isFavorite,lastReadTimestamp,coverPath,contentHash FROM books").use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(41L, row.getLong(0))
                assertEquals("Livre é📚", row.getString(1))
                assertEquals("Auteur", row.getString(2))
                assertEquals("content://original/book", row.getString(3))
                assertEquals("EPUB", row.getString(4))
                assertEquals(0.375, row.getDouble(5), 0.000001)
                assertEquals(1, row.getInt(6))
                assertEquals(123456L, row.getLong(7))
                if (version >= 2) assertEquals("private-cover.png", row.getString(8)) else assertTrue(row.isNull(8))
                assertTrue(row.isNull(9))
                assertFalse(row.moveToNext())
            }
            db.query("SELECT bookId,note,quote FROM annotations").use { row ->
                if (version >= 3) {
                    assertTrue(row.moveToFirst())
                    assertEquals(41L, row.getLong(0))
                    assertEquals("Note conservée", row.getString(1))
                    assertEquals("livre", row.getString(2))
                    assertFalse(row.moveToNext())
                } else assertFalse(row.moveToFirst())
            }
        } finally {
            room?.close()
            context.deleteDatabase(name)
        }
    }
}

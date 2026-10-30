package com.example.oryn.reader.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.oryn.reader.data.local.dao.AnnotationDao
import com.example.oryn.reader.data.local.dao.BookDao
import com.example.oryn.reader.data.local.entity.AnnotationEntity
import com.example.oryn.reader.data.local.entity.BookEntity

@Database(entities = [BookEntity::class, AnnotationEntity::class], version = 4, exportSchema = false)
abstract class OrynDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        @Volatile
        private var INSTANCE: OrynDatabase? = null

        /** v1 -> v2 : ajout de la couverture, sans perdre la bibliothèque existante. */
        /** v3 -> v4 : hash de contenu (couvertures stables). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN contentHash TEXT")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN coverPath TEXT")
            }
        }

        /** v2 -> v3 : annotations (surlignage, soulignage, notes). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `annotations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`bookId` INTEGER NOT NULL, `startOffset` INTEGER NOT NULL, `endOffset` INTEGER NOT NULL, " +
                        "`kind` TEXT NOT NULL, `color` INTEGER NOT NULL, `note` TEXT, `quote` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_annotations_bookId` ON `annotations` (`bookId`)")
            }
        }

        fun getDatabase(context: Context): OrynDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OrynDatabase::class.java,
                    "oryn_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

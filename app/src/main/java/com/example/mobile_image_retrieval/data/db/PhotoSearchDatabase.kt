package com.example.mobile_image_retrieval.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaEmbeddingEntity::class, SearchHistoryEntity::class, IndexingStateEntity::class, PersonEntity::class, FaceIndexEntity::class, FaceEmbeddingEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PhotoSearchDatabase : RoomDatabase() {
    abstract fun mediaEmbeddingDao(): MediaEmbeddingDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun indexingStateDao(): IndexingStateDao
    abstract fun personDao(): PersonDao
    abstract fun faceEmbeddingDao(): FaceEmbeddingDao

    companion object {
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `people` ADD COLUMN `embeddingModel` TEXT NOT NULL DEFAULT 'mobileclip2-s0'")
                db.execSQL("CREATE TABLE IF NOT EXISTS `face_index` (`mediaId` INTEGER NOT NULL, `dateModified` INTEGER NOT NULL, `modelVersion` TEXT NOT NULL, PRIMARY KEY(`mediaId`), FOREIGN KEY(`mediaId`) REFERENCES `media_embeddings`(`mediaId`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `face_embeddings` (`mediaId` INTEGER NOT NULL, `faceIndex` INTEGER NOT NULL, `embedding` BLOB NOT NULL, `embeddingDimension` INTEGER NOT NULL, PRIMARY KEY(`mediaId`, `faceIndex`), FOREIGN KEY(`mediaId`) REFERENCES `media_embeddings`(`mediaId`) ON UPDATE NO ACTION ON DELETE CASCADE)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `people` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `handle` TEXT NOT NULL, `thumbnail` BLOB NOT NULL, `embedding` BLOB NOT NULL, `embeddingDimension` INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_people_handle` ON `people` (`handle`)")
            }
        }
    }
}

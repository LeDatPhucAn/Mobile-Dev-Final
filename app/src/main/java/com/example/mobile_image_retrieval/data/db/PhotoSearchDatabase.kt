package com.example.mobile_image_retrieval.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [MediaEmbeddingEntity::class, SearchHistoryEntity::class, IndexingStateEntity::class, PersonEntity::class, FaceIndexEntity::class, FaceEmbeddingEntity::class, PhotoTextEntity::class, PhotoTextFts::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PhotoSearchDatabase : RoomDatabase() {
    abstract fun mediaEmbeddingDao(): MediaEmbeddingDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun indexingStateDao(): IndexingStateDao
    abstract fun personDao(): PersonDao
    abstract fun faceEmbeddingDao(): FaceEmbeddingDao
    abstract fun photoTextDao(): PhotoTextDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `search_history` ADD COLUMN `searchMode` TEXT NOT NULL DEFAULT 'HYBRID'")
                db.execSQL("CREATE TABLE IF NOT EXISTS `photo_text` (`rowid` INTEGER NOT NULL, `text` TEXT NOT NULL, `searchText` TEXT NOT NULL, `dateModified` INTEGER NOT NULL, `modelVersion` TEXT NOT NULL, `truncated` INTEGER NOT NULL, PRIMARY KEY(`rowid`), FOREIGN KEY(`rowid`) REFERENCES `media_embeddings`(`mediaId`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `photo_text_fts` USING FTS4(`searchText` TEXT NOT NULL, tokenize=unicode61, content=`photo_text`)")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_photo_text_fts_BEFORE_UPDATE BEFORE UPDATE ON `photo_text` BEGIN DELETE FROM `photo_text_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_photo_text_fts_BEFORE_DELETE BEFORE DELETE ON `photo_text` BEGIN DELETE FROM `photo_text_fts` WHERE `docid`=OLD.`rowid`; END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_photo_text_fts_AFTER_UPDATE AFTER UPDATE ON `photo_text` BEGIN INSERT INTO `photo_text_fts`(`docid`, `searchText`) VALUES (NEW.`rowid`, NEW.`searchText`); END")
                db.execSQL("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_photo_text_fts_AFTER_INSERT AFTER INSERT ON `photo_text` BEGIN INSERT INTO `photo_text_fts`(`docid`, `searchText`) VALUES (NEW.`rowid`, NEW.`searchText`); END")
            }
        }
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

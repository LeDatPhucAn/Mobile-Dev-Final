package com.example.mobile_image_retrieval.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [MediaEmbeddingEntity::class, SearchHistoryEntity::class, IndexingStateEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PhotoSearchDatabase : RoomDatabase() {
    abstract fun mediaEmbeddingDao(): MediaEmbeddingDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun indexingStateDao(): IndexingStateDao
}

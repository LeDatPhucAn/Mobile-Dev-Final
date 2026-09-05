package com.example.mobile_image_retrieval.data.db

import androidx.room.Dao
import androidx.room.Upsert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoTextDao {
    @Upsert
    suspend fun write(document: PhotoTextEntity)

    @Query("SELECT dateModified FROM media_embeddings WHERE mediaId = :id")
    suspend fun parentRevision(id: Long): Long?

    @Transaction
    suspend fun upsert(document: PhotoTextEntity) {
        if (parentRevision(document.mediaId) == document.dateModified) write(document)
    }

    @Query("SELECT * FROM photo_text WHERE rowid = :mediaId")
    suspend fun byMediaId(mediaId: Long): PhotoTextEntity?

    @Query("SELECT rowid AS mediaId, dateModified, modelVersion FROM photo_text")
    suspend fun indexStates(): List<PhotoTextIndexState>

    @Query("SELECT COUNT(*) FROM photo_text AS t INNER JOIN media_embeddings AS m ON t.rowid = m.mediaId WHERE t.modelVersion = :version AND t.dateModified = m.dateModified")
    fun observeIndexedCount(version: String): Flow<Int>

    @Query("""
        SELECT t.rowid FROM photo_text_fts
        INNER JOIN photo_text AS t ON photo_text_fts.rowid = t.rowid
        INNER JOIN media_embeddings AS m ON t.rowid = m.mediaId
        WHERE photo_text_fts MATCH :expression AND t.modelVersion = :version AND t.dateModified = m.dateModified
    """)
    suspend fun matchingIds(expression: String, version: String): List<Long>
}

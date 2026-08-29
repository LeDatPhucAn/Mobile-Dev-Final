package com.example.mobile_image_retrieval.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaEmbeddingEntity)

    @Query("SELECT mediaId, dateModified FROM media_embeddings")
    suspend fun indexStates(): List<MediaIndexState>

    @Query("SELECT COUNT(*) FROM media_embeddings")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_embeddings")
    suspend fun count(): Int

    @Query("SELECT * FROM media_embeddings WHERE mediaId = :mediaId")
    suspend fun byId(mediaId: Long): MediaEmbeddingEntity?

    @Query("DELETE FROM media_embeddings WHERE mediaId IN (:ids)")
    suspend fun deleteIds(ids: List<Long>)

    @Query(
        """
        SELECT mediaId, uri, mediaType, displayName, dateTaken, dateAdded, dateModified,
               width, height, mimeType, bucketId, bucketName, embedding, embeddingDimension
        FROM media_embeddings
        WHERE (:startMillis IS NULL OR COALESCE(dateTaken, dateAdded * 1000) >= :startMillis)
          AND (:endExclusiveMillis IS NULL OR COALESCE(dateTaken, dateAdded * 1000) < :endExclusiveMillis)
          AND (:mediaType IS NULL OR mediaType = :mediaType)
        ORDER BY mediaId
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun searchPage(
        startMillis: Long?,
        endExclusiveMillis: Long?,
        mediaType: String?,
        limit: Int,
        offset: Int,
    ): List<SearchCandidate>
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 100")
    fun observeRecent(): Flow<List<SearchHistoryEntity>>

    @Insert
    suspend fun insertInternal(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY timestamp DESC LIMIT 100)")
    suspend fun trimToLatestHundred()

    @Transaction
    suspend fun insert(entity: SearchHistoryEntity) {
        insertInternal(entity)
        trimToLatestHundred()
    }

    @Query("DELETE FROM search_history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM search_history")
    suspend fun count(): Int
}

@Dao
interface IndexingStateDao {
    @Query("SELECT * FROM indexing_state WHERE id = 1")
    fun observe(): Flow<IndexingStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: IndexingStateEntity)
}

package com.example.mobile_image_retrieval.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaEmbeddingDao {
    @Upsert
    suspend fun write(entity: MediaEmbeddingEntity)

    @Transaction
    suspend fun upsert(entity: MediaEmbeddingEntity) {
        // Filling an embedding must retain already scanned OCR/faces for this photo revision.
        deleteChanged(entity.mediaId, entity.dateModified)
        write(entity)
    }

    @Query("DELETE FROM media_embeddings WHERE mediaId = :id AND dateModified != :modified")
    suspend fun deleteChanged(id: Long, modified: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMetadata(entity: MediaEmbeddingEntity)

    @Transaction
    suspend fun ensureMetadata(entity: MediaEmbeddingEntity) {
        require(entity.embeddingDimension == 0 && entity.embedding.isEmpty())
        if ((byId(entity.mediaId)?.dateModified ?: Long.MIN_VALUE) > entity.dateModified) return
        deleteChanged(entity.mediaId, entity.dateModified)
        insertMetadata(entity)
    }

    @Query("SELECT mediaId, dateModified, embeddingDimension FROM media_embeddings")
    suspend fun indexStates(): List<MediaIndexState>

    @Query("SELECT COUNT(*) FROM media_embeddings WHERE embeddingDimension > 0")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_embeddings WHERE embeddingDimension > 0")
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
          AND (:includeMetadata OR embeddingDimension > 0)
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
        includeMetadata: Boolean = false,
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
    @Query("SELECT * FROM indexing_state WHERE id = :id")
    fun observe(id: Int = 1): Flow<IndexingStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: IndexingStateEntity)
}

@Dao
interface PersonDao {
    @Query("SELECT id, name, handle, thumbnail, embeddingModel FROM people ORDER BY handle")
    fun observePeople(): Flow<List<SavedPerson>>

    @Query("SELECT * FROM people WHERE handle IN (:handles)")
    suspend fun byHandles(handles: List<String>): List<PersonEntity>

    @Insert
    suspend fun insert(person: PersonEntity)

    @Query("UPDATE people SET thumbnail = :thumbnail, embedding = :embedding, embeddingDimension = :dimension, embeddingModel = :model WHERE id = :id")
    suspend fun updateFace(id: Long, thumbnail: ByteArray, embedding: ByteArray, dimension: Int, model: String): Int

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FaceEmbeddingDao {
    @Query("SELECT * FROM face_index")
    suspend fun indexStates(): List<FaceIndexEntity>

    @Query("SELECT COUNT(*) FROM face_index AS f INNER JOIN media_embeddings AS m ON f.mediaId = m.mediaId WHERE f.modelVersion = :model AND f.dateModified = m.dateModified")
    fun observeIndexedCount(model: String): Flow<Int>

    @Query("SELECT f.* FROM face_embeddings AS f INNER JOIN face_index AS s ON f.mediaId = s.mediaId INNER JOIN media_embeddings AS m ON f.mediaId = m.mediaId WHERE f.mediaId IN (:ids) AND s.modelVersion = :model AND s.dateModified = m.dateModified ORDER BY f.mediaId, f.faceIndex")
    suspend fun forMedia(ids: List<Long>, model: String): List<FaceEmbeddingEntity>

    @Query("DELETE FROM face_embeddings WHERE mediaId = :mediaId")
    suspend fun deleteFaces(mediaId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun writeState(state: FaceIndexEntity)

    @Insert
    suspend fun insertFaces(faces: List<FaceEmbeddingEntity>)

    @Transaction
    suspend fun replace(state: FaceIndexEntity, faces: List<FaceEmbeddingEntity>) {
        require(faces.all { it.mediaId == state.mediaId })
        deleteFaces(state.mediaId)
        insertFaces(faces)
        writeState(state) // Empty detections also mark the photo as processed.
    }
}

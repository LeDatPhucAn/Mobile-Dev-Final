package com.example.mobile_image_retrieval.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.MediaType

@Entity(
    tableName = "media_embeddings",
    indices = [Index("dateModified"), Index("dateTaken"), Index("bucketId")],
)
data class MediaEmbeddingEntity(
    @PrimaryKey val mediaId: Long,
    val uri: String,
    val mediaType: MediaType,
    val displayName: String?,
    val dateTaken: Long?,
    val dateAdded: Long?,
    val dateModified: Long,
    val width: Int?,
    val height: Int?,
    val mimeType: String?,
    val bucketId: String?,
    val bucketName: String?,
    val embedding: ByteArray,
    val embeddingDimension: Int,
    val indexedAt: Long,
) {
    fun toMediaItem() = MediaItem(
        mediaId, uri, mediaType, displayName, dateTaken, dateAdded, dateModified,
        width, height, mimeType, bucketId, bucketName,
    )
}

@Entity(tableName = "search_history", indices = [Index("timestamp")])
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestamp: Long,
    val topResultUri: String?,
)

@Entity(tableName = "indexing_state")
data class IndexingStateEntity(
    @PrimaryKey val id: Int = 1,
    val processed: Int,
    val total: Int,
    val failed: Int,
    val status: String,
    val error: String?,
    val updatedAt: Long,
)

data class MediaIndexState(val mediaId: Long, val dateModified: Long)

data class SearchCandidate(
    val mediaId: Long,
    val uri: String,
    val mediaType: MediaType,
    val displayName: String?,
    val dateTaken: Long?,
    val dateAdded: Long?,
    val dateModified: Long,
    val width: Int?,
    val height: Int?,
    val mimeType: String?,
    val bucketId: String?,
    val bucketName: String?,
    val embedding: ByteArray,
    val embeddingDimension: Int,
) {
    fun toMediaItem() = MediaItem(
        mediaId, uri, mediaType, displayName, dateTaken, dateAdded, dateModified,
        width, height, mimeType, bucketId, bucketName,
    )
}

package com.example.mobile_image_retrieval.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
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

@Entity(tableName = "people", indices = [Index(value = ["handle"], unique = true)])
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val handle: String,
    val thumbnail: ByteArray,
    val embedding: ByteArray,
    val embeddingDimension: Int,
    @ColumnInfo(defaultValue = "'mobileclip2-s0'") val embeddingModel: String = "mobileclip2-s0",
)

data class SavedPerson(val id: Long, val name: String, val handle: String, val thumbnail: ByteArray, val embeddingModel: String)

@Entity(
    tableName = "face_index",
    foreignKeys = [ForeignKey(entity = MediaEmbeddingEntity::class, parentColumns = ["mediaId"], childColumns = ["mediaId"], onDelete = ForeignKey.CASCADE)],
)
data class FaceIndexEntity(@PrimaryKey val mediaId: Long, val dateModified: Long, val modelVersion: String)

@Entity(
    tableName = "face_embeddings", primaryKeys = ["mediaId", "faceIndex"],
    foreignKeys = [ForeignKey(entity = MediaEmbeddingEntity::class, parentColumns = ["mediaId"], childColumns = ["mediaId"], onDelete = ForeignKey.CASCADE)],
)
data class FaceEmbeddingEntity(val mediaId: Long, val faceIndex: Int, val embedding: ByteArray, val embeddingDimension: Int)

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

package com.example.mobile_image_retrieval.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.PrimaryKey

@Entity(tableName = "photo_text", foreignKeys = [ForeignKey(
    entity = MediaEmbeddingEntity::class, parentColumns = ["mediaId"], childColumns = ["rowid"], onDelete = ForeignKey.CASCADE,
)])
data class PhotoTextEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val mediaId: Long,
    val text: String,
    val searchText: String,
    val dateModified: Long,
    val modelVersion: String,
    val truncated: Boolean = false,
)

@Fts4(contentEntity = PhotoTextEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "photo_text_fts")
data class PhotoTextFts(val searchText: String)

data class PhotoTextIndexState(val mediaId: Long, val dateModified: Long, val modelVersion: String)

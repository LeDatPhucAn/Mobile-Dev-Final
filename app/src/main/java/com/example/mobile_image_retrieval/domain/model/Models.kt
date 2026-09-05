package com.example.mobile_image_retrieval.domain.model

enum class MediaType { IMAGE, VIDEO }

data class MediaItem(
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
) : java.io.Serializable

enum class TimeRange { ANY_TIME, TODAY, YESTERDAY, THIS_WEEK, THIS_MONTH, CUSTOM }
enum class ResultSort { MOST_RELEVANT, NEWEST_FIRST, OLDEST_FIRST }
enum class SearchMode {
    NORMAL, OCR;

    companion object {
        // Preserve searches saved by the previous three-mode UI.
        fun fromStored(value: String) = if (value == "OCR" || value == "TEXT_IN_PHOTOS") OCR else NORMAL
    }
}

data class SearchFilters(
    val timeRange: TimeRange = TimeRange.ANY_TIME,
    val sort: ResultSort = ResultSort.MOST_RELEVANT,
    val mediaType: MediaType? = null,
    val customStartMillis: Long? = null,
    val customEndExclusiveMillis: Long? = null,
    val searchMode: SearchMode = SearchMode.NORMAL,
) : java.io.Serializable

data class SearchResult(val media: MediaItem, val rawSimilarity: Float, val textMatch: Boolean = false)

data class Album(
    val id: String,
    val name: String,
    val count: Int,
    val coverUri: String?,
    val isSystemCollection: Boolean = false,
)

sealed interface IndexingStatus {
    data object Idle : IndexingStatus
    data class Running(val indexed: Int, val total: Int) : IndexingStatus
    data class Waiting(val indexed: Int, val total: Int, val reason: String? = null) : IndexingStatus
    data class Interrupted(val indexed: Int, val total: Int) : IndexingStatus
    data class Unavailable(val reason: String) : IndexingStatus
}

sealed interface UiError {
    data class ModelUnavailable(val message: String) : UiError
    data class Permission(val message: String) : UiError
    data class Search(val message: String) : UiError
    data class Storage(val message: String) : UiError
}

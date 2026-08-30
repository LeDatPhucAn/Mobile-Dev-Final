package com.example.mobile_image_retrieval.domain.model

/**
 * Defines the album collections shown by the app and resolves their contents.
 * Keeping both operations here prevents an album's count and opened contents
 * from being calculated with subtly different rules.
 */
object AlbumCatalog {
    const val ALL_PHOTOS_ID = "all"
    const val RECENTLY_ADDED_ID = "recent"

    private const val RECENT_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000

    fun build(
        photos: List<MediaItem>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<Album> {
        if (photos.isEmpty()) return emptyList()

        val albums = mutableListOf(
            Album(
                id = ALL_PHOTOS_ID,
                name = "All Photos",
                count = photos.size,
                coverUri = photos.first().uri,
                isSystemCollection = true,
            ),
        )
        val recentPhotos = photosFor(RECENTLY_ADDED_ID, photos, nowMillis)
        if (recentPhotos.isNotEmpty()) {
            albums += Album(
                id = RECENTLY_ADDED_ID,
                name = "Recently Added",
                count = recentPhotos.size,
                coverUri = recentPhotos.maxByOrNull { it.dateAdded ?: 0 }?.uri,
                isSystemCollection = true,
            )
        }

        photos.groupBy { it.bucketId to it.bucketName }
            .filterKeys { (id, name) -> id != null && !name.isNullOrBlank() }
            .entries
            .sortedByDescending { it.value.size }
            .forEach { (bucket, bucketPhotos) ->
                albums += Album(
                    id = bucket.first!!,
                    name = bucket.second!!,
                    count = bucketPhotos.size,
                    coverUri = bucketPhotos.firstOrNull()?.uri,
                )
            }

        return albums.distinctBy { it.id }
    }

    fun photosFor(
        albumId: String,
        photos: List<MediaItem>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<MediaItem> = when (albumId) {
        ALL_PHOTOS_ID -> photos
        RECENTLY_ADDED_ID -> {
            val cutoffSeconds = (nowMillis - RECENT_WINDOW_MILLIS) / 1000
            photos.filter { (it.dateAdded ?: 0) >= cutoffSeconds }
        }
        else -> photos.filter { it.bucketId == albumId }
    }
}

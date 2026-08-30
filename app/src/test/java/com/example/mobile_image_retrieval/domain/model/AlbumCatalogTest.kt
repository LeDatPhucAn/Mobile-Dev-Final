package com.example.mobile_image_retrieval.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumCatalogTest {
    @Test
    fun `album counts and resolved contents use the same rules`() {
        val nowMillis = 2_000_000_000_000L
        val recentSeconds = nowMillis / 1000 - 60
        val oldSeconds = nowMillis / 1000 - 31L * 24 * 60 * 60
        val photos = listOf(
            photo(1, "camera", "Camera", recentSeconds),
            photo(2, "camera", "Camera", oldSeconds),
            photo(3, "screenshots", "Screenshots", recentSeconds - 1),
        )

        val albums = AlbumCatalog.build(photos, nowMillis)

        albums.forEach { album ->
            assertEquals(album.count, AlbumCatalog.photosFor(album.id, photos, nowMillis).size)
        }
        assertEquals(
            listOf(1L, 3L),
            AlbumCatalog.photosFor(AlbumCatalog.RECENTLY_ADDED_ID, photos, nowMillis).map { it.mediaId },
        )
    }

    @Test
    fun `empty and invalid buckets do not create albums`() {
        assertTrue(AlbumCatalog.build(emptyList()).isEmpty())

        val albums = AlbumCatalog.build(
            listOf(
                photo(1, null, null, 1),
                photo(2, "", "", 1),
            ),
            nowMillis = 1_000,
        )

        assertEquals(listOf(AlbumCatalog.ALL_PHOTOS_ID, AlbumCatalog.RECENTLY_ADDED_ID), albums.map { it.id })
    }

    @Test
    fun `recently added includes the cutoff boundary`() {
        val nowMillis = 2_000_000_000_000L
        val cutoffSeconds = (nowMillis - 30L * 24 * 60 * 60 * 1000) / 1000
        val photos = listOf(
            photo(1, "camera", "Camera", cutoffSeconds),
            photo(2, "camera", "Camera", cutoffSeconds - 1),
        )

        val recent = AlbumCatalog.photosFor(AlbumCatalog.RECENTLY_ADDED_ID, photos, nowMillis)

        assertEquals(listOf(1L), recent.map { it.mediaId })
    }

    private fun photo(
        id: Long,
        bucketId: String?,
        bucketName: String?,
        dateAdded: Long,
    ) = MediaItem(
        mediaId = id,
        uri = "content://photos/$id",
        mediaType = MediaType.IMAGE,
        displayName = "$id.jpg",
        dateTaken = null,
        dateAdded = dateAdded,
        dateModified = dateAdded,
        width = 100,
        height = 100,
        mimeType = "image/jpeg",
        bucketId = bucketId,
        bucketName = bucketName,
    )
}

package com.example.mobile_image_retrieval.data.repository

import com.example.mobile_image_retrieval.data.db.MediaIndexState
import com.example.mobile_image_retrieval.data.db.FaceIndexEntity
import com.example.mobile_image_retrieval.ai.FaceModelContract
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexingPlannerTest {
    private fun media(id: Long, modified: Long) = MediaItem(id, "content://$id", MediaType.IMAGE, null, null, null, modified, null, null, null, null, null)

    @Test fun `new photo is embedded`() = assertEquals(listOf(2L), IndexingPlanner.create(listOf(media(1, 1), media(2, 1)), listOf(MediaIndexState(1, 1))).toEmbed.map { it.mediaId })
    @Test fun `unchanged photo is skipped`() = assertEquals(1, IndexingPlanner.create(listOf(media(1, 2)), listOf(MediaIndexState(1, 2))).unchangedCount)
    @Test fun `modified photo is re-embedded`() = assertEquals(listOf(1L), IndexingPlanner.create(listOf(media(1, 3)), listOf(MediaIndexState(1, 2))).toEmbed.map { it.mediaId })
    @Test fun `deleted photo is removed`() = assertEquals(listOf(9L), IndexingPlanner.create(listOf(media(1, 1)), listOf(MediaIndexState(1, 1), MediaIndexState(9, 1))).deletedIds)

    @Test fun `upgrade backfills faces without redoing unchanged semantic embeddings`() {
        val images = listOf(media(1, 2))
        assertEquals(emptyList<Long>(), IndexingPlanner.create(images, listOf(MediaIndexState(1, 2))).toEmbed.map { it.mediaId })
        assertEquals(images, IndexingPlanner.facePending(images, emptyList(), emptySet()))
    }

    @Test fun `unchanged face scans including empty detections are skipped`() {
        assertEquals(emptyList<MediaItem>(), IndexingPlanner.facePending(listOf(media(1, 2)), listOf(FaceIndexEntity(1, 2, FaceModelContract.VERSION)), emptySet()))
    }

    @Test fun `modified photos and old models are reprocessed`() {
        val images = listOf(media(1, 3), media(2, 2))
        val stored = listOf(FaceIndexEntity(1, 2, FaceModelContract.VERSION), FaceIndexEntity(2, 2, "older-model"))
        assertEquals(images, IndexingPlanner.facePending(images, stored, emptySet()))
    }

    @Test fun `semantic replacement invalidates its face index`() {
        val images = listOf(media(1, 2))
        assertEquals(images, IndexingPlanner.facePending(images, listOf(FaceIndexEntity(1, 2, FaceModelContract.VERSION)), setOf(1L)))
    }
}

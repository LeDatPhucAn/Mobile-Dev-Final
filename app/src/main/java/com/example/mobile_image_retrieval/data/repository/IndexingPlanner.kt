package com.example.mobile_image_retrieval.data.repository

import com.example.mobile_image_retrieval.data.db.MediaIndexState
import com.example.mobile_image_retrieval.domain.model.MediaItem

data class IndexingPlan(
    val toEmbed: List<MediaItem>,
    val deletedIds: List<Long>,
    val unchangedCount: Int,
)

object IndexingPlanner {
    fun create(current: List<MediaItem>, stored: List<MediaIndexState>): IndexingPlan {
        val storedById = stored.associateBy { it.mediaId }
        val currentIds = current.asSequence().map { it.mediaId }.toHashSet()
        val toEmbed = current.filter { media -> storedById[media.mediaId]?.dateModified != media.dateModified }
        val deleted = stored.asSequence().map { it.mediaId }.filterNot(currentIds::contains).toList()
        return IndexingPlan(toEmbed, deleted, current.size - toEmbed.size)
    }
}

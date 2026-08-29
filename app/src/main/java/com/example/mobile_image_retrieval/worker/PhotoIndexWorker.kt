package com.example.mobile_image_retrieval.worker

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.mobile_image_retrieval.PhotoSearchApplication
import com.example.mobile_image_retrieval.ai.ModelUnavailableException
import com.example.mobile_image_retrieval.ai.VectorMath
import com.example.mobile_image_retrieval.data.db.EmbeddingCodec
import com.example.mobile_image_retrieval.data.db.MediaEmbeddingEntity
import com.example.mobile_image_retrieval.data.db.IndexingStateEntity
import com.example.mobile_image_retrieval.data.repository.IndexingPlanner

class PhotoIndexWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PhotoSearchApplication).container
        return try {
            val media = container.mediaStoreRepository.queryImages() // newest first
            val dao = container.database.mediaEmbeddingDao()
            val stateDao = container.database.indexingStateDao()
            val plan = IndexingPlanner.create(media, dao.indexStates())
            plan.deletedIds.chunked(500).forEach { dao.deleteIds(it) }
            var completed = plan.unchangedCount
            var failed = 0
            stateDao.upsert(indexState(completed, media.size, failed, "RUNNING"))
            setProgress(progress(completed, media.size))
            for (item in plan.toEmbed) {
                if (isStopped) return Result.retry()
                val totalStarted = SystemClock.elapsedRealtime()
                var bitmap: android.graphics.Bitmap? = null
                try {
                    val thumbnailStarted = SystemClock.elapsedRealtime()
                    bitmap = container.mediaStoreRepository.loadEmbeddingThumbnail(item)
                    val thumbnailMs = SystemClock.elapsedRealtime() - thumbnailStarted
                    val embedding = container.imageEmbeddingModel.embed(bitmap)
                    require(embedding.size == 512) { "Image encoder returned ${embedding.size} dimensions" }
                    VectorMath.l2NormalizeInPlace(embedding)
                    val databaseStarted = SystemClock.elapsedRealtime()
                    dao.upsert(
                        MediaEmbeddingEntity(
                            item.mediaId, item.uri, item.mediaType, item.displayName, item.dateTaken,
                            item.dateAdded, item.dateModified, item.width, item.height, item.mimeType,
                            item.bucketId, item.bucketName, EmbeddingCodec.encode(embedding),
                            embedding.size, System.currentTimeMillis(),
                        ),
                    )
                    val databaseMs = SystemClock.elapsedRealtime() - databaseStarted
                    Log.d(TAG, "media=${item.mediaId} thumbnail=${thumbnailMs}ms database=${databaseMs}ms total=${SystemClock.elapsedRealtime() - totalStarted}ms")
                } catch (error: ModelUnavailableException) {
                    stateDao.upsert(indexState(completed, media.size, failed, "UNAVAILABLE", error.message))
                    return Result.failure(Data.Builder().putString(KEY_ERROR, error.message).build())
                } catch (error: Exception) {
                    failed++
                    Log.w(TAG, "Skipping unreadable media ${item.mediaId}", error)
                } finally {
                    bitmap?.recycle()
                }
                completed++
                setProgress(progress(completed, media.size))
                if (completed % 10 == 0) stateDao.upsert(indexState(completed, media.size, failed, "RUNNING"))
            }
            stateDao.upsert(indexState(completed, media.size, failed, if (failed == 0) "COMPLETE" else "INTERRUPTED"))
            Result.success(progress(media.size, media.size))
        } catch (security: SecurityException) {
            runCatching {
                container.database.indexingStateDao().upsert(indexState(0, 0, 0, "INTERRUPTED", "Photo permission was revoked"))
            }
            Result.failure(Data.Builder().putString(KEY_ERROR, "Photo permission was revoked").build())
        } catch (error: Exception) {
            Log.e(TAG, "Indexing interrupted", error)
            runCatching {
                container.database.indexingStateDao().upsert(indexState(0, 0, 0, "INTERRUPTED", error.message))
            }
            Result.retry()
        }
    }

    private fun progress(indexed: Int, total: Int) = Data.Builder()
        .putInt(KEY_INDEXED, indexed).putInt(KEY_TOTAL, total).build()

    private fun indexState(processed: Int, total: Int, failed: Int, status: String, error: String? = null) =
        IndexingStateEntity(processed = processed, total = total, failed = failed, status = status, error = error, updatedAt = System.currentTimeMillis())

    companion object {
        const val UNIQUE_WORK = "photo-library-index"
        const val KEY_INDEXED = "indexed"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val TAG = "PhotoIndexWorker"
    }
}

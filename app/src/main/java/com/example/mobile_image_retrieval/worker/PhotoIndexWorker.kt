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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.example.mobile_image_retrieval.data.db.EmbeddingCodec
import com.example.mobile_image_retrieval.data.db.MediaEmbeddingEntity
import com.example.mobile_image_retrieval.data.db.IndexingStateEntity
import com.example.mobile_image_retrieval.data.repository.IndexingPlanner
import com.example.mobile_image_retrieval.permissions.PhotoAccess
import com.example.mobile_image_retrieval.permissions.PhotoPermission

class PhotoIndexWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PhotoSearchApplication).container
        return try {
            // Without read permission MediaStore may return an empty/owned-only collection.
            // That is not evidence that the user's indexed photos were deleted.
            if (PhotoPermission.access(applicationContext) == PhotoAccess.DENIED) throw SecurityException("Photo permission was revoked")
            val media = container.mediaStoreRepository.queryImages() // newest first
            val dao = container.database.mediaEmbeddingDao()
            val stateDao = container.database.indexingStateDao()
            val plan = IndexingPlanner.create(media, dao.indexStates())
            plan.deletedIds.chunked(500).forEach { dao.deleteIds(it) }
            val semanticIds = plan.toEmbed.map { it.mediaId }.toHashSet()
            val faceIds = IndexingPlanner.facePending(media, container.database.faceEmbeddingDao().indexStates(), emptySet()).map { it.mediaId }.toSet()
            // Complete each photo across search modes, preserving independent cached stages.
            val textPending = IndexingPlanner.textPending(media, container.database.photoTextDao().indexStates(), emptySet())
            val textIds = textPending.map { it.mediaId }.toHashSet()
            var textCompleted = media.size - textPending.size
            var textFailed = 0
            stateDao.upsert(indexState(textCompleted, media.size, 0, "RUNNING", id = 2))
            val pending = media.filter { it.mediaId in semanticIds || it.mediaId in faceIds || it.mediaId in textIds }
            var completed = media.count { it.mediaId !in semanticIds && it.mediaId !in faceIds }
            var failed = 0
            var modelError: String? = null
            stateDao.upsert(indexState(completed, media.size, failed, "RUNNING"))
            setProgress(progress(completed, media.size))
            for (item in pending) {
                currentCoroutineContext().ensureActive()
                if (isStopped) return Result.retry()
                if (item.mediaId in textIds) {
                    try { container.photoTextRepository.read(item); textCompleted++ }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { textFailed++; Log.w(TAG, "Text indexing failed for ${item.mediaId}", error) }
                    stateDao.upsert(indexState(textCompleted, media.size, textFailed, "RUNNING", id = 2))
                }
                if (modelError != null || (item.mediaId !in semanticIds && item.mediaId !in faceIds)) continue
                val totalStarted = SystemClock.elapsedRealtime()
                var bitmap: android.graphics.Bitmap? = null
                try {
                    if (item.mediaId in semanticIds) {
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
                    }
                    bitmap?.recycle()
                    bitmap = null
                    if (item.mediaId in faceIds) container.faceIndexRepository.index(item)
                } catch (error: ModelUnavailableException) {
                    modelError = error.message ?: "Visual search model is unavailable"
                    stateDao.upsert(indexState(completed, media.size, failed, "UNAVAILABLE", modelError))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failed++
                    Log.w(TAG, "Skipping unreadable media ${item.mediaId}", error)
                } finally {
                    bitmap?.recycle()
                }
                completed++
                setProgress(progress(completed, media.size))
                if (modelError == null) stateDao.upsert(indexState(completed, media.size, failed, "RUNNING"))
            }
            stateDao.upsert(indexState(textCompleted, media.size, textFailed, if (textFailed == 0) "COMPLETE" else "INTERRUPTED", id = 2))
            stateDao.upsert(indexState(completed, media.size, failed,
                if (modelError != null) "UNAVAILABLE" else if (failed == 0) "COMPLETE" else "INTERRUPTED", modelError))
            // A MediaStore notification can arrive while unique work is already running.
            // Reconcile another snapshot so additions during this pass are not left unindexed.
            val latest = container.mediaStoreRepository.queryImages()
            if (latest.associate { it.mediaId to it.dateModified } != media.associate { it.mediaId to it.dateModified }) return Result.retry()
            if (modelError != null) return Result.failure(Data.Builder().putString(KEY_ERROR, modelError).build())
            Result.success(progress(media.size, media.size))
        } catch (cancelled: CancellationException) {
            throw cancelled
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

    private fun indexState(processed: Int, total: Int, failed: Int, status: String, error: String? = null, id: Int = 1) =
        IndexingStateEntity(id = id, processed = processed, total = total, failed = failed, status = status, error = error, updatedAt = System.currentTimeMillis())

    companion object {
        const val UNIQUE_WORK = "photo-library-index"
        const val KEY_INDEXED = "indexed"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val TAG = "PhotoIndexWorker"
    }
}

package com.example.mobile_image_retrieval.worker

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.map

class PhotoIndexScheduler(private val workManager: WorkManager) {
    val workState = workManager.getWorkInfosForUniqueWorkFlow(PhotoIndexWorker.UNIQUE_WORK).map { jobs ->
        jobs.firstOrNull { it.state == WorkInfo.State.RUNNING }?.state
            ?: jobs.firstOrNull { !it.state.isFinished }?.state
    }

    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<PhotoIndexWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .addTag(PhotoIndexWorker.UNIQUE_WORK)
            .build()
        workManager.enqueueUniqueWork(PhotoIndexWorker.UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }
}

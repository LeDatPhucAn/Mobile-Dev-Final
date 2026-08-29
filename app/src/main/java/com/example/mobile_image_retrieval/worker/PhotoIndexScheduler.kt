package com.example.mobile_image_retrieval.worker

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class PhotoIndexScheduler(private val workManager: WorkManager) {
    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<PhotoIndexWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .addTag(PhotoIndexWorker.UNIQUE_WORK)
            .build()
        workManager.enqueueUniqueWork(PhotoIndexWorker.UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }
}

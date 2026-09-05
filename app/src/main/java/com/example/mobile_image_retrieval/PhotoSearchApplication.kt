package com.example.mobile_image_retrieval

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import com.example.mobile_image_retrieval.ai.MobileClipImageEncoder
import com.example.mobile_image_retrieval.ai.MobileClipTextEncoder
import com.example.mobile_image_retrieval.ai.RoomSearchCandidateSource
import com.example.mobile_image_retrieval.ai.SemanticSearchEngine
import com.example.mobile_image_retrieval.ai.OnnxFaceAnalyzer
import com.example.mobile_image_retrieval.ai.RoomFaceCandidateSource
import com.example.mobile_image_retrieval.data.repository.FacePhotoLoader
import com.example.mobile_image_retrieval.data.repository.FaceIndexRepository
import com.example.mobile_image_retrieval.data.db.PhotoSearchDatabase
import com.example.mobile_image_retrieval.data.mediastore.MediaStoreRepository
import com.example.mobile_image_retrieval.data.repository.SearchRepository
import com.example.mobile_image_retrieval.data.repository.ReferencePhotoRepository
import com.example.mobile_image_retrieval.worker.PhotoIndexScheduler

class PhotoSearchApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    val applicationContext: Application = application
    val database: PhotoSearchDatabase = Room.databaseBuilder(
        application,
        PhotoSearchDatabase::class.java,
        "photo-search.db",
    ).addMigrations(PhotoSearchDatabase.MIGRATION_1_2, PhotoSearchDatabase.MIGRATION_2_3).build()
    val mediaStoreRepository = MediaStoreRepository(application.contentResolver)
    val imageEmbeddingModel = MobileClipImageEncoder(application)
    val textEmbeddingModel = MobileClipTextEncoder(application)
    val faceAnalyzer = OnnxFaceAnalyzer(application)
    val facePhotoLoader = FacePhotoLoader(application.contentResolver)
    val faceIndexRepository = FaceIndexRepository(facePhotoLoader, faceAnalyzer, database.faceEmbeddingDao())
    val semanticSearchEngine = SemanticSearchEngine(RoomSearchCandidateSource(database.mediaEmbeddingDao()), RoomFaceCandidateSource(database.faceEmbeddingDao()))
    val referencePhotoRepository = ReferencePhotoRepository(application.contentResolver, imageEmbeddingModel, database.personDao(), faceAnalyzer, facePhotoLoader)
    val searchRepository = SearchRepository(
        textEmbeddingModel, semanticSearchEngine, database.searchHistoryDao(), database.mediaEmbeddingDao(),
        database.personDao(), referencePhotoRepository,
    )
    val indexScheduler = PhotoIndexScheduler(WorkManager.getInstance(application))
}

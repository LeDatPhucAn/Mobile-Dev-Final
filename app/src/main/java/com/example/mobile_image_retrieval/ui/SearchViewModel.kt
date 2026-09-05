package com.example.mobile_image_retrieval.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewModelScope
import com.example.mobile_image_retrieval.AppContainer
import com.example.mobile_image_retrieval.ai.MobileClipAssets
import com.example.mobile_image_retrieval.ai.ModelUnavailableException
import com.example.mobile_image_retrieval.ai.FaceModelContract
import com.example.mobile_image_retrieval.ai.OcrModelContract
import com.example.mobile_image_retrieval.ai.VietnameseText
import com.example.mobile_image_retrieval.domain.model.SearchMode
import com.example.mobile_image_retrieval.data.db.SearchHistoryEntity
import com.example.mobile_image_retrieval.data.db.IndexingStateEntity
import com.example.mobile_image_retrieval.data.db.SavedPerson
import com.example.mobile_image_retrieval.domain.model.Album
import com.example.mobile_image_retrieval.domain.model.IndexingStatus
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.domain.model.SearchResult
import com.example.mobile_image_retrieval.domain.model.UiError
import com.example.mobile_image_retrieval.permissions.PhotoAccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SearchUiState(
    val navigation: SearchNavigation? = null,
    val hasCompletedSearch: Boolean = false,
    val savingCopyMediaId: Long? = null,
    val query: String = "",
    val resultQuery: String = "",
    val selectedImageUri: String? = null,
    val resultImageUri: String? = null,
    val people: List<SavedPerson> = emptyList(),
    val isSavingPerson: Boolean = false,
    val personSaveError: String? = null,
    val personSaveVersion: Long = 0,
    val photoAccess: PhotoAccess = PhotoAccess.DENIED,
    val indexingStatus: IndexingStatus = IndexingStatus.Idle,
    val ocrIndexingStatus: IndexingStatus = IndexingStatus.Idle,
    val isSearching: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val filters: SearchFilters = SearchFilters(),
    val history: List<SearchHistoryEntity> = emptyList(),
    val libraryPhotos: List<MediaItem> = emptyList(),
    val recentPhotos: List<MediaItem> = emptyList(),
    val albums: List<Album> = emptyList(),
    val isLibraryLoading: Boolean = false,
    val libraryError: String? = null,
    val librarySnapshotTimeMillis: Long = 0,
    val libraryTotal: Int = 0,
    val indexedCount: Int = 0,
    val faceIndexedCount: Int = 0,
    val textIndexedCount: Int = 0,
    val error: UiError? = null,
)

enum class SearchNavigation { SEARCHING, RESULTS, BACK }

sealed interface SearchEvent {
    data class CopySaved(val uri: String) : SearchEvent
    data class CopyFailed(val message: String) : SearchEvent
}

class SearchViewModel(private val container: AppContainer, private val savedState: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState(
        query = savedState["query"] ?: "",
        selectedImageUri = savedState["image"],
        filters = savedState["filters"] ?: SearchFilters(),
    ))
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private val mutableEvents = Channel<SearchEvent>(Channel.BUFFERED)
    val events = mutableEvents.receiveAsFlow()
    val libraryChanges = container.mediaStoreRepository.observeChanges()
    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var libraryRefreshJob: Job? = null
    private val modelUnavailable = MobileClipAssets.unavailableReason(container.applicationContext)
    private var workerState: IndexingStateEntity? = null
    private var ocrWorkerState: IndexingStateEntity? = null
    private var textCountLoaded = false
    private var imageCountLoaded = false
    private var faceCountLoaded = false
    private var workState: androidx.work.WorkInfo.State? = null

    init {
        viewModelScope.launch {
            container.indexScheduler.workState.collectLatest { status ->
                workState = status
                mutableState.update { current -> current.copy(
                    indexingStatus = indexingStatus(current.indexedCount, current.libraryTotal),
                    ocrIndexingStatus = ocrStatus(current.textIndexedCount, current.libraryTotal),
                ) }
            }
        }
        viewModelScope.launch {
            container.database.photoTextDao().observeIndexedCount(OcrModelContract.VERSION).collectLatest { count ->
                textCountLoaded = true
                mutableState.update { current -> current.copy(textIndexedCount = count, ocrIndexingStatus = ocrStatus(count, current.libraryTotal)) }
            }
        }
        viewModelScope.launch {
            container.database.indexingStateDao().observe(2).collectLatest { indexing ->
                ocrWorkerState = indexing
                mutableState.update { current -> current.copy(ocrIndexingStatus = ocrStatus(current.textIndexedCount, current.libraryTotal)) }
            }
        }
        viewModelScope.launch {
            container.database.faceEmbeddingDao().observeIndexedCount(FaceModelContract.VERSION).collectLatest { count ->
                faceCountLoaded = true
                mutableState.update { current -> current.copy(faceIndexedCount = count, indexingStatus = indexingStatus(current.indexedCount, current.libraryTotal, count)) }
            }
        }
        viewModelScope.launch {
            container.referencePhotoRepository.people.collectLatest { people -> mutableState.update { it.copy(people = people) } }
        }
        viewModelScope.launch {
            container.searchRepository.history.collectLatest { history -> mutableState.update { it.copy(history = history) } }
        }
        viewModelScope.launch {
            container.database.indexingStateDao().observe().collectLatest { indexing ->
                workerState = indexing
                mutableState.update { current -> current.copy(indexingStatus = indexingStatus(current.indexedCount, current.libraryTotal)) }
            }
        }
        viewModelScope.launch {
            container.database.mediaEmbeddingDao().observeCount().collectLatest { count ->
                imageCountLoaded = true
                mutableState.update { current ->
                    current.copy(indexedCount = count, indexingStatus = indexingStatus(count, current.libraryTotal))
                }
            }
        }
    }

    private fun saveDraft() {
        savedState["query"] = state.value.query
        savedState["image"] = state.value.selectedImageUri
        savedState["filters"] = state.value.filters
    }

    fun consumeNavigation() = mutableState.update { it.copy(navigation = null) }
    fun updateQuery(query: String) {
        mutableState.update { it.copy(query = query) }
        saveDraft()
    }
    fun selectSearchImage(uri: String?) = mutableState.update {
        it.copy(selectedImageUri = uri, filters = if (uri != null && it.filters.searchMode == SearchMode.OCR) it.filters.copy(searchMode = SearchMode.NORMAL) else it.filters)
    }.also { saveDraft() }

    fun updateSearchMode(mode: SearchMode) = mutableState.update {
        it.copy(filters = it.filters.copy(searchMode = mode))
    }.also { saveDraft() }

    suspend fun readPhotoText(item: MediaItem) = container.photoTextRepository.read(item)

    fun savePhotoCopy(item: MediaItem) {
        if (state.value.savingCopyMediaId != null) return
        mutableState.update { it.copy(savingCopyMediaId = item.mediaId) }
        viewModelScope.launch {
            try {
                val uri = container.photoCopyRepository.saveCopy(item)
                refreshLibrary().join()
                mutableEvents.send(SearchEvent.CopySaved(uri.toString()))
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableEvents.send(SearchEvent.CopyFailed(failure.message ?: "Could not save the copy. Check available storage."))
            } finally {
                mutableState.update { it.copy(savingCopyMediaId = null) }
            }
        }
    }

    fun savePerson(name: String, uri: String, existingId: Long? = null) {
        if (state.value.isSavingPerson) return
        mutableState.update { it.copy(isSavingPerson = true, personSaveError = null) }
        viewModelScope.launch {
            try {
                container.referencePhotoRepository.savePerson(name, uri, existingId)
                mutableState.update { it.copy(isSavingPerson = false, personSaveVersion = it.personSaveVersion + 1) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                mutableState.update { it.copy(isSavingPerson = false) }
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(isSavingPerson = false, personSaveError = error.message ?: "Could not save this person.") }
            }
        }
    }

    fun clearPersonSaveError() = mutableState.update { it.copy(personSaveError = null) }

    fun removePerson(id: Long) = viewModelScope.launch {
        try {
            container.referencePhotoRepository.removePerson(id)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableState.update { it.copy(error = UiError.Storage(error.message ?: "Could not remove this person.")) }
        }
    }

    fun updatePhotoAccess(access: PhotoAccess) {
        mutableState.update { it.copy(photoAccess = access, error = null) }
        if (access != PhotoAccess.DENIED) {
            refreshLibrary()
        } else {
            cancelSearch()
            libraryRefreshJob?.cancel()
            mutableState.update {
                it.copy(
                    libraryPhotos = emptyList(), recentPhotos = emptyList(), albums = emptyList(),
                    libraryTotal = 0, isLibraryLoading = false, libraryError = null,
                    results = emptyList(),
                    error = UiError.Permission("Photo access is required to search your library."),
                )
            }
        }
    }

    fun refreshLibrary(): Job {
        libraryRefreshJob?.cancel()
        return viewModelScope.launch {
            if (state.value.photoAccess == PhotoAccess.DENIED) return@launch
            mutableState.update { it.copy(isLibraryLoading = true, libraryError = null) }
            try {
                val photos = container.mediaStoreRepository.queryImages()
                val snapshotTimeMillis = System.currentTimeMillis()
                val albums = withContext(Dispatchers.Default) { container.mediaStoreRepository.albums(photos, snapshotTimeMillis) }
                val accessible = photos.associateBy { it.mediaId }
                mutableState.update { current ->
                    current.copy(
                        libraryPhotos = photos,
                        recentPhotos = photos.take(12),
                        albums = albums,
                        librarySnapshotTimeMillis = snapshotTimeMillis,
                        libraryTotal = photos.size,
                        results = current.results.filter { accessible[it.media.mediaId]?.dateModified == it.media.dateModified },
                        isLibraryLoading = false,
                        libraryError = null,
                        indexingStatus = indexingStatus(current.indexedCount, photos.size),
                        ocrIndexingStatus = ocrStatus(current.textIndexedCount, photos.size),
                        error = null,
                    )
                }
                container.indexScheduler.enqueue()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                updatePhotoAccess(PhotoAccess.DENIED)
            } catch (error: Exception) {
                Log.e(TAG, "Could not read the photo library", error)
                val message = error.message ?: "Could not read the photo library."
                mutableState.update {
                    it.copy(isLibraryLoading = false, libraryError = message, error = UiError.Storage(message))
                }
            }
        }.also { libraryRefreshJob = it }
    }

    fun submitSearch(
        query: String = state.value.query,
        filters: SearchFilters = state.value.filters,
        imageUri: String? = if (filters.searchMode == SearchMode.OCR) null else state.value.selectedImageUri,
    ) {
        val clean = VietnameseText.normalize(query.trim())
        if (clean.isEmpty() && imageUri == null) return
        if (state.value.photoAccess == PhotoAccess.DENIED) {
            mutableState.update { it.copy(error = UiError.Permission("Photo access is required to search your library.")) }
            return
        }
        if (modelUnavailable != null && filters.searchMode != SearchMode.OCR) {
            mutableState.update { it.copy(error = UiError.ModelUnavailable(modelUnavailable)) }
            return
        }
        searchJob?.cancel()
        val generation = ++searchGeneration
        mutableState.update { it.copy(query = clean, resultQuery = clean, selectedImageUri = imageUri, resultImageUri = imageUri, filters = filters, isSearching = true, error = null, navigation = SearchNavigation.SEARCHING) }
        saveDraft()
        searchJob = viewModelScope.launch {
            try {
                libraryRefreshJob?.join()
                if (state.value.libraryError != null && state.value.libraryPhotos.isEmpty()) {
                    failSearch("The photo library could not be loaded. Refresh it and try again.")
                    return@launch
                }
                val execution = container.searchRepository.search(clean, filters, imageUri = imageUri)
                if (generation != searchGeneration) return@launch
                Log.d(TAG, "embedding=${execution.queryEmbeddingMillis}ms scan=${execution.vectorScanMillis}ms total=${execution.totalMillis}ms")
                val accessible = state.value.libraryPhotos.associateBy { it.mediaId }
                val results = execution.results.filter { accessible[it.media.mediaId]?.dateModified == it.media.dateModified }
                mutableState.update { it.copy(results = results, isSearching = false, hasCompletedSearch = true, navigation = SearchNavigation.RESULTS) }
                if (imageUri == null) try {
                    container.searchRepository.saveHistory(clean, results.firstOrNull()?.media?.uri, filters.searchMode)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) { Log.w(TAG, "Could not save search history", error) }
            } catch (_: kotlinx.coroutines.CancellationException) {
                if (generation == searchGeneration) mutableState.update { it.copy(isSearching = false) }
            } catch (error: ModelUnavailableException) {
                if (generation == searchGeneration) failSearch(error.message ?: "MobileCLIP2-S0 is unavailable")
            } catch (error: Exception) {
                if (generation == searchGeneration) failSearch(error.message ?: "Search failed")
            }
        }
    }

    fun cancelSearch() {
        searchGeneration++
        searchJob?.cancel()
        mutableState.update { it.copy(isSearching = false, navigation = null) }
    }

    fun applyFilters(filters: SearchFilters, rerun: Boolean = true) {
        mutableState.update { it.copy(filters = filters) }
        saveDraft()
        if (rerun && (state.value.resultQuery.isNotBlank() || state.value.resultImageUri != null)) {
            submitSearch(state.value.resultQuery, filters, if (filters.searchMode == SearchMode.OCR) null else state.value.resultImageUri)
        }
    }

    fun clearHistory() = viewModelScope.launch {
        try { container.searchRepository.clearHistory() }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { mutableState.update { it.copy(error = UiError.Storage("Could not clear search history. Please try again.")) } }
    }
    fun clearError() = mutableState.update { it.copy(error = null) }
    fun removeIndexedMedia(mediaId: Long) = viewModelScope.launch {
        try {
            container.database.mediaEmbeddingDao().deleteIds(listOf(mediaId))
            mutableState.update { current ->
                current.copy(results = current.results.filterNot { it.media.mediaId == mediaId })
            }
            refreshLibrary()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableState.update {
                it.copy(error = UiError.Storage(error.message ?: "Could not update the photo index."))
            }
        }
    }

    private fun indexingStatus(indexed: Int, total: Int, faces: Int = state.value.faceIndexedCount): IndexingStatus = when {
        !imageCountLoaded || !faceCountLoaded -> IndexingStatus.Idle
        modelUnavailable != null -> IndexingStatus.Unavailable(modelUnavailable)
        workerState?.status == "UNAVAILABLE" -> IndexingStatus.Unavailable(workerState?.error ?: "Model unavailable")
        total == 0 || minOf(indexed, faces) >= total -> IndexingStatus.Idle
        workState == androidx.work.WorkInfo.State.ENQUEUED || workState == androidx.work.WorkInfo.State.BLOCKED -> IndexingStatus.Waiting(minOf(indexed, faces), total)
        workState == androidx.work.WorkInfo.State.RUNNING -> IndexingStatus.Running(minOf(indexed, faces), total)
        workerState?.status == "INTERRUPTED" && workerState?.total == total -> IndexingStatus.Interrupted(minOf(indexed, faces), total)
        else -> IndexingStatus.Waiting(minOf(indexed, faces), total)
    }

    private fun ocrStatus(indexed: Int, total: Int): IndexingStatus = when {
        !textCountLoaded || total == 0 || indexed >= total -> IndexingStatus.Idle
        workState == androidx.work.WorkInfo.State.ENQUEUED || workState == androidx.work.WorkInfo.State.BLOCKED -> IndexingStatus.Waiting(indexed, total)
        workState == androidx.work.WorkInfo.State.RUNNING -> IndexingStatus.Running(indexed, total)
        ocrWorkerState?.status == "INTERRUPTED" && ocrWorkerState?.total == total -> IndexingStatus.Interrupted(indexed, total)
        else -> IndexingStatus.Waiting(indexed, total)
    }

    private suspend fun failSearch(message: String) {
        mutableState.update { it.copy(isSearching = false, error = UiError.Search(message), navigation = SearchNavigation.BACK) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = SearchViewModel(container, extras.createSavedStateHandle()) as T
    }

    companion object { private const val TAG = "SemanticSearch" }
}

package com.example.mobile_image_retrieval.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mobile_image_retrieval.AppContainer
import com.example.mobile_image_retrieval.ai.MobileClipAssets
import com.example.mobile_image_retrieval.ai.ModelUnavailableException
import com.example.mobile_image_retrieval.data.db.SearchHistoryEntity
import com.example.mobile_image_retrieval.data.db.IndexingStateEntity
import com.example.mobile_image_retrieval.domain.model.Album
import com.example.mobile_image_retrieval.domain.model.IndexingStatus
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.domain.model.SearchResult
import com.example.mobile_image_retrieval.domain.model.UiError
import com.example.mobile_image_retrieval.permissions.PhotoAccess
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val resultQuery: String = "",
    val photoAccess: PhotoAccess = PhotoAccess.DENIED,
    val indexingStatus: IndexingStatus = IndexingStatus.Idle,
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
    val error: UiError? = null,
)

sealed interface SearchEvent {
    data object Searching : SearchEvent
    data object ResultsReady : SearchEvent
    data class Failed(val message: String) : SearchEvent
}

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private val mutableEvents = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 2)
    val events = mutableEvents.asSharedFlow()
    val libraryChanges = container.mediaStoreRepository.observeChanges()
    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var libraryRefreshJob: Job? = null
    private val modelUnavailable = MobileClipAssets.unavailableReason(container.applicationContext)
    private var workerState: IndexingStateEntity? = null

    init {
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
                mutableState.update { current ->
                    current.copy(indexedCount = count, indexingStatus = indexingStatus(count, current.libraryTotal))
                }
            }
        }
    }

    fun updateQuery(query: String) = mutableState.update { it.copy(query = query) }

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
                val albums = container.mediaStoreRepository.albums(photos, snapshotTimeMillis)
                mutableState.update { current ->
                    current.copy(
                        libraryPhotos = photos,
                        recentPhotos = photos.take(12),
                        albums = albums,
                        librarySnapshotTimeMillis = snapshotTimeMillis,
                        libraryTotal = photos.size,
                        isLibraryLoading = false,
                        libraryError = null,
                        indexingStatus = indexingStatus(current.indexedCount, photos.size),
                        error = null,
                    )
                }
                if (modelUnavailable == null) container.indexScheduler.enqueue()
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

    fun submitSearch(query: String = state.value.query, filters: SearchFilters = state.value.filters) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        if (modelUnavailable != null) {
            mutableState.update { it.copy(error = UiError.ModelUnavailable(modelUnavailable)) }
            mutableEvents.tryEmit(SearchEvent.Failed(modelUnavailable))
            return
        }
        searchJob?.cancel()
        val generation = ++searchGeneration
        mutableState.update { it.copy(query = clean, resultQuery = clean, filters = filters, isSearching = true, error = null) }
        mutableEvents.tryEmit(SearchEvent.Searching)
        searchJob = viewModelScope.launch {
            try {
                val execution = container.searchRepository.search(clean, filters)
                if (generation != searchGeneration) return@launch
                Log.d(TAG, "query='$clean' text=${execution.textEmbeddingMillis}ms scan=${execution.vectorScanMillis}ms total=${execution.totalMillis}ms")
                mutableState.update { it.copy(results = execution.results, isSearching = false) }
                container.searchRepository.saveHistory(clean, execution.results.firstOrNull()?.media?.uri)
                if (generation != searchGeneration) return@launch
                mutableEvents.emit(SearchEvent.ResultsReady)
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
        mutableState.update { it.copy(isSearching = false) }
    }

    fun applyFilters(filters: SearchFilters) {
        mutableState.update { it.copy(filters = filters) }
        if (state.value.resultQuery.isNotBlank()) submitSearch(state.value.resultQuery, filters)
    }

    fun clearHistory() = viewModelScope.launch { container.searchRepository.clearHistory() }
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

    private fun indexingStatus(indexed: Int, total: Int): IndexingStatus = when {
        modelUnavailable != null -> IndexingStatus.Unavailable(modelUnavailable)
        workerState?.status == "UNAVAILABLE" -> IndexingStatus.Unavailable(workerState?.error ?: "Model unavailable")
        workerState?.status == "INTERRUPTED" && workerState?.total == total -> IndexingStatus.Interrupted(indexed, total)
        total == 0 || indexed >= total -> IndexingStatus.Idle
        else -> IndexingStatus.Running(indexed, total)
    }

    private suspend fun failSearch(message: String) {
        mutableState.update { it.copy(isSearching = false, error = UiError.Search(message)) }
        mutableEvents.emit(SearchEvent.Failed(message))
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(container) as T
    }

    companion object { private const val TAG = "SemanticSearch" }
}

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
    val recentPhotos: List<MediaItem> = emptyList(),
    val albums: List<Album> = emptyList(),
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
    val state: StateFlow<SearchUiState> = mutableState
    private val mutableEvents = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 2)
    val events = mutableEvents.asSharedFlow()
    private var searchJob: Job? = null
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
        if (access != PhotoAccess.DENIED) refreshLibrary() else mutableState.update {
            it.copy(error = UiError.Permission("Photo access is required to search your library."))
        }
    }

    fun refreshLibrary() = viewModelScope.launch {
        try {
            val photos = container.mediaStoreRepository.queryImages()
            val albums = container.mediaStoreRepository.albums(photos)
            mutableState.update { current ->
                current.copy(
                    recentPhotos = photos.take(12), albums = albums, libraryTotal = photos.size,
                    indexingStatus = indexingStatus(current.indexedCount, photos.size), error = null,
                )
            }
            if (modelUnavailable == null) container.indexScheduler.enqueue()
        } catch (error: SecurityException) {
            mutableState.update { it.copy(error = UiError.Permission("Photo access was revoked.")) }
        } catch (error: Exception) {
            mutableState.update { it.copy(error = UiError.Storage(error.message ?: "Could not read the photo library.")) }
        }
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
        mutableState.update { it.copy(query = clean, resultQuery = clean, filters = filters, isSearching = true, error = null) }
        mutableEvents.tryEmit(SearchEvent.Searching)
        searchJob = viewModelScope.launch {
            try {
                val execution = container.searchRepository.search(clean, filters)
                Log.d(TAG, "query='$clean' text=${execution.textEmbeddingMillis}ms scan=${execution.vectorScanMillis}ms total=${execution.totalMillis}ms")
                mutableState.update { it.copy(results = execution.results, isSearching = false) }
                container.searchRepository.saveHistory(clean, execution.results.firstOrNull()?.media?.uri)
                mutableEvents.emit(SearchEvent.ResultsReady)
            } catch (_: kotlinx.coroutines.CancellationException) {
                mutableState.update { it.copy(isSearching = false) }
            } catch (error: ModelUnavailableException) {
                failSearch(error.message ?: "MobileCLIP2-S0 is unavailable")
            } catch (error: Exception) {
                failSearch(error.message ?: "Search failed")
            }
        }
    }

    fun cancelSearch() {
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
        container.database.mediaEmbeddingDao().deleteIds(listOf(mediaId))
        mutableState.update { it.copy(results = it.results.filterNot { result -> result.media.mediaId == mediaId }) }
        refreshLibrary()
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

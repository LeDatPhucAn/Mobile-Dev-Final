package com.example.mobile_image_retrieval

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mobile_image_retrieval.domain.model.AlbumCatalog
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.domain.model.SearchMode
import com.example.mobile_image_retrieval.ui.screens.PhotoTextScreen
import com.example.mobile_image_retrieval.permissions.PhotoPermission
import com.example.mobile_image_retrieval.permissions.PhotoAccess
import com.example.mobile_image_retrieval.ui.SearchEvent
import com.example.mobile_image_retrieval.ui.SearchViewModel
import com.example.mobile_image_retrieval.ui.screens.AlbumPhotosScreen
import com.example.mobile_image_retrieval.ui.screens.AlbumsScreen
import com.example.mobile_image_retrieval.ui.screens.FiltersScreen
import com.example.mobile_image_retrieval.ui.screens.PhotoViewerScreen
import com.example.mobile_image_retrieval.ui.screens.ResultsScreen
import com.example.mobile_image_retrieval.ui.screens.SearchHomeScreen
import com.example.mobile_image_retrieval.ui.screens.SearchingScreen
import com.example.mobile_image_retrieval.ui.screens.PeopleScreen
import com.example.mobile_image_retrieval.ui.theme.PhotoSearchTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PhotoSearchApplication).container
        setContent {
            PhotoSearchTheme {
                val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(container))
                PhotoSearchApp(viewModel)
            }
        }
    }
}

private object Route {
    const val HOME = "home"
    const val SEARCHING = "searching"
    const val RESULTS = "results"
    const val FILTERS = "filters"
    const val ALBUMS = "albums"
    const val PEOPLE = "people"
    const val PHOTO_TEXT = "text/{mediaId}"
    fun photoText(mediaId: Long) = "text/$mediaId"
    const val ALBUM = "album/{albumId}"
    const val RESULT_VIEWER = "viewer/results/{mediaId}"
    const val LIBRARY_VIEWER = "viewer/library/{mediaId}"
    const val ALBUM_VIEWER = "viewer/album/{albumId}/{mediaId}"
    fun album(albumId: String) = "album/${Uri.encode(albumId)}"
    fun resultViewer(mediaId: Long) = "viewer/results/$mediaId"
    fun libraryViewer(mediaId: Long) = "viewer/library/$mediaId"
    fun albumViewer(albumId: String, mediaId: Long) = "viewer/album/${Uri.encode(albumId)}/$mediaId"
}

@Composable
private fun PhotoSearchApp(viewModel: SearchViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.updatePhotoAccess(PhotoPermission.access(context))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.updatePhotoAccess(PhotoPermission.access(context))
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycleOwner, state.photoAccess) {
        if (state.photoAccess != PhotoAccess.DENIED) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.libraryChanges.collect { viewModel.refreshLibrary().join() }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SearchEvent.Searching -> navController.navigate(Route.SEARCHING)
                SearchEvent.ResultsReady -> navController.navigate(Route.RESULTS) { popUpTo(Route.SEARCHING) { inclusive = true } }
                is SearchEvent.Failed -> {
                    if (navController.currentDestination?.route == Route.SEARCHING) navController.popBackStack()
                    snackbar.showSnackbar(event.message)
                }
            }
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            val message = when (error) {
                is com.example.mobile_image_retrieval.domain.model.UiError.ModelUnavailable -> error.message
                is com.example.mobile_image_retrieval.domain.model.UiError.Permission -> error.message
                is com.example.mobile_image_retrieval.domain.model.UiError.Search -> error.message
                is com.example.mobile_image_retrieval.domain.model.UiError.Storage -> error.message
            }
            snackbar.showSnackbar(message)
            viewModel.clearError()
        }
    }

    var pendingDelete by remember { mutableStateOf<MediaItem?>(null) }
    val deleteConfirmation = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val item = pendingDelete
        if (result.resultCode == Activity.RESULT_OK && item != null) {
            scope.launch {
                val deletionSucceeded = if (Build.VERSION.SDK_INT == 29) {
                    withContext(Dispatchers.IO) {
                        runCatching { context.contentResolver.delete(item.uri.toUri(), null, null) }.isSuccess
                    }
                } else {
                    true
                }
                if (deletionSucceeded) {
                    viewModel.removeIndexedMedia(item.mediaId)
                    navController.popBackStack()
                } else {
                    snackbar.showSnackbar("This photo could not be deleted.")
                }
            }
        }
        pendingDelete = null
    }
    fun requestDelete(item: MediaItem) {
        pendingDelete = item
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri.toUri()))
                deleteConfirmation.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            }.onFailure {
                pendingDelete = null
                scope.launch { snackbar.showSnackbar("This photo could not be deleted.") }
            }
        } else {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { context.contentResolver.delete(item.uri.toUri(), null, null) }
                    viewModel.removeIndexedMedia(item.mediaId)
                    navController.popBackStack()
                } catch (recoverable: RecoverableSecurityException) {
                    runCatching {
                        deleteConfirmation.launch(IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build())
                    }.onFailure {
                        pendingDelete = null
                        snackbar.showSnackbar("This photo could not be deleted.")
                    }
                } catch (_: SecurityException) {
                    pendingDelete = null
                    snackbar.showSnackbar("This photo could not be deleted.")
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    pendingDelete = null
                    snackbar.showSnackbar("This photo could not be deleted.")
                }
            }
        }
    }
    fun share(item: MediaItem) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = item.mimeType ?: "image/*"
            putExtra(Intent.EXTRA_STREAM, item.uri.toUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(shareIntent, "Share photo"))
        }.onFailure {
            scope.launch { snackbar.showSnackbar("No app is available to share this photo.") }
        }
    }

    val showBottomBar = currentRoute == Route.HOME || currentRoute == Route.ALBUMS || currentRoute == Route.PEOPLE
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Route.HOME,
                    onClick = { navController.navigate(Route.HOME) { popUpTo(navController.graph.findStartDestination().id); launchSingleTop = true } },
                    icon = { Icon(Icons.Default.Search, null) }, label = { Text("Search") },
                )
                NavigationBarItem(
                    selected = currentRoute == Route.ALBUMS,
                    onClick = { navController.navigate(Route.ALBUMS) { popUpTo(navController.graph.findStartDestination().id); launchSingleTop = true } },
                    icon = { Icon(Icons.Default.Album, null) }, label = { Text("Albums") },
                )
                NavigationBarItem(
                    selected = currentRoute == Route.PEOPLE,
                    onClick = { navController.navigate(Route.PEOPLE) { popUpTo(navController.graph.findStartDestination().id); launchSingleTop = true } },
                    icon = { Icon(Icons.Default.People, null) }, label = { Text("People") },
                )
            }
        },
    ) { outerPadding ->
        NavHost(navController, startDestination = Route.HOME, modifier = Modifier.padding(outerPadding)) {
            composable(Route.HOME) {
                SearchHomeScreen(
                    state, viewModel::updateQuery, { viewModel.submitSearch(it) },
                    { permissionLauncher.launch(PhotoPermission.requestedPermissions()) },
                    viewModel::clearHistory, { navController.navigate(Route.FILTERS) },
                    { navController.navigate(Route.libraryViewer(it)) },
                    onSelectImage = viewModel::selectSearchImage,
                    onOpenPeople = { navController.navigate(Route.PEOPLE) { launchSingleTop = true } },
                    onHistorySearch = { query, mode -> viewModel.submitSearch(query, state.filters.copy(searchMode = mode), imageUri = null) },
                    onModeChanged = viewModel::updateSearchMode,
                    onRefresh = { viewModel.refreshLibrary() },
                )
            }
            composable(Route.SEARCHING) {
                androidx.activity.compose.BackHandler { viewModel.cancelSearch(); navController.popBackStack() }
                SearchingScreen(state.resultQuery.ifBlank { "Similar photos" }, { viewModel.cancelSearch(); navController.popBackStack() }, { viewModel.cancelSearch(); navController.popBackStack() })
            }
            composable(Route.RESULTS) {
                ResultsScreen(
                    state, { navController.popBackStack(Route.HOME, false) }, { navController.navigate(Route.FILTERS) },
                    { viewModel.applyFilters(SearchFilters(searchMode = state.filters.searchMode)) }, { navController.navigate(Route.resultViewer(it)) },
                )
            }
            composable(Route.FILTERS) {
                FiltersScreen(state.filters, { navController.popBackStack() }) { filters ->
                    navController.popBackStack()
                    viewModel.applyFilters(filters)
                }
            }
            composable(Route.ALBUMS) {
                LaunchedEffect(Unit) { viewModel.updatePhotoAccess(PhotoPermission.access(context)) }
                AlbumsScreen(
                    albums = state.albums,
                    onAlbum = { navController.navigate(Route.album(it)) },
                    photoAccess = state.photoAccess,
                    isLoading = state.isLibraryLoading,
                    error = state.libraryError,
                    onRefresh = { viewModel.updatePhotoAccess(PhotoPermission.access(context)) },
                    onRequestPermission = { permissionLauncher.launch(PhotoPermission.requestedPermissions()) },
                )
            }
            composable(Route.PEOPLE) {
                PeopleScreen(
                    state = state,
                    onSave = viewModel::savePerson,
                    onRemove = { viewModel.removePerson(it) },
                    onSearch = { viewModel.submitSearch(it, state.filters.copy(searchMode = SearchMode.NORMAL), imageUri = null) },
                    onClearSaveError = viewModel::clearPersonSaveError,
                    onRefresh = { viewModel.refreshLibrary() },
                )
            }
            composable(Route.PHOTO_TEXT, arguments = listOf(navArgument("mediaId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("mediaId")
                PhotoTextScreen(state.libraryPhotos.firstOrNull { it.mediaId == id } ?: state.results.firstOrNull { it.media.mediaId == id }?.media,
                    { navController.popBackStack() }, viewModel::readPhotoText)
            }
            composable(Route.ALBUM, arguments = listOf(navArgument("albumId") { type = NavType.StringType })) { entry ->
                val albumId = Uri.decode(entry.arguments?.getString("albumId").orEmpty())
                val album = state.albums.firstOrNull { it.id == albumId }
                AlbumPhotosScreen(
                    album = album,
                    photos = AlbumCatalog.photosFor(
                        albumId,
                        state.libraryPhotos,
                        state.librarySnapshotTimeMillis,
                    ),
                    onBack = { navController.popBackStack() },
                    onPhoto = { navController.navigate(Route.albumViewer(albumId, it)) },
                )
            }
            composable(Route.RESULT_VIEWER, arguments = listOf(navArgument("mediaId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("mediaId")
                PhotoViewerScreen(
                    photos = state.results.map { it.media },
                    initialMediaId = id,
                    onBack = { navController.popBackStack() },
                    onShare = ::share,
                    onDelete = ::requestDelete,
                    scoreFor = { mediaId -> if (state.filters.searchMode == SearchMode.OCR) null else state.results.firstOrNull { it.media.mediaId == mediaId }?.rawSimilarity },
                    onFindSimilar = { viewModel.submitSearch("", state.filters.copy(searchMode = SearchMode.NORMAL), imageUri = it.uri) },
                    onReadText = { navController.navigate(Route.photoText(it.mediaId)) },
                )
            }
            composable(Route.LIBRARY_VIEWER, arguments = listOf(navArgument("mediaId") { type = NavType.LongType })) { entry ->
                PhotoViewerScreen(
                    photos = state.libraryPhotos,
                    initialMediaId = entry.arguments?.getLong("mediaId"),
                    onBack = { navController.popBackStack() },
                    onShare = ::share,
                    onDelete = ::requestDelete,
                    onFindSimilar = { viewModel.submitSearch("", state.filters.copy(searchMode = SearchMode.NORMAL), imageUri = it.uri) },
                    onReadText = { navController.navigate(Route.photoText(it.mediaId)) },
                )
            }
            composable(
                Route.ALBUM_VIEWER,
                arguments = listOf(
                    navArgument("albumId") { type = NavType.StringType },
                    navArgument("mediaId") { type = NavType.LongType },
                ),
            ) { entry ->
                val albumId = Uri.decode(entry.arguments?.getString("albumId").orEmpty())
                PhotoViewerScreen(
                    photos = AlbumCatalog.photosFor(
                        albumId,
                        state.libraryPhotos,
                        state.librarySnapshotTimeMillis,
                    ),
                    initialMediaId = entry.arguments?.getLong("mediaId"),
                    onBack = { navController.popBackStack() },
                    onShare = ::share,
                    onDelete = ::requestDelete,
                    onFindSimilar = { viewModel.submitSearch("", state.filters.copy(searchMode = SearchMode.NORMAL), imageUri = it.uri) },
                    onReadText = { navController.navigate(Route.photoText(it.mediaId)) },
                )
            }
        }
    }
}

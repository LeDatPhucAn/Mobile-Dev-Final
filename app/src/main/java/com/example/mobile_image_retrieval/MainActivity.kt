package com.example.mobile_image_retrieval

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.Intent
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.permissions.PhotoPermission
import com.example.mobile_image_retrieval.ui.SearchEvent
import com.example.mobile_image_retrieval.ui.SearchViewModel
import com.example.mobile_image_retrieval.ui.screens.AlbumsScreen
import com.example.mobile_image_retrieval.ui.screens.FiltersScreen
import com.example.mobile_image_retrieval.ui.screens.PhotoDetailScreen
import com.example.mobile_image_retrieval.ui.screens.ResultsScreen
import com.example.mobile_image_retrieval.ui.screens.SearchHomeScreen
import com.example.mobile_image_retrieval.ui.screens.SearchingScreen
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
    const val DETAIL = "detail/{mediaId}"
    fun detail(mediaId: Long) = "detail/$mediaId"
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
                if (Build.VERSION.SDK_INT == 29) withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.delete(item.uri.toUri(), null, null) }
                }
                viewModel.removeIndexedMedia(item.mediaId)
                navController.popBackStack()
            }
        }
        pendingDelete = null
    }
    fun requestDelete(item: MediaItem) {
        pendingDelete = item
        if (Build.VERSION.SDK_INT >= 30) {
            val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri.toUri()))
            deleteConfirmation.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } else {
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { context.contentResolver.delete(item.uri.toUri(), null, null) }
                    viewModel.removeIndexedMedia(item.mediaId)
                    navController.popBackStack()
                } catch (recoverable: RecoverableSecurityException) {
                    deleteConfirmation.launch(IntentSenderRequest.Builder(recoverable.userAction.actionIntent.intentSender).build())
                }
            }
        }
    }

    val showBottomBar = currentRoute == Route.HOME || currentRoute == Route.ALBUMS
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
            }
        },
    ) { outerPadding ->
        NavHost(navController, startDestination = Route.HOME, modifier = Modifier.padding(outerPadding)) {
            composable(Route.HOME) {
                SearchHomeScreen(
                    state, viewModel::updateQuery, viewModel::submitSearch,
                    { permissionLauncher.launch(PhotoPermission.requestedPermissions()) },
                    viewModel::clearHistory, { navController.navigate(Route.FILTERS) },
                )
            }
            composable(Route.SEARCHING) {
                SearchingScreen(state.resultQuery, { viewModel.cancelSearch(); navController.popBackStack() }, { viewModel.cancelSearch(); navController.popBackStack() })
            }
            composable(Route.RESULTS) {
                ResultsScreen(
                    state, { navController.popBackStack(Route.HOME, false) }, { navController.navigate(Route.FILTERS) },
                    { viewModel.applyFilters(SearchFilters()) }, { navController.navigate(Route.detail(it)) },
                )
            }
            composable(Route.FILTERS) {
                FiltersScreen(state.filters, { navController.popBackStack() }) { filters ->
                    navController.popBackStack()
                    viewModel.applyFilters(filters)
                }
            }
            composable(Route.ALBUMS) { AlbumsScreen(state.albums) }
            composable(Route.DETAIL, arguments = listOf(navArgument("mediaId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("mediaId")
                val result = state.results.firstOrNull { it.media.mediaId == id }
                PhotoDetailScreen(
                    result, { navController.popBackStack() },
                    { item ->
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = item.mimeType ?: "image/*"
                            putExtra(Intent.EXTRA_STREAM, item.uri.toUri())
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(share, "Share photo"))
                    },
                    ::requestDelete,
                )
            }
        }
    }
}

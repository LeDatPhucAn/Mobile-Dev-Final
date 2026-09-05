@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.mobile_image_retrieval.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.mobile_image_retrieval.ai.MatchScoreFormatter
import com.example.mobile_image_retrieval.domain.model.Album
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.MediaType
import com.example.mobile_image_retrieval.permissions.PhotoAccess
import com.example.mobile_image_retrieval.ui.theme.PhotoSearchTheme
import java.time.Instant
import java.time.format.DateTimeFormatter

@Composable
fun PhotoViewerScreen(
    photos: List<MediaItem>,
    initialMediaId: Long?,
    onBack: () -> Unit,
    onShare: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    scoreFor: (Long) -> Float? = { null },
    onFindSimilar: ((MediaItem) -> Unit)? = null,
    onReadText: ((MediaItem) -> Unit)? = null,
    onSaveCopy: ((MediaItem) -> Unit)? = null,
    savingCopyMediaId: Long? = null,
) {
    var selectedMediaId by rememberSaveable(initialMediaId) { mutableStateOf(initialMediaId) }
    val initialPage = remember(photos, selectedMediaId) {
        photos.indexOfFirst { it.mediaId == selectedMediaId }
    }
    if (initialPage < 0) {
        UnavailablePhotoScreen(onBack)
        return
    }

    val pagerState = rememberPagerState(initialPage = initialPage) { photos.size }
    val media = photos.getOrNull(pagerState.currentPage) ?: photos[initialPage]
    LaunchedEffect(media.mediaId) { selectedMediaId = media.mediaId }
    val score = scoreFor(media.mediaId)
    var zoomed by remember(media.mediaId) { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Black,
        contentColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("${pagerState.currentPage + 1} of ${photos.size}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val landscape = maxWidth > maxHeight
            val photoPane: @Composable (Modifier) -> Unit = { modifier ->
                HorizontalPager(
                    state = pagerState,
                    modifier = modifier,
                    userScrollEnabled = !zoomed,
                    key = { photos[it].mediaId },
                ) { page ->
                    val pageMedia = photos[page]
                    ZoomablePhoto(pageMedia.uri, pageMedia.displayName ?: "Photo ${page + 1}") {
                        if (page == pagerState.currentPage) zoomed = it
                    }
                }
            }
            val detailsPane: @Composable (Modifier) -> Unit = { modifier ->
                Surface(modifier = modifier, color = Color(0xFF121212), contentColor = Color.White) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Text(
                            text = media.displayName ?: "Photo",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(formatDate(media.dateTaken ?: media.dateAdded?.times(1000)), color = Color.LightGray)
                        Text("Pinch or double-tap to zoom. Swipe to browse.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        if (score != null) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Match score", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${MatchScoreFormatter.percentage(score)}%",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                "Relevance indicator derived from cosine similarity; not model confidence or probability.",
                                fontSize = 11.sp,
                                color = Color.LightGray,
                            )
                        }
                        if (media.width != null && media.height != null) {
                            Text(
                                "${media.width} × ${media.height}  •  ${media.bucketName ?: "Photo library"}",
                                color = Color.LightGray,
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ViewerActionButton("Share", Icons.Default.Share, Modifier.weight(1f)) { onShare(media) }
                            ViewerActionButton("Delete", Icons.Default.Delete, Modifier.weight(1f)) { onDelete(media) }
                        }
                        onSaveCopy?.let { save ->
                            OutlinedButton(onClick = { save(media) }, enabled = savingCopyMediaId == null,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                                Icon(Icons.Default.SaveAlt, null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (savingCopyMediaId == media.mediaId) "Saving…" else "Save a copy")
                            }
                        }
                        onFindSimilar?.let { search ->
                            TextButton(onClick = { search(media) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Find similar photos")
                            }
                        }
                        onReadText?.let { read ->
                            TextButton(onClick = { read(media) }, modifier = Modifier.fillMaxWidth()) { Text("Read text") }
                        }
                    }
                }
            }
            if (landscape) Row(Modifier.fillMaxSize()) {
                photoPane(Modifier.weight(1f).fillMaxSize())
                detailsPane(Modifier.width(280.dp).fillMaxSize())
            } else Column(Modifier.fillMaxSize()) {
                photoPane(Modifier.weight(1f).fillMaxWidth())
                detailsPane(Modifier.fillMaxWidth().weight(0.85f))
            }
        }
    }
}

@Composable
private fun ViewerActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        border = BorderStroke(1.dp, Color.DarkGray),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun UnavailablePhotoScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Photo") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text("This photo is no longer available")
        }
    }
}

@Composable
fun AlbumsScreen(
    albums: List<Album>,
    onAlbum: (String) -> Unit,
    photoAccess: PhotoAccess = PhotoAccess.FULL,
    isLoading: Boolean = false,
    error: String? = null,
    onRefresh: () -> Unit = {},
    onRequestPermission: () -> Unit = {},
) {
    if (photoAccess == PhotoAccess.DENIED) {
        PermissionScreen(onRequestPermission)
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Albums",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(top = 20.dp, bottom = 18.dp),
            )
            IconButton(onClick = onRefresh, enabled = !isLoading) {
                Icon(Icons.Default.Refresh, "Refresh albums")
            }
        }
        if (photoAccess == PhotoAccess.PARTIAL) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("Only selected photos are accessible.")
                    TextButton(onClick = onRequestPermission) { Text("Manage photo access") }
                }
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRefresh, enabled = !isLoading) { Text("Try again") }
        }
        if (albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                        Text("Loading photos…")
                    } else if (error == null) {
                        Text(if (photoAccess == PhotoAccess.PARTIAL) "No selected photos are available" else "No photos found on this device")
                        Text("Refresh the library or check photo access.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onRefresh) { Text("Refresh") }
                        TextButton(onClick = onRequestPermission) { Text("Manage photo access") }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(albums, key = { it.id }) { album ->
                    ElevatedCard(onClick = { onAlbum(album.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (album.coverUri != null) {
                                PhotoThumbnail(album.coverUri, Modifier.size(68.dp), album.name)
                            } else {
                                Box(
                                    Modifier.size(68.dp).background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(album.name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${album.count} photos",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                )
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumPhotosScreen(
    album: Album?,
    photos: List<MediaItem>,
    onBack: () -> Unit,
    onPhoto: (Long) -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    Text(album?.name ?: "Album", fontWeight = FontWeight.Bold)
                    Text(
                        "${photos.size} photos",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
        )
    }) { padding ->
        if (photos.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No accessible photos in this album")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(photos, key = { it.mediaId }) { photo ->
                    PhotoThumbnail(
                        uri = photo.uri,
                        modifier = Modifier.clickable { onPhoto(photo.mediaId) },
                        contentDescription = photo.displayName,
                    )
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long?): String = epochMillis?.let {
    DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a")
        .format(Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()))
} ?: "Date unavailable"

private val PreviewMedia = MediaItem(
    1,
    "",
    MediaType.IMAGE,
    "Beach.jpg",
    1_715_760_000_000,
    0,
    0,
    4032,
    3024,
    "image/jpeg",
    "camera",
    "Camera",
)

@Preview(showBackground = true)
@Composable
private fun ViewerPreview() = PhotoSearchTheme {
    PhotoViewerScreen(listOf(PreviewMedia), PreviewMedia.mediaId, {}, {}, {}, { .42f })
}

@Preview(showBackground = true)
@Composable
private fun AlbumsPreview() = PhotoSearchTheme {
    AlbumsScreen(listOf(Album("all", "All Photos", 1, null)), {})
}

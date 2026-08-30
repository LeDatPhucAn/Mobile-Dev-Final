@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.mobile_image_retrieval.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.mobile_image_retrieval.data.db.SearchHistoryEntity
import com.example.mobile_image_retrieval.domain.model.Album
import com.example.mobile_image_retrieval.domain.model.IndexingStatus
import com.example.mobile_image_retrieval.domain.model.MediaItem
import com.example.mobile_image_retrieval.domain.model.MediaType
import com.example.mobile_image_retrieval.domain.model.ResultSort
import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.domain.model.SearchResult
import com.example.mobile_image_retrieval.domain.model.TimeRange
import com.example.mobile_image_retrieval.permissions.PhotoAccess
import com.example.mobile_image_retrieval.ui.SearchUiState
import com.example.mobile_image_retrieval.ui.theme.PhotoSearchTheme
import java.time.Instant
import java.time.ZoneOffset

private val Suggestions = listOf("beach with friends", "sunset", "my birthday", "dog playing", "mountain trip", "coffee time")

@Composable
fun SearchHomeScreen(
    state: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onSearch: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenFilters: () -> Unit,
    onPhoto: (Long) -> Unit,
) {
    if (state.photoAccess == PhotoAccess.DENIED) {
        PermissionScreen(onRequestPermission)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = {}) { Icon(Icons.Default.Menu, "Menu") }
                IconButton(onClick = {}) { Icon(Icons.Default.History, "Search history") }
            }
            Text("Photo Search", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text("Search your photos with AI", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.photoAccess == PhotoAccess.PARTIAL) item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                Text("Searching only the photos you selected. You can expand access in system settings.", Modifier.padding(14.dp), fontSize = 13.sp)
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Describe what you're looking for...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { Icon(Icons.Default.AutoAwesome, "Semantic search") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onOpenFilters, label = { Text(timeRangeLabel(state.filters.timeRange) + "  ▾") })
                AssistChip(onClick = onOpenFilters, label = { Text((state.filters.mediaType?.let { if (it == MediaType.IMAGE) "Photos" else "Videos" } ?: "All photos") + "  ▾") })
            }
            Button(
                onClick = { onSearch(state.query) },
                enabled = state.query.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Search") }
        }
        when (val indexing = state.indexingStatus) {
            is IndexingStatus.Running -> item { IndexingCard(indexing) }
            is IndexingStatus.Unavailable -> item {
                Surface(color = Color(0xFFFFF5E6), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Semantic model unavailable", fontWeight = FontWeight.SemiBold)
                        Text(indexing.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            is IndexingStatus.Interrupted -> item {
                Surface(color = Color(0xFFFFF5E6), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Indexing paused", fontWeight = FontWeight.SemiBold)
                        Text("${indexing.indexed} / ${indexing.total} photos indexed. The next background pass will retry remaining photos.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> Unit
        }
        item {
            Text("Try searching", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Suggestions.forEach { suggestion -> AssistChip(onClick = { onSearch(suggestion) }, label = { Text(suggestion) }) }
            }
        }
        if (state.history.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent searches", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    TextButton(onClick = onClearHistory) { Text("Clear") }
                }
            }
            items(state.history, key = { it.id }) { history -> HistoryRow(history) { onSearch(history.query) } }
        }
        if (state.recentPhotos.isNotEmpty()) {
            item { Text("Your library", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    userScrollEnabled = false,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(state.recentPhotos.take(8), key = { it.mediaId }) { photo ->
                        PhotoThumbnail(
                            photo.uri,
                            Modifier.clickable { onPhoto(photo.mediaId) },
                            photo.displayName,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PermissionScreen(onRequestPermission: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Search your own photo library", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Your photos and searches stay on this device. Semantic search does not require photos to be uploaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("Choose photo access") }
            }
        }
    }
}

@Composable
private fun IndexingCard(status: IndexingStatus.Running) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Indexing your library", fontWeight = FontWeight.SemiBold)
            Text("${status.indexed} / ${status.total} photos", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { if (status.total == 0) 0f else status.indexed.toFloat() / status.total },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun HistoryRow(history: SearchHistoryEntity, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(history.query, fontWeight = FontWeight.Medium)
            Text(DateUtils.getRelativeTimeSpanString(history.timestamp).toString(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        history.topResultUri?.let { PhotoThumbnail(it, Modifier.size(52.dp)) }
    }
}

@Composable
fun SearchingScreen(query: String, onBack: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Searching...") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
        Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Finding the best matches", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(34.dp))
            CircularProgressIndicator(Modifier.size(58.dp))
            Spacer(Modifier.height(22.dp))
            Text("Searching your photos", fontWeight = FontWeight.SemiBold)
            Text("“$query”", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 22.dp)) { Text("Cancel") }
        }
    }
}

@Composable
fun ResultsScreen(
    state: SearchUiState,
    onBack: () -> Unit,
    onOpenFilters: () -> Unit,
    onClearFilters: () -> Unit,
    onPhoto: (Long) -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = onOpenFilters) { Icon(Icons.Default.FilterList, "Filters") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Text("“${state.resultQuery}”", fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
            Text("${state.results.size} results", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                AssistChip(onClick = onOpenFilters, label = { Text(timeRangeLabel(state.filters.timeRange) + "  ▾") })
                if (state.filters != SearchFilters()) TextButton(onClick = onClearFilters) { Text("Clear") }
            }
            if (state.results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("No matches in the indexed photos", modifier = Modifier.padding(top = 12.dp))
                        Text("Try a broader phrase or different filters.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                items(state.results, key = { it.media.mediaId }) { result ->
                    Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).clickable { onPhoto(result.media.mediaId) }) {
                        AsyncImage(result.media.uri, result.media.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
    }
}

@Composable
fun FiltersScreen(initial: SearchFilters, onClose: () -> Unit, onApply: (SearchFilters) -> Unit) {
    var filters by remember(initial) { mutableStateOf(initial) }
    var showRangePicker by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filters", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
                actions = { TextButton(onClick = { filters = SearchFilters() }) { Text("Reset") } },
            )
        },
        bottomBar = { Button(onClick = { onApply(filters) }, Modifier.fillMaxWidth().padding(20.dp).height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Apply filters") } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 20.dp)) {
            item { SectionTitle("TIME RANGE") }
            items(TimeRange.entries) { range -> SelectionRow(timeRangeLabel(range), filters.timeRange == range) {
                filters = filters.copy(timeRange = range)
                if (range == TimeRange.CUSTOM) showRangePicker = true
            } }
            item { HorizontalDivider(Modifier.padding(vertical = 14.dp)); SectionTitle("SORT BY") }
            items(ResultSort.entries) { sort -> SelectionRow(sortLabel(sort), filters.sort == sort) { filters = filters.copy(sort = sort) } }
            item { HorizontalDivider(Modifier.padding(vertical = 14.dp)); SectionTitle("MEDIA TYPE") }
            item { SelectionRow("All", filters.mediaType == null) { filters = filters.copy(mediaType = null) } }
            items(MediaType.entries) { type -> SelectionRow(if (type == MediaType.IMAGE) "Photos" else "Videos", filters.mediaType == type) { filters = filters.copy(mediaType = type) } }
            item { Spacer(Modifier.height(90.dp)) }
        }
    }
    if (showRangePicker) CustomRangeDialog(
        onDismiss = { showRangePicker = false },
        onRange = { start, end -> filters = filters.copy(timeRange = TimeRange.CUSTOM, customStartMillis = start, customEndExclusiveMillis = end); showRangePicker = false },
    )
}

@Composable
private fun CustomRangeDialog(onDismiss: () -> Unit, onRange: (Long, Long) -> Unit) {
    val picker = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = picker.selectedStartDateMillis != null && picker.selectedEndDateMillis != null,
                onClick = {
                    val zone = java.time.ZoneId.systemDefault()
                    val startDate = Instant.ofEpochMilli(picker.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                    val endDate = Instant.ofEpochMilli(picker.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1)
                    onRange(startDate.atStartOfDay(zone).toInstant().toEpochMilli(), endDate.atStartOfDay(zone).toInstant().toEpochMilli())
                },
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DateRangePicker(picker, modifier = Modifier.fillMaxHeight(0.75f)) }
}

@Composable private fun SectionTitle(text: String) = Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))

@Composable
private fun SelectionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        RadioButton(selected, onClick)
    }
}

private fun timeRangeLabel(range: TimeRange) = when (range) {
    TimeRange.ANY_TIME -> "Any time"; TimeRange.TODAY -> "Today"; TimeRange.YESTERDAY -> "Yesterday"
    TimeRange.THIS_WEEK -> "This week"; TimeRange.THIS_MONTH -> "This month"; TimeRange.CUSTOM -> "Custom range"
}
private fun sortLabel(sort: ResultSort) = when (sort) { ResultSort.MOST_RELEVANT -> "Most relevant"; ResultSort.NEWEST_FIRST -> "Newest first"; ResultSort.OLDEST_FIRST -> "Oldest first" }
private val PreviewMedia = MediaItem(1, "", MediaType.IMAGE, "Beach.jpg", 1_715_760_000_000, 0, 0, 4032, 3024, "image/jpeg", "camera", "Camera")
private val PreviewState = SearchUiState(photoAccess = PhotoAccess.FULL, query = "me at the beach", indexingStatus = IndexingStatus.Running(2431, 8912), history = listOf(SearchHistoryEntity(1, "sunset", System.currentTimeMillis(), null)), results = listOf(SearchResult(PreviewMedia, .42f)), resultQuery = "me at the beach", albums = listOf(Album("all", "All Photos", 8912, null)))

@Preview(showBackground = true) @Composable private fun HomePreview() = PhotoSearchTheme { SearchHomeScreen(PreviewState, {}, {}, {}, {}, {}, {}) }
@Preview(showBackground = true) @Composable private fun SearchingPreview() = PhotoSearchTheme { SearchingScreen("me at the beach", {}, {}) }
@Preview(showBackground = true) @Composable private fun ResultsPreview() = PhotoSearchTheme { ResultsScreen(PreviewState, {}, {}, {}, {}) }
@Preview(showBackground = true) @Composable private fun FiltersPreview() = PhotoSearchTheme { FiltersScreen(SearchFilters(), {}, {}) }

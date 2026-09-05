@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.mobile_image_retrieval.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.example.mobile_image_retrieval.domain.model.SearchMode
import com.example.mobile_image_retrieval.ai.VietnameseText
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    onSelectImage: (String?) -> Unit = {},
    onOpenPeople: () -> Unit = {},
    onHistorySearch: (String, SearchMode) -> Unit = { query, _ -> onSearch(query) },
    onModeChanged: (SearchMode) -> Unit = {},
    onRefresh: () -> Unit = {},
) {
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onSelectImage(it.toString()) }
    }
    if (state.photoAccess == PhotoAccess.DENIED) {
        PermissionScreen(onRequestPermission)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Photo Search", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
            Text("Search your photos with AI", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.isLibraryLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (state.libraryError != null) item {
            Text(state.libraryError, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRefresh) { Text("Try loading photos again") }
        } else if (!state.isLibraryLoading && state.libraryTotal == 0) item {
            Text("No accessible photos yet. Add photos to this device or update photo access.")
            TextButton(onClick = onRequestPermission) { Text("Manage photo access") }
        }
        if (state.photoAccess == PhotoAccess.PARTIAL) item {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Searching only the photos you selected.", fontSize = 13.sp)
                    TextButton(onClick = onRequestPermission) { Text("Manage photo access") }
                }
            }
        }
        item {
            TabRow(selectedTabIndex = state.filters.searchMode.ordinal) {
                SearchMode.entries.forEach { mode ->
                    Tab(selected = state.filters.searchMode == mode, onClick = { onModeChanged(mode) }, text = { Text(searchModeLabel(mode)) })
                }
            }
            VietnameseTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                placeholder = { Text(if (state.filters.searchMode == SearchMode.OCR) "hóa đơn, tin nhắn, 70.000…" else "Try @thảo at the beach…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                onSearch = { onSearch(state.query) },
            )
            Text(if (state.filters.searchMode == SearchMode.OCR)
                "Find words or amounts in bills and screenshots, with or without Vietnamese accents."
            else "Describe a photo, choose an image, or combine both. Visual descriptions work best in English.", style = MaterialTheme.typography.bodySmall)
            val mention = Regex("(?:^|\\s)@([\\p{L}\\p{M}\\p{N}_]*)$").find(state.query)
            if (mention != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.people.filter { it.handle.startsWith(VietnameseText.normalize(mention.groupValues[1]), ignoreCase = true) }.take(6).forEach { person ->
                        AssistChip(onClick = {
                            val start = state.query.lastIndexOf('@')
                            onQueryChanged(state.query.take(start) + "@${person.handle} ")
                        }, label = { Text("@${person.handle}") })
                    }
                }
            }
            if (state.filters.searchMode == SearchMode.NORMAL) state.selectedImageUri?.let { uri ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PhotoThumbnail(uri, Modifier.size(64.dp), "Search reference")
                    Text("Find similar photos", Modifier.weight(1f).padding(horizontal = 12.dp))
                    IconButton(onClick = { onSelectImage(null) }) { Icon(Icons.Default.Close, "Remove search image") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.filters.searchMode == SearchMode.NORMAL) AssistChip(
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    label = { Text(if (state.selectedImageUri == null) "Search by image" else "Change image") },
                    leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                )
                AssistChip(onClick = onOpenPeople, label = { Text("Saved people") })
            }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onOpenFilters, label = { Text(timeRangeLabel(state.filters.timeRange) + "  ▾") })
            }
            Button(
                onClick = { onSearch(state.query) },
                enabled = state.query.isNotBlank() || (state.filters.searchMode == SearchMode.NORMAL && state.selectedImageUri != null),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Search") }
        }
        when (val indexing = if (state.filters.searchMode == SearchMode.OCR) state.ocrIndexingStatus else state.indexingStatus) {
            is IndexingStatus.Running -> item {
                IndexingCard(indexing, when {
                    state.filters.searchMode == SearchMode.OCR -> "Scanning text"
                    else -> "Indexing photos and faces"
                })
            }
            is IndexingStatus.Unavailable -> item {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Search model unavailable", fontWeight = FontWeight.SemiBold)
                        Text(indexing.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            is IndexingStatus.Interrupted -> item {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Indexing paused", fontWeight = FontWeight.SemiBold)
                        Text("${indexing.indexed} / ${indexing.total} photos indexed. The next background pass will retry remaining photos.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            is IndexingStatus.Waiting -> item {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Indexing queued", fontWeight = FontWeight.SemiBold)
                        Text("${indexing.indexed} saved · ${(indexing.total - indexing.indexed).coerceAtLeast(0)} remaining. " +
                            (indexing.reason ?: "Android will resume when background work is allowed and the battery is not low."), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            else -> Unit
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.filters.searchMode == SearchMode.OCR) "Text saved: ${state.textIndexedCount} / ${state.libraryTotal} photos"
                    else "Photos indexed: ${state.indexedCount} / ${state.libraryTotal}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
            Text("Try searching", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val suggestions = if (state.filters.searchMode == SearchMode.OCR) listOf("hóa đơn", "tổng cộng", "cà phê", "hẹn gặp") else Suggestions
                suggestions.forEach { suggestion -> AssistChip(onClick = { onSearch(suggestion) }, label = { Text(suggestion) }) }
            }
        }
        if (state.history.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent searches", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    TextButton(onClick = onClearHistory) { Text("Clear") }
                }
            }
            items(state.history, key = { it.id }) { history -> HistoryRow(history) {
                onHistorySearch(history.query, SearchMode.fromStored(history.searchMode))
            } }
        }
        if (state.recentPhotos.isNotEmpty()) {
            item { Text("Your library", fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    state.recentPhotos.take(8).chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        row.forEach { photo ->
                            PhotoThumbnail(
                                photo.uri,
                                Modifier.weight(1f).clickable { onPhoto(photo.mediaId) },
                                photo.displayName,
                            )
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
internal fun PermissionScreen(onRequestPermission: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp), contentAlignment = Alignment.Center) {
        ElevatedCard(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Search your own photo library", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Your photos and searches stay on this device. Semantic search does not require photos to be uploaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("Choose photo access") }
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}")))
                }) { Text("Open app settings") }
            }
        }
    }
}

@Composable
private fun IndexingCard(status: IndexingStatus.Running, title: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text("${status.indexed} saved · ${(status.total - status.indexed).coerceAtLeast(0)} remaining", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Progress is saved automatically. Reopening resumes the remaining photos.", style = MaterialTheme.typography.bodySmall)
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
    onSearchAgain: () -> Unit = {},
) {
    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { IconButton(onClick = onOpenFilters) { Icon(Icons.Default.FilterList, "Filters") } },
        )
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            val headerMaxHeight = maxHeight * 0.55f
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.heightIn(max = headerMaxHeight).verticalScroll(rememberScrollState())) {
                    Text(state.resultQuery.ifBlank { "Similar photos" }, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
                    state.resultImageUri?.let { uri ->
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PhotoThumbnail(uri, Modifier.size(52.dp), "Search reference")
                            Text("Image similarity search", Modifier.padding(start = 12.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (state.results.size == 100) "Top 100 results" else "${state.results.size} results", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).padding(8.dp))
                        TextButton(onClick = onSearchAgain) { Text("Search again") }
                    }
                    if (state.filters.searchMode == SearchMode.NORMAL && state.indexedCount < state.libraryTotal) {
                        Text("Photo indexing: ${state.indexedCount} / ${state.libraryTotal}. Search again as more photos become ready.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                    }
                    if (state.filters.searchMode != SearchMode.NORMAL && state.textIndexedCount < state.libraryTotal) {
                        Text("Text scanning: ${state.textIndexedCount} / ${state.libraryTotal} photos. Results may be incomplete.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                    }
                    if (state.resultQuery.contains('@') && state.faceIndexedCount < state.libraryTotal) {
                        Text("Face indexing: ${state.faceIndexedCount} / ${state.libraryTotal} photos. Results may be incomplete.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        AssistChip(onClick = onOpenFilters, label = { Text(timeRangeLabel(state.filters.timeRange) + "  ▾") })
                        if (state.filters != SearchFilters(searchMode = state.filters.searchMode)) TextButton(onClick = onClearFilters) { Text("Clear") }
                    }
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
                    columns = GridCells.Adaptive(110.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(state.results, key = { it.media.mediaId }) { result ->
                        Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(4.dp)).clickable { onPhoto(result.media.mediaId) }) {
                            AsyncImage(result.media.uri, result.media.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            if (result.textMatch) Surface(Modifier.align(Alignment.BottomStart), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("Text match", Modifier.padding(4.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FiltersScreen(initial: SearchFilters, onClose: () -> Unit, onApply: (SearchFilters) -> Unit) {
    var filters by rememberSaveable(initial) { mutableStateOf(initial) }
    var showRangePicker by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Filters", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
                actions = { TextButton(onClick = { filters = SearchFilters(searchMode = initial.searchMode) }) { Text("Reset") } },
            )
        },
        bottomBar = { Button(onClick = { onApply(filters) }, Modifier.fillMaxWidth().padding(20.dp).height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Apply filters") } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 20.dp)) {
            item { SectionTitle("TIME RANGE") }
            items(TimeRange.entries) { range -> SelectionRow(timeRangeLabel(range), filters.timeRange == range) {
                if (range == TimeRange.CUSTOM) showRangePicker = true else filters = filters.copy(timeRange = range)
            } }
            if (filters.timeRange == TimeRange.CUSTOM && filters.customStartMillis != null && filters.customEndExclusiveMillis != null) item {
                val zone = java.time.ZoneId.systemDefault()
                Text("${Instant.ofEpochMilli(filters.customStartMillis!!).atZone(zone).toLocalDate()} — ${Instant.ofEpochMilli(filters.customEndExclusiveMillis!! - 1).atZone(zone).toLocalDate()}")
            }
            item { HorizontalDivider(Modifier.padding(vertical = 14.dp)); SectionTitle("SORT BY") }
            items(ResultSort.entries) { sort -> SelectionRow(sortLabel(sort), filters.sort == sort) { filters = filters.copy(sort = sort) } }
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
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        RadioButton(selected, onClick)
    }
}

private fun timeRangeLabel(range: TimeRange) = when (range) {
    TimeRange.ANY_TIME -> "Any time"; TimeRange.TODAY -> "Today"; TimeRange.YESTERDAY -> "Yesterday"
    TimeRange.THIS_WEEK -> "This week"; TimeRange.THIS_MONTH -> "This month"; TimeRange.CUSTOM -> "Custom range"
}
private fun sortLabel(sort: ResultSort) = when (sort) { ResultSort.MOST_RELEVANT -> "Most relevant"; ResultSort.NEWEST_FIRST -> "Newest first"; ResultSort.OLDEST_FIRST -> "Oldest first" }
private fun searchModeLabel(mode: SearchMode) = when (mode) {
    SearchMode.NORMAL -> "Normal query"
    SearchMode.OCR -> "OCR search"
}
private val PreviewMedia = MediaItem(1, "", MediaType.IMAGE, "Beach.jpg", 1_715_760_000_000, 0, 0, 4032, 3024, "image/jpeg", "camera", "Camera")
private val PreviewState = SearchUiState(photoAccess = PhotoAccess.FULL, query = "me at the beach", indexingStatus = IndexingStatus.Running(2431, 8912), history = listOf(SearchHistoryEntity(1, "sunset", System.currentTimeMillis(), null)), results = listOf(SearchResult(PreviewMedia, .42f)), resultQuery = "me at the beach", albums = listOf(Album("all", "All Photos", 8912, null)))

@Preview(showBackground = true) @Composable private fun HomePreview() = PhotoSearchTheme { SearchHomeScreen(PreviewState, {}, {}, {}, {}, {}, {}) }
@Preview(showBackground = true) @Composable private fun SearchingPreview() = PhotoSearchTheme { SearchingScreen("me at the beach", {}, {}) }
@Preview(showBackground = true) @Composable private fun ResultsPreview() = PhotoSearchTheme { ResultsScreen(PreviewState, {}, {}, {}, {}) }
@Preview(showBackground = true) @Composable private fun FiltersPreview() = PhotoSearchTheme { FiltersScreen(SearchFilters(), {}, {}) }

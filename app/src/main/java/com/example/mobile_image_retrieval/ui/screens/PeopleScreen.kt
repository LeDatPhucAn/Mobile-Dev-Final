@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.mobile_image_retrieval.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.mobile_image_retrieval.ai.PersonNames
import com.example.mobile_image_retrieval.ai.FaceModelContract
import com.example.mobile_image_retrieval.ui.SearchUiState

@Composable
fun PeopleScreen(
    state: SearchUiState,
    onSave: (String, String, Long?) -> Unit,
    onRemove: (Long) -> Unit,
    onSearch: (String) -> Unit,
    onClearSaveError: () -> Unit,
    onRefresh: () -> Unit,
) {
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var saveVersion by rememberSaveable { mutableLongStateOf(state.personSaveVersion) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            photoUri = uri.toString()
            onClearSaveError()
        }
    }
    LaunchedEffect(state.personSaveVersion) {
        if (state.personSaveVersion != saveVersion) {
            photoUri = null
            name = ""
            editingId = null
            saveVersion = state.personSaveVersion
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("People") }) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text("Give a friend's photo a name, then search with @name and a description, like @alex at the beach.")
                Text(
                    "Choose a clear photo of one person. Faces are recognized on this device. Mentioning @alex @mai searches for photos containing both people.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Button(
                    onClick = { editingId = null; name = ""; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !state.isSavingPerson,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Text("Add person")
                }
                Text("Faces indexed: ${state.faceIndexedCount} / ${state.libraryTotal} photos", style = MaterialTheme.typography.bodySmall)
                if (state.faceIndexedCount < state.libraryTotal) {
                    Text("Results may be incomplete while indexing finishes.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onRefresh) { Text("Resume indexing") }
                }
            }
            if (state.people.isEmpty()) item {
                Text("No saved people yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(state.people, key = { it.id }) { person ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(person.thumbnail, person.name, Modifier.size(64.dp), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(person.name, style = MaterialTheme.typography.titleMedium)
                            val ready = person.embeddingModel == FaceModelContract.VERSION
                            TextButton(onClick = { onSearch("@${person.handle}") }, enabled = ready) { Text("@${person.handle}") }
                            if (!ready) Text("Update this photo to enable face recognition", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                editingId = person.id
                                name = person.name
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }, enabled = !state.isSavingPerson) { Text(if (ready) "Change face photo" else "Update face") }
                        }
                        IconButton(onClick = { onRemove(person.id) }) {
                            Icon(Icons.Default.Delete, "Remove ${person.name}")
                        }
                    }
                }
            }
        }
    }
    photoUri?.let { uri ->
        val handle = remember(name) { runCatching { PersonNames.handle(name) }.getOrNull() }
        val duplicate = state.people.any { it.handle == handle && it.id != editingId }
        AlertDialog(
            onDismissRequest = { if (!state.isSavingPerson) photoUri = null },
            title = { Text("Save person") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(uri, "Selected reference photo", Modifier.size(120.dp), contentScale = ContentScale.Crop)
                    VietnameseTextField(
                        value = name,
                        onValueChange = { name = it; onClearSaveError() },
                        label = { Text("Name") },
                        placeholder = { Text("Alex") },
                        singleLine = true,
                        enabled = !state.isSavingPerson && editingId == null,
                        isError = duplicate || (name.isNotBlank() && handle == null),
                        supportingText = {
                            Text(when {
                                duplicate -> "@$handle is already saved"
                                handle != null -> "Mention as @$handle"
                                else -> "1–40 letters, numbers or underscores. Spaces become underscores."
                            })
                        },
                    )
                    state.personSaveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.isSavingPerson) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onSave(name, uri, editingId) },
                    enabled = handle != null && !duplicate && !state.isSavingPerson,
                ) { Text(if (state.isSavingPerson) "Saving…" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { photoUri = null }, enabled = !state.isSavingPerson) { Text("Cancel") }
            },
        )
    }
}

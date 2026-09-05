@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.mobile_image_retrieval.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.mobile_image_retrieval.data.db.PhotoTextEntity
import com.example.mobile_image_retrieval.domain.model.MediaItem
import kotlinx.coroutines.CancellationException

@Suppress("DEPRECATION")
@Composable
fun PhotoTextScreen(photo: MediaItem?, onBack: () -> Unit, readText: suspend (MediaItem) -> PhotoTextEntity) {
    var document by remember(photo?.mediaId, photo?.dateModified) { mutableStateOf<PhotoTextEntity?>(null) }
    var error by remember(photo?.mediaId) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(photo?.mediaId, photo?.dateModified, attempt) {
        error = null
        if (photo != null) try { document = readText(photo) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "Could not read this photo." }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Text in photo") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        }, actions = {
            TextButton(enabled = !document?.text.isNullOrBlank(), onClick = { clipboard.setText(AnnotatedString(document!!.text)) }) { Text("Copy") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when {
                photo == null -> Text("This photo is no longer available.")
                error != null -> { Text(error!!, color = MaterialTheme.colorScheme.error); TextButton(onClick = { attempt++ }) { Text("Try again") } }
                document == null -> { CircularProgressIndicator(); Text("Reading text on this device…") }
                document!!.text.isBlank() -> Text("No readable text found. Try a sharper, well-lit photo.")
                else -> {
                    Text("Detected text — check names and amounts against the photo.", style = MaterialTheme.typography.bodySmall)
                    if (document!!.truncated) Text("Showing the first 100,000 characters.")
                    SelectionContainer { Text(document!!.text) }
                }
            }
        }
    }
}

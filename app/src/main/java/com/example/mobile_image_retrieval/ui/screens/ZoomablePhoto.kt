package com.example.mobile_image_retrieval.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage

@Composable
internal fun ZoomablePhoto(uri: String, description: String, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var attempt by remember(uri) { mutableIntStateOf(0) }
    fun reset() { scale = 1f; offset = Offset.Zero }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        val maxX = size.width * (scale - 1f) / 2f
        val maxY = size.height * (scale - 1f) / 2f
        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
    }
    LaunchedEffect(scale) { onZoomChanged(scale > 1f) }
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { size = it }
        .semantics {
            stateDescription = if (scale > 1f) "Zoomed photo" else "Full photo"
            customActions = listOf(CustomAccessibilityAction("Reset zoom") { reset(); true })
        }
        .pointerInput(uri) { detectTapGestures(onDoubleTap = { if (scale > 1f) reset() else scale = 2.5f }) }
        .transformable(transform, canPan = { scale > 1f }), contentAlignment = Alignment.Center) {
        key(attempt) {
            SubcomposeAsyncImage(
                model = uri,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                },
                loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                error = {
                    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Could not open this photo", color = Color.White)
                        TextButton(onClick = { reset(); attempt++ }) { Text("Try again") }
                    }
                },
            )
        }
        if (scale > 1f) TextButton(onClick = { reset() }, modifier = Modifier.align(Alignment.TopEnd)) { Text("Reset zoom") }
    }
}

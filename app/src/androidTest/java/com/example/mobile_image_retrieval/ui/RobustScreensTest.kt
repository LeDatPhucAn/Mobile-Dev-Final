package com.example.mobile_image_retrieval.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.example.mobile_image_retrieval.domain.model.*
import com.example.mobile_image_retrieval.ui.screens.FiltersScreen
import com.example.mobile_image_retrieval.ui.screens.PhotoViewerScreen
import com.example.mobile_image_retrieval.ui.screens.ZoomablePhoto
import com.example.mobile_image_retrieval.ui.theme.PhotoSearchTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After
import androidx.compose.ui.semantics.SemanticsProperties

class RobustScreensTest {
    @get:Rule val compose = createComposeRule()
    private val photo = MediaItem(1, "content://test/missing", MediaType.IMAGE, "Test photo", 0, 0, 0, 100, 100, "image/jpeg", null, null)
    private var imageFile: java.io.File? = null
    @After fun cleanup() { imageFile?.delete() }

    @Test fun doubleTapZoomCanBeReset() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = java.io.File.createTempFile("zoom-test", ".png", context.cacheDir).also { imageFile = it }
        android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888).let { bitmap ->
            bitmap.eraseColor(android.graphics.Color.GREEN)
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.setContent { PhotoSearchTheme { ZoomablePhoto(android.net.Uri.fromFile(file).toString(), "Zoom test", {}) } }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Full photo"))
            .performTouchInput { doubleClick() }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoomed photo")).assertExists()
        compose.onNodeWithText("Reset zoom").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Full photo")).assertExists()
    }

    @Test fun viewerActionsRemainReachableOnShortLandscapeScreen() {
        var saved = false
        var read = false
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
            PhotoSearchTheme {
                Box(Modifier.requiredSize(640.dp, 280.dp)) {
                    PhotoViewerScreen(listOf(photo), 1, {}, {}, {}, onSaveCopy = { saved = true }, onReadText = { read = true })
                }
            }
            }
        }
        compose.onNodeWithText("Save a copy").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Read text").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(saved); assertTrue(read) }
    }

    @Test fun filterDraftSurvivesSavedStateRecreation() {
        val restoration = StateRestorationTester(compose)
        var applied: SearchFilters? = null
        restoration.setContent { PhotoSearchTheme { FiltersScreen(SearchFilters(), {}, { applied = it }) } }
        compose.onNodeWithText("Today").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Apply filters").performClick()
        compose.runOnIdle { assertEquals(TimeRange.TODAY, applied?.timeRange) }
    }

    @Test fun cancellingCustomRangeKeepsThePreviousFilter() {
        var applied: SearchFilters? = null
        compose.setContent { PhotoSearchTheme { FiltersScreen(SearchFilters(), {}, { applied = it }) } }
        compose.onNodeWithText("Custom range").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Apply filters").performClick()
        compose.runOnIdle { assertEquals(TimeRange.ANY_TIME, applied?.timeRange) }
    }
}

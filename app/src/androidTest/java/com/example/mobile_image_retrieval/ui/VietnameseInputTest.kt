package com.example.mobile_image_retrieval.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import com.example.mobile_image_retrieval.ui.screens.VietnameseTextField
import com.example.mobile_image_retrieval.ui.screens.SearchHomeScreen
import com.example.mobile_image_retrieval.domain.model.IndexingStatus
import com.example.mobile_image_retrieval.domain.model.SearchMode
import com.example.mobile_image_retrieval.permissions.PhotoAccess
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VietnameseInputTest {
    @get:Rule val compose = createComposeRule()

    @Test fun twoSearchTabsKeepOcrProgressSeparateFromVisualWork() {
        var state by mutableStateOf(SearchUiState(photoAccess = PhotoAccess.FULL, libraryTotal = 1,
            textIndexedCount = 1, indexingStatus = IndexingStatus.Running(0, 1), selectedImageUri = "content://test/1"))
        compose.setContent {
            SearchHomeScreen(state, {}, {}, {}, {}, {}, {}, onModeChanged = { state = state.copy(filters = state.filters.copy(searchMode = it)) })
        }
        compose.onNodeWithText("Normal query").assertIsSelected()
        compose.onNodeWithText("Photos + text").assertDoesNotExist()
        compose.onNodeWithText("Visual similarity").assertDoesNotExist()
        compose.onNodeWithText("Change image").assertExists()
        compose.onNodeWithText("OCR search").performClick().assertIsSelected()
        compose.onNodeWithText("Change image").assertDoesNotExist()
        compose.onNodeWithText("Search by image").assertDoesNotExist()
        compose.onNodeWithText("Indexing photos and faces").assertDoesNotExist()
        compose.onNodeWithText("Search").assertIsNotEnabled()
        compose.onNodeWithText("Normal query").performClick()
        compose.onNodeWithText("Change image").assertExists()
    }

    @Test fun composingAccentsSurviveRecompositionAndExternalSuggestions() {
        var text by mutableStateOf("")
        lateinit var view: View
        lateinit var connection: InputConnection
        compose.setContent {
            view = LocalView.current
            VietnameseTextField(text, { text = it }, Modifier.testTag("query"))
        }
        val field = compose.onNodeWithTag("query")
        field.performClick()
        compose.runOnIdle {
            connection = checkNotNull(view.onCreateInputConnection(EditorInfo()))
            connection.setComposingText("hoa", 1)
        }
        field.assertTextEquals("hoa")
        compose.runOnIdle { connection.setComposingText("hóa", 1) }
        field.assertTextEquals("hóa")
        compose.runOnIdle { connection.commitText("hóa", 1) }
        field.performTextInput(" đơn @đặng_thảo")
        compose.runOnIdle { assertEquals("hóa đơn @đặng_thảo", text) }
        compose.runOnIdle { text = "tin nhắn" }
        field.assertTextEquals("tin nhắn")
        field.performTextInput(" mới")
        compose.runOnIdle { assertEquals("tin nhắn mới", text) }
    }
}

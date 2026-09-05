package com.example.mobile_image_retrieval.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchModeTest {
    @Test fun legacyHistoryRestoresTheCorrespondingTab() {
        assertEquals(listOf(SearchMode.NORMAL, SearchMode.OCR), SearchMode.entries)
        for (legacy in listOf("HYBRID", "VISUAL", "NORMAL", "unknown")) assertEquals(SearchMode.NORMAL, SearchMode.fromStored(legacy))
        for (legacy in listOf("TEXT_IN_PHOTOS", "OCR")) assertEquals(SearchMode.OCR, SearchMode.fromStored(legacy))
    }
}

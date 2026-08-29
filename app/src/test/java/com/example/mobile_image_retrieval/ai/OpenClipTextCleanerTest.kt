package com.example.mobile_image_retrieval.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenClipTextCleanerTest {
    @Test
    fun appliesOpenClipCleanLowerPipeline() {
        assertEquals("a & b", OpenClipTextCleaner.cleanLower("  A &amp; B\n"))
        assertEquals("<tag>", OpenClipTextCleaner.cleanLower("&amp;lt;TAG&amp;gt;"))
    }

    @Test
    fun preservesCorrectUnicode() {
        assertEquals(
            "một con mèo đang ngồi trên ghế",
            OpenClipTextCleaner.cleanLower("MỘT con mèo đang ngồi trên ghế"),
        )
    }

    @Test
    fun repairsCommonUtf8Mojibake() {
        assertEquals("café", OpenClipTextCleaner.cleanLower("cafÃ©"))
    }
}

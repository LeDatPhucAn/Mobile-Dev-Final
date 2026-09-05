package com.example.mobile_image_retrieval.ai

import org.junit.Assert.*
import org.junit.Test
import java.text.Normalizer

class VietnameseTextTest {
    @Test fun foldedSearchPreservesWordsAndAmounts() {
        assertEquals("hoa don ca phe sua 70 000 d", VietnameseText.searchable("HÓA ĐƠN: Cà phê sữa — 70.000 đ"))
        assertEquals(VietnameseText.searchable("hóa đơn"), VietnameseText.searchable("hoa don"))
    }
    @Test fun decomposedInputAndMentionsNormalizeOnSubmission() {
        val original = "@Đặng_Thảo hóa đơn"
        val decomposed = Normalizer.normalize(original, Normalizer.Form.NFD)
        assertEquals(original, VietnameseText.normalize(decomposed))
        assertEquals(listOf("đặng_thảo"), PersonalizedQueryParser.parse(decomposed).handles)
    }
    @Test fun queriesTreatOperatorsAsLiteralWordsAndRejectEmptyText() {
        assertEquals("\"hoa\" \"don\" \"or\" \"near\"", VietnameseText.ftsQuery("hóa đơn OR NEAR* hóa"))
        assertNull(VietnameseText.ftsQuery("— * \""))
    }
}

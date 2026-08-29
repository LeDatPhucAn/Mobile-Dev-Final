package com.example.mobile_image_retrieval.ai

import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.domain.model.TimeRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class DateRangeCalculatorTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val clock = Clock.fixed(Instant.parse("2026-08-29T06:00:00Z"), zone)

    @Test fun `any time has no bounds`() {
        val bounds = DateRangeCalculator.bounds(SearchFilters(), clock)
        assertNull(bounds.startMillis); assertNull(bounds.endExclusiveMillis)
    }

    @Test fun `today uses device local day`() {
        val bounds = DateRangeCalculator.bounds(SearchFilters(timeRange = TimeRange.TODAY), clock)
        assertEquals(Instant.parse("2026-08-28T17:00:00Z").toEpochMilli(), bounds.startMillis)
        assertEquals(Instant.parse("2026-08-29T17:00:00Z").toEpochMilli(), bounds.endExclusiveMillis)
    }

    @Test fun `custom range is preserved`() {
        assertEquals(DateBounds(10, 20), DateRangeCalculator.bounds(SearchFilters(timeRange = TimeRange.CUSTOM, customStartMillis = 10, customEndExclusiveMillis = 20), clock, Locale.US))
    }
}

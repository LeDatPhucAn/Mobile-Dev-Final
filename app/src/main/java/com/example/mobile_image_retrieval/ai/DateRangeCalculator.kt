package com.example.mobile_image_retrieval.ai

import com.example.mobile_image_retrieval.domain.model.SearchFilters
import com.example.mobile_image_retrieval.domain.model.TimeRange
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

data class DateBounds(val startMillis: Long?, val endExclusiveMillis: Long?)

object DateRangeCalculator {
    fun bounds(
        filters: SearchFilters,
        clock: Clock = Clock.systemDefaultZone(),
        locale: Locale = Locale.getDefault(),
    ): DateBounds {
        if (filters.timeRange == TimeRange.CUSTOM) return DateBounds(filters.customStartMillis, filters.customEndExclusiveMillis)
        if (filters.timeRange == TimeRange.ANY_TIME) return DateBounds(null, null)
        val zone = clock.zone
        val today = LocalDate.now(clock)
        val (start, end) = when (filters.timeRange) {
            TimeRange.TODAY -> today to today.plusDays(1)
            TimeRange.YESTERDAY -> today.minusDays(1) to today
            TimeRange.THIS_WEEK -> {
                val firstDay = WeekFields.of(locale).firstDayOfWeek
                val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDay))
                weekStart to weekStart.plusWeeks(1)
            }
            TimeRange.THIS_MONTH -> today.withDayOfMonth(1) to today.withDayOfMonth(1).plusMonths(1)
            else -> error("Handled above")
        }
        return DateBounds(start.atStartOfDay(zone).toInstant().toEpochMilli(), end.atStartOfDay(zone).toInstant().toEpochMilli())
    }
}

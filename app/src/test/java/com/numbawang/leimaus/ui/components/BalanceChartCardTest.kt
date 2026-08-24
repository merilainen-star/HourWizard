package com.numbawang.leimaus.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceChartCardTest {
    private fun point(hours: Double) = BalanceDataPoint(
        dateLabel = "Nyt",
        hours = hours,
        rawString = "",
        timestamp = 0L
    )

    @Test
    fun viewportMakesOneHourChangeClearlyVisible() {
        val viewport = calculateBalanceChartViewport(listOf(point(28.0), point(29.0)))

        assertEquals(4.0, viewport.maxHours - viewport.minHours, 0.001)
        assertTrue(28.0 >= viewport.minHours && 29.0 <= viewport.maxHours)
    }

    @Test
    fun viewportStopsAtZeroNearLowerEnd() {
        val viewport = calculateBalanceChartViewport(listOf(point(0.0), point(1.0)))

        assertEquals(0.0, viewport.minHours, 0.001)
        assertEquals(4.0, viewport.maxHours, 0.001)
    }

    @Test
    fun viewportStopsAtSixtyNearUpperEnd() {
        val viewport = calculateBalanceChartViewport(listOf(point(59.0), point(60.0)))

        assertEquals(56.0, viewport.minHours, 0.001)
        assertEquals(60.0, viewport.maxHours, 0.001)
    }

    @Test
    fun viewportUsesFullRangeForWidelySeparatedValues() {
        val viewport = calculateBalanceChartViewport(listOf(point(0.0), point(60.0)))

        assertEquals(0.0, viewport.minHours, 0.001)
        assertEquals(60.0, viewport.maxHours, 0.001)
    }
}

package dev.wishlist.counter

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ChartDataTest {
    @Test fun `sparse wishlist reports remain visibly connected without inventing zero days`() {
        val points=listOf(ChartPoint(LocalDate.parse("2026-11-11"),1),ChartPoint(LocalDate.parse("2026-11-15"),2))
        val connections=chartConnections(points)
        assertEquals("The latest report must not be isolated from the line",1,connections.size)
        assertTrue("A connector across dates without reports must be distinguishable",connections.single().spansMissingDays)
        assertEquals(1L,connections.single().from.value)
        assertEquals(2L,connections.single().to.value)
    }

    private val start = LocalDate.parse("2026-11-08")
    private val end = LocalDate.parse("2026-11-16")
    private fun report(date: String, adds: Long) = DailyReport(42, date, adds, 0, 0, 0)

    @Test fun `the original five reports are all connected and only unavailable dates are dashed`() {
        val points = listOf(8 to 3L, 9 to 2L, 10 to 3L, 11 to 1L, 15 to 2L).map { (day, count) -> ChartPoint(LocalDate.of(2026, 11, day), count) }
        val connections = chartConnections(points)
        assertEquals(4, connections.size)
        assertEquals(1, connections.count { it.spansMissingDays })
        assertEquals(11L, points.sumOf { it.value })
    }
    @Test fun `leading pre-wishlist history is hidden without expanding the selected interval`() {
        val history = listOf(report("2026-11-01",0),report("2026-11-08",3),report("2026-11-09",0))
        assertEquals(start,trimmedChartStart(LocalDate.parse("2026-11-01"),end,history))
        assertEquals(LocalDate.parse("2026-11-09"),trimmedChartStart(LocalDate.parse("2026-11-09"),end,history))
    }
    @Test fun `an all-zero or empty history preserves its date range`() {
        assertEquals(start,trimmedChartStart(start,end,emptyList()))
        assertEquals(start,trimmedChartStart(start,end,listOf(report("2026-11-08",0))))
        assertEquals(start,trimmedChartStart(start,end,listOf(report("2026-11-20",1))))
    }
    @Test fun `touch selection snaps to calendar dates and clamps to the chart edges`() {
        assertEquals(start,chartDateAtFraction(start,end,-1f))
        assertEquals(end,chartDateAtFraction(start,end,2f))
        assertEquals(LocalDate.parse("2026-11-10"),chartDateAtFraction(start,end,.25f))
        assertEquals(start,chartDateAtFraction(start,start,.75f))
    }
    @Test fun `a tooltip distinguishes an explicit zero from an unavailable report`() {
        val history=listOf(report("2026-11-08",3),report("2026-11-09",0))
        assertEquals(3L,chartSelection(history,start)?.adds)
        assertEquals(0L,chartSelection(history,start.plusDays(1))?.adds)
        assertNull(chartSelection(history,start.plusDays(2)))
    }
}

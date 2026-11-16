package dev.wishlist.counter

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ReportingTest {
    private val game=Game(42,"Test game",baseline=100,baselineDate="2026-11-10")
    private fun report(date:String,adds:Long=10,deletes:Long=2,purchases:Long=1,gifts:Long=1)=DailyReport(42,date,adds,deletes,purchases,gifts)

    @Test fun `baseline is end of day and movements subtract all exit types`() {
        val reports=listOf(report("2026-11-10",adds=999),report("2026-11-11"),report("2026-11-12",adds=20))
        assertEquals(122L,derivedBalance(game,reports,LocalDate.parse("2026-11-12")))
        assertEquals(100L,derivedBalance(game,reports,LocalDate.parse("2026-11-10")))
    }
    @Test fun `baseline reconstructs backward and forward balances and accumulates from zero when unset`() {
        val reports=listOf(report("2026-11-11"),report("2026-11-13"))
        assertEquals("Quiet days without a Steam report preserve the running balance",112L,derivedBalance(game,reports,LocalDate.parse("2026-11-13")))
        assertEquals("Without a manual baseline, balance accumulates from 0 across cached reports",12L,derivedBalance(game.copy(baseline=null),reports,LocalDate.parse("2026-11-13")))
        assertNull("Without a baseline or reports before the first report, earlier dates have no balance",derivedBalance(game.copy(baseline=null),reports,LocalDate.parse("2026-11-09")))
        val anchoredToday=Game(42,"Test game",baseline=11,baselineDate="2026-11-15")
        val fiveReports=listOf(
            DailyReport(42,"2026-11-08",3,0,0,0),
            DailyReport(42,"2026-11-09",2,0,0,0),
            DailyReport(42,"2026-11-10",3,0,0,0),
            DailyReport(42,"2026-11-11",1,0,0,0),
            DailyReport(42,"2026-11-15",2,0,0,0),
        )
        assertEquals(3L,derivedBalance(anchoredToday,fiveReports,LocalDate.parse("2026-11-08")))
        assertEquals(5L,derivedBalance(anchoredToday,fiveReports,LocalDate.parse("2026-11-09")))
        assertEquals(8L,derivedBalance(anchoredToday,fiveReports,LocalDate.parse("2026-11-10")))
        assertEquals(9L,derivedBalance(anchoredToday,fiveReports,LocalDate.parse("2026-11-11")))
        assertEquals(9L,derivedBalance(anchoredToday,fiveReports,LocalDate.parse("2026-11-13")))
        assertEquals(11L,derivedBalance(anchoredToday,fiveReports,LocalDate.parse("2026-11-15")))
    }
    @Test fun `replaced daily report counts once`() {
        val reports=listOf(report("2026-11-11",adds=20),report("2026-11-11",adds=30))
        assertEquals(126L,derivedBalance(game,reports,LocalDate.parse("2026-11-11")))
    }
    @Test fun `negative derived balances require investigation`() {
        assertNull(derivedBalance(game,listOf(report("2026-11-11",adds=0,deletes=200)),LocalDate.parse("2026-11-11")))
    }
    @Test fun `provisional count corrections cannot create duplicate alerts`() {
        assertEquals(0L,unseenAdditions(100,80))
        assertEquals(0L,unseenAdditions(100,100))
        assertEquals(7L,unseenAdditions(100,107))
    }
    @Test fun `quiet hours span midnight and exclude the ending boundary`() {
        val rule=AlertRule(quiet=true,start="22:00",end="08:00")
        assertTrue(inQuietHours(rule,LocalTime.parse("22:00")))
        assertTrue(inQuietHours(rule,LocalTime.parse("02:00")))
        assertFalse(inQuietHours(rule,LocalTime.parse("08:00")))
        assertFalse(inQuietHours(rule,LocalTime.NOON))
        assertFalse(inQuietHours(rule.copy(quiet=false),LocalTime.parse("02:00")))
        assertFalse(inQuietHours(rule.copy(start="08:00"),LocalTime.parse("02:00")))
    }
    @Test fun `AppIDs respect unsigned 32 bit API contract`() {
        assertEquals(4_294_967_295L,validAppId("4294967295"))
        for(value in listOf("0","-1","4294967296","abc","","999999999999999999999"))assertNull(validAppId(value))
    }
    @Test fun `USD financial parsing refund rate and Steam review milestones are exact`() {
        assertEquals(3_117_201L, parseUsdMicros("311.7201"))
        assertEquals(-255_322L, parseUsdMicros("-25.5322"))
        assertEquals("$311.72", formatUsd(3_117_201L))
        assertEquals(12.5, refundRatePercent(grossUnits = 80, returnedUnits = 10), 0.001)
        assertEquals(0.0, refundRatePercent(grossUnits = 0, returnedUnits = 0), 0.001)
        assertEquals(10L, nextReviewMilestone(0))
        assertEquals(10L, nextReviewMilestone(9))
        assertEquals(50L, nextReviewMilestone(10))
        assertEquals(500L, nextReviewMilestone(50))
        assertEquals(1_000L, nextReviewMilestone(500))
    }
}

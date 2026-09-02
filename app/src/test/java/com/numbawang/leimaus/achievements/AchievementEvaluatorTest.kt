package com.numbawang.leimaus.achievements

import com.numbawang.leimaus.data.db.StampEntity
import com.numbawang.leimaus.data.preferences.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

private val HELSINKI_TZ: TimeZone = TimeZone.getTimeZone("Europe/Helsinki")

class AchievementEvaluatorTest {

    private fun stamp(timestamp: Long, actionType: String, balance: String = "", isSuccess: Boolean = true) =
        StampEntity(
            timestamp = timestamp,
            formattedTime = "",
            actionType = actionType,
            isSuccess = isSuccess,
            message = "",
            balance = balance
        )

    private fun settings(
        workdayHours: Int = 7,
        workdayMinutes: Int = 30,
        lunchBreakMinutes: Int = 30,
        enabledDaysString: String = "1,2,3,4,5",
        lastServerBalance: String = ""
    ) = AppSettings(
        workdayHours = workdayHours,
        workdayMinutes = workdayMinutes,
        lunchBreakMinutes = lunchBreakMinutes,
        enabledDaysString = enabledDaysString,
        lastServerBalance = lastServerBalance
    )

    private fun helsinkiTs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(HELSINKI_TZ)
        cal.set(year, month - 1, day, hour, minute, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun helsinkiTsPlusDays(base: Long, days: Int): Long =
        Calendar.getInstance(HELSINKI_TZ).apply { timeInMillis = base; add(Calendar.DAY_OF_MONTH, days) }.timeInMillis

    /** 2026-01-05 is a Monday. Builds [days] consecutive Mon-Fri sessions starting there, each
     * clocking in at [clockInHour]:[clockInMinute] and out 8 hours later. */
    private fun weekdaySessions(days: Int, clockInHour: Int = 8, clockInMinute: Int = 0): List<StampEntity> {
        val logs = mutableListOf<StampEntity>()
        val base = helsinkiTs(2026, 1, 5, clockInHour, clockInMinute)
        var added = 0
        var offset = 0
        while (added < days) {
            val inTs = helsinkiTsPlusDays(base, offset)
            offset++
            val dow = Calendar.getInstance(HELSINKI_TZ).apply { timeInMillis = inTs }.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) continue
            logs += stamp(inTs, "SISÄÄN")
            logs += stamp(inTs + 8 * 60 * 60 * 1000L, "ULOS")
            added++
        }
        return logs
    }

    @Test
    fun emptyHistoryUnlocksNothing() {
        assertTrue(AchievementEvaluator.evaluate(emptyList(), settings()).isEmpty())
    }

    @Test
    fun firstStampUnlocksOnFirstSuccessfulPunch() {
        val logs = listOf(stamp(helsinkiTs(2026, 1, 5, 8, 0), "SISÄÄN"))
        assertTrue(AchievementEvaluator.evaluate(logs, settings()).contains("first_stamp"))
    }

    @Test
    fun firstStampIgnoresFailedPunches() {
        val logs = listOf(stamp(helsinkiTs(2026, 1, 5, 8, 0), "SISÄÄN", isSuccess = false))
        assertFalse(AchievementEvaluator.evaluate(logs, settings()).contains("first_stamp"))
    }

    @Test
    fun centuryClubRequiresAtLeast100SuccessfulPunches() {
        val base = helsinkiTs(2026, 1, 1, 8, 0)
        val ninetyNine = (1..99).map { stamp(base + it * 60_000L, "SISÄÄN") }
        assertFalse(AchievementEvaluator.evaluate(ninetyNine, settings()).contains("century_club"))

        val hundred = ninetyNine + stamp(base + 100 * 60_000L, "SISÄÄN")
        assertTrue(AchievementEvaluator.evaluate(hundred, settings()).contains("century_club"))
    }

    @Test
    fun onARollUnlocksAfterFiveConsecutiveCompletedWorkdays() {
        assertTrue(AchievementEvaluator.evaluate(weekdaySessions(days = 5), settings()).contains("on_a_roll"))
    }

    @Test
    fun onARollDoesNotUnlockAfterFourDays() {
        assertFalse(AchievementEvaluator.evaluate(weekdaySessions(days = 4), settings()).contains("on_a_roll"))
    }

    @Test
    fun onARollStreakBreaksWhenAWorkdayIsMissed() {
        // Mon, Tue, Wed logged; Thu skipped; Fri logged; next Mon logged. Longest run is 3.
        val logs = mutableListOf<StampEntity>()
        logs += weekdaySessions(days = 3) // Mon-Wed
        val friday = helsinkiTsPlusDays(helsinkiTs(2026, 1, 5, 8, 0), 4)
        logs += stamp(friday, "SISÄÄN")
        logs += stamp(friday + 8 * 60 * 60 * 1000L, "ULOS")
        val nextMonday = helsinkiTsPlusDays(friday, 3)
        logs += stamp(nextMonday, "SISÄÄN")
        logs += stamp(nextMonday + 8 * 60 * 60 * 1000L, "ULOS")

        assertFalse(AchievementEvaluator.evaluate(logs, settings()).contains("on_a_roll"))
    }

    @Test
    fun earlyBirdUnlocksWithFiveConsecutiveEarlyClockIns() {
        val logs = weekdaySessions(days = 5, clockInHour = 7, clockInMinute = 0)
        assertTrue(AchievementEvaluator.evaluate(logs, settings()).contains("early_bird"))
    }

    @Test
    fun earlyBirdDoesNotUnlockWhenClockInIsAfterThreshold() {
        val logs = weekdaySessions(days = 5, clockInHour = 8, clockInMinute = 0)
        assertFalse(AchievementEvaluator.evaluate(logs, settings()).contains("early_bird"))
    }

    @Test
    fun clockworkToleratesSmallJitterButNotLargerDrift() {
        // Mon..Fri clock-ins drift 0, +1, -1, +2, 0 minutes off the Monday anchor - all within
        // the +-2 minute tolerance, so the run should still reach the target length.
        val base = helsinkiTs(2026, 1, 5, 8, 0)
        val minuteOffsets = listOf(0, 1, -1, 2, 0)
        val logs = mutableListOf<StampEntity>()
        minuteOffsets.forEachIndexed { i, offsetMin ->
            val inTs = helsinkiTsPlusDays(base, i) + offsetMin * 60_000L
            logs += stamp(inTs, "SISÄÄN")
            logs += stamp(inTs + 8 * 60 * 60 * 1000L, "ULOS")
        }
        assertTrue(AchievementEvaluator.evaluate(logs, settings()).contains("clockwork"))
    }

    @Test
    fun clockworkDoesNotUnlockWhenClockInDriftsEachDay() {
        val base = helsinkiTs(2026, 1, 5, 8, 0)
        val minuteOffsets = listOf(0, 10, 20, 30, 40)
        val logs = mutableListOf<StampEntity>()
        minuteOffsets.forEachIndexed { i, offsetMin ->
            val inTs = helsinkiTsPlusDays(base, i) + offsetMin * 60_000L
            logs += stamp(inTs, "SISÄÄN")
            logs += stamp(inTs + 8 * 60 * 60 * 1000L, "ULOS")
        }
        assertFalse(AchievementEvaluator.evaluate(logs, settings()).contains("clockwork"))
    }

    @Test
    fun overtimeHeroUnlocksWhenBalanceReachesTenHours() {
        val logs = listOf(
            stamp(helsinkiTs(2026, 1, 5, 8, 0), "SISÄÄN"),
            stamp(helsinkiTs(2026, 1, 5, 16, 0), "ULOS", balance = "+10:00")
        )
        assertTrue(AchievementEvaluator.evaluate(logs, settings()).contains("overtime_hero"))
    }

    @Test
    fun overtimeHeroFallsBackToLastServerBalanceInDemoMode() {
        // Demo-mode punches never populate StampEntity.balance, only settings.lastServerBalance.
        val logs = listOf(stamp(helsinkiTs(2026, 1, 5, 8, 0), "SISÄÄN"))
        val result = AchievementEvaluator.evaluate(logs, settings(lastServerBalance = "+10:30"))
        assertTrue(result.contains("overtime_hero"))
    }

    @Test
    fun deadEvenUnlocksOnExactZeroBalance() {
        val logs = listOf(
            stamp(helsinkiTs(2026, 1, 5, 8, 0), "SISÄÄN"),
            stamp(helsinkiTs(2026, 1, 5, 16, 0), "ULOS", balance = "+0:00")
        )
        assertTrue(AchievementEvaluator.evaluate(logs, settings()).contains("dead_even"))
    }

    @Test
    fun comebackUnlocksWhenBalanceFlipsFromNegativeToPositive() {
        val logs = listOf(
            stamp(helsinkiTs(2026, 1, 5, 8, 0), "SISÄÄN"),
            stamp(helsinkiTs(2026, 1, 5, 16, 0), "ULOS", balance = "−1:00"),
            stamp(helsinkiTs(2026, 1, 6, 8, 0), "SISÄÄN"),
            stamp(helsinkiTs(2026, 1, 6, 16, 0), "ULOS", balance = "+0:30")
        )
        assertTrue(AchievementEvaluator.evaluate(logs, settings()).contains("comeback"))
    }

    @Test
    fun comebackDoesNotUnlockWhenBalanceStaysNegative() {
        val logs = listOf(
            stamp(helsinkiTs(2026, 1, 5, 8, 0), "SISÄÄN"),
            stamp(helsinkiTs(2026, 1, 5, 16, 0), "ULOS", balance = "−1:00"),
            stamp(helsinkiTs(2026, 1, 6, 8, 0), "SISÄÄN"),
            stamp(helsinkiTs(2026, 1, 6, 16, 0), "ULOS", balance = "−0:30")
        )
        assertFalse(AchievementEvaluator.evaluate(logs, settings()).contains("comeback"))
    }

    @Test
    fun rightOnTheDotUnlocksWhenDailyMinutesAreCloseToTarget() {
        // Target is 7h30 worked; with the 30min lunch deduction that needs 8h on the clock.
        // Landing 3 minutes over that is within the +-5 minute tolerance.
        val logs = listOf(
            stamp(helsinkiTs(2026, 1, 5, 8, 0), "SISÄÄN"),
            stamp(helsinkiTs(2026, 1, 5, 16, 3), "ULOS")
        )
        assertTrue(AchievementEvaluator.evaluate(logs, settings()).contains("right_on_the_dot"))
    }

    @Test
    fun rightOnTheDotDoesNotUnlockWhenFarFromTarget() {
        val logs = listOf(
            stamp(helsinkiTs(2026, 1, 5, 8, 0), "SISÄÄN"),
            stamp(helsinkiTs(2026, 1, 5, 17, 0), "ULOS")
        )
        assertFalse(AchievementEvaluator.evaluate(logs, settings()).contains("right_on_the_dot"))
    }

    @Test
    fun nightOwlUnlocksOnLateClockOut() {
        val logs = listOf(
            stamp(helsinkiTs(2026, 1, 5, 8, 0), "SISÄÄN"),
            stamp(helsinkiTs(2026, 1, 5, 22, 15), "ULOS")
        )
        assertTrue(AchievementEvaluator.evaluate(logs, settings()).contains("night_owl"))
    }

    @Test
    fun nightOwlDoesNotUnlockForNormalClockOut() {
        val logs = listOf(
            stamp(helsinkiTs(2026, 1, 5, 8, 0), "SISÄÄN"),
            stamp(helsinkiTs(2026, 1, 5, 16, 0), "ULOS")
        )
        assertFalse(AchievementEvaluator.evaluate(logs, settings()).contains("night_owl"))
    }
}

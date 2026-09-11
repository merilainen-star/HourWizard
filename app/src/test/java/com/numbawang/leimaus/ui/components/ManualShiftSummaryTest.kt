package com.numbawang.leimaus.ui.components

import com.numbawang.leimaus.data.db.StampEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualShiftSummaryTest {
    @Test fun addsManualWorkWithoutApplyingDefaultLunchTwiceOrPairingLiveStamps() {
        val now = System.currentTimeMillis()
        val manual = StampEntity(timestamp = now, formattedTime = "", actionType = "TYÖVUORO",
            isSuccess = true, message = "", rawDetails = "MANUAL_SHIFT_MINUTES=480\n{}")
        val failed = manual.copy(isSuccess = false)
        val malformed = manual.copy(rawDetails = "{}")
        val minutes = calculateDailyWorkedMinutes(listOf(manual, failed, malformed), 60, 30)
        assertEquals(540, minutes.values.sum())
    }
}

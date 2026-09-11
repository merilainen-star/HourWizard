package com.numbawang.leimaus.approval

import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.numbawang.leimaus.ui.screens.ApprovalScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApprovalScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `home entry exists only for confirmed pending state`() {
        val state = mutableStateOf(ApprovalEntryState.Unknown)
        compose.setContent { MaterialTheme {
            com.numbawang.leimaus.ui.components.ApprovalEntryButton(state.value, {})
        } }
        for (value in ApprovalEntryState.entries) {
            compose.runOnIdle { state.value = value }
            val node = compose.onNodeWithText("Hyväksy edellisen kuukauden tunnit")
            if (value == ApprovalEntryState.Pending) node.assertExists() else node.assertDoesNotExist()
            compose.onNodeWithText("Hyväksytty").assertDoesNotExist()
        }
    }

    @Test fun `one button stays visible while pending and disappears only for approved state`() {
        val state = mutableStateOf(ApprovalUiState("2026-08", listOf(samplePeriod()), samplePeriod()))
        var clicks = 0
        compose.setContent { MaterialTheme {
            ApprovalScreen(state.value, {}, {}, { clicks++; state.value = state.value.copy(busy = true) })
        } }
        listOf("Toteuma", "Luetut", "Tase", "Työpäivät", "152:30", "151:45", "-0:45", "20").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onAllNodesWithText("Hyväksy").assertCountEquals(1)
        compose.onNodeWithText("Hyväksy").performScrollTo().performClick()
        compose.onNodeWithText("Hyväksy").assertIsNotEnabled()
        assertEquals(1, clicks)
        compose.runOnIdle { state.value = state.value.copy(busy = false, error = "Ei yhteyttä", requiresRefresh = true) }
        compose.onNodeWithText("Hyväksy").assertExists().assertIsNotEnabled()
        compose.onNodeWithText("Hyväksytty").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(selected = samplePeriod().copy(approved = true), error = null) }
        compose.onNodeWithText("Hyväksy").assertDoesNotExist()
        compose.onNodeWithText("Hyväksytty").assertExists()
    }
}

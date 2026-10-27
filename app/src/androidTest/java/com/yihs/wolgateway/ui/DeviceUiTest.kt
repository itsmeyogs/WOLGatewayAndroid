package com.yihs.wolgateway.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yihs.wolgateway.domain.model.Device
import com.yihs.wolgateway.domain.model.DeviceStatus
import com.yihs.wolgateway.ui.component.DeviceListItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun deviceRowShowsOperationalMetadataAndWakeAction() {
        var wakeClicked = false
        composeRule.setContent {
            DeviceListItem(
                device = Device(
                    name = "Office PC",
                    ipAddress = "192.168.1.20",
                    macAddress = "AA:BB:CC:DD:EE:FF",
                    status = DeviceStatus.ONLINE,
                ),
                onWake = { wakeClicked = true },
                onOpen = {},
            )
        }

        composeRule.onNodeWithText("Office PC").assertExists()
        composeRule.onNodeWithText("ONLINE").assertExists()
        composeRule.onNodeWithText("192.168.1.20").assertExists()
        composeRule.onNodeWithContentDescription("Device Office PC").assertTextContains("Office PC")
        composeRule.onNodeWithText("Wake").performClick()
        assert(wakeClicked)
    }
}

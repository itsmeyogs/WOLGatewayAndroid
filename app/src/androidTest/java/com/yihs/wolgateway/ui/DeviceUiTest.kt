package com.yihs.wolgateway.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
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
                    status = DeviceStatus.UNKNOWN,
                ),
                onWake = { wakeClicked = true },
                onOpen = {},
            )
        }

        composeRule.onNodeWithText("Office PC").assertExists()
        composeRule.onNodeWithText("UNKNOWN").assertExists()
        composeRule.onNodeWithText("192.168.1.20").assertExists()
        composeRule.onNodeWithContentDescription("Device Office PC").assertTextContains("Office PC")
        composeRule.onNodeWithText("Wake").performClick()
        assert(wakeClicked)
    }

    @Test
    fun onlineDeviceHidesWakeAction() {
        composeRule.setContent {
            DeviceListItem(
                device = Device(
                    name = "Online PC",
                    ipAddress = "192.168.1.21",
                    macAddress = "11:22:33:44:55:66",
                    status = DeviceStatus.ONLINE,
                ),
                onWake = {},
                onOpen = {},
            )
        }

        composeRule.onNodeWithText("Online PC").assertExists()
        composeRule.onAllNodesWithText("Wake").assertCountEquals(0)
    }
}

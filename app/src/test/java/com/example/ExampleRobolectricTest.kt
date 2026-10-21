package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.BtConnectedChassisSurface
import com.example.ui.theme.BtElectricBlue
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.LocalRemotePalette
import com.example.ui.theme.MiOrange
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Mi TV Remote", appName)
    }

    @Test
    fun `hid descriptor and reports are valid`() {
        assertTrue(HidDescriptor.COMBINED_HID_DESCRIPTOR.isNotEmpty())
        val dpadUpReport = HidDescriptor.buildKeyboardPressReport(HidDescriptor.KEY_DPAD_UP)
        assertEquals(8, dpadUpReport.size)
        assertEquals(HidDescriptor.KEY_DPAD_UP.toByte(), dpadUpReport[2])

        val homeConsumerReport = HidDescriptor.buildConsumerPressReport(HidDescriptor.CONSUMER_HOME)
        assertEquals(2, homeConsumerReport.size)
        assertEquals(0x23.toByte(), homeConsumerReport[0])
        assertEquals(0x02.toByte(), homeConsumerReport[1])

        val wakeSystemReport = HidDescriptor.buildSystemPressReport(HidDescriptor.SYSTEM_WAKE_UP)
        assertEquals(1, wakeSystemReport.size)
        assertEquals(0x04.toByte(), wakeSystemReport[0])
    }

    @Test
    fun `ir blaster nec pattern generation is valid`() {
        assertEquals(38000, IrBlasterHelper.CARRIER_FREQUENCY_HZ)
        assertTrue(IrBlasterHelper.MI_TV_POWER_NEC_PATTERN.size >= 68)
        assertEquals(9000, IrBlasterHelper.MI_TV_POWER_NEC_PATTERN[0])
        assertEquals(4500, IrBlasterHelper.MI_TV_POWER_NEC_PATTERN[1])
    }

    @Test
    fun `nav pad mode selector toggles between dpad and joystick`() {
        var selectedMode = NavPadMode.DPAD

        composeTestRule.setContent {
            MyApplicationTheme {
                NavPadModeSelector(
                    currentMode = selectedMode,
                    onSelectMode = { selectedMode = it }
                )
            }
        }

        composeTestRule.onNodeWithTag("nav_mode_selector").assertExists()
        composeTestRule.onNodeWithTag("mode_joystick_button").performClick()
        assertEquals(NavPadMode.JOYSTICK, selectedMode)
    }

    @Test
    fun `virtual joystick pad triggers direction and tap ok`() {
        var stepCount = 0
        var okTapped = false

        composeTestRule.setContent {
            MyApplicationTheme {
                VirtualJoystickPad(
                    onDirectionStep = { _, _, _, _ -> stepCount++ },
                    onTapOk = { okTapped = true }
                )
            }
        }

        val joystickNode = composeTestRule.onNodeWithTag("virtual_joystick_pad")
        joystickNode.assertExists()

        // Drag right to trigger directional threshold
        joystickNode.performTouchInput {
            swipeRight()
        }
        assertTrue("Expected direction step from joystick drag", stepCount > 0)

        // Tap in center to trigger OK
        joystickNode.performClick()
        assertTrue("Expected tap to trigger OK", okTapped)
    }

    @Test
    fun `remote screen renders without scrolling and dpad arrows work`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val btController = BluetoothRemoteController(context)
        val irHelper = IrBlasterHelper(context)
        val voiceHelper = VoiceInputHelper(context)

        composeTestRule.setContent {
            RemoteScreen(
                btController = btController,
                irHelper = irHelper,
                voiceHelper = voiceHelper,
                hasBluetoothPermissions = true,
                hasAudioPermission = true,
                onRequestPermissions = {},
                onRequestAudioPermission = {}
            )
        }

        composeTestRule.waitForIdle()

        // Verify root container and chassis exist (no scroll required)
        composeTestRule.onNodeWithTag("remote_root_container").assertExists()
        composeTestRule.onNodeWithTag("remote_chassis_card").assertExists()

        // Verify D-Pad arrow buttons exist and can be clicked
        composeTestRule.onNodeWithTag("dpad_up_button").performClick()
        composeTestRule.onNodeWithTag("dpad_down_button").performClick()
        composeTestRule.onNodeWithTag("dpad_left_button").performClick()
        composeTestRule.onNodeWithTag("dpad_right_button").performClick()
        composeTestRule.onNodeWithTag("dpad_ok_button").performClick()

        // Switch to Virtual Joystick mode
        composeTestRule.onNodeWithTag("mode_joystick_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("virtual_joystick_pad").assertExists()

        // Verify Direct Connect 1-tap button exists and activates Direct Pairing Beacon
        val directConnectBtn = composeTestRule.onNodeWithTag("direct_connect_button")
        directConnectBtn.assertExists()
        directConnectBtn.performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("direct_pairing_overlay").assertExists()
    }

    @Test
    fun `dynamic theme shifts from charcoal to bluetooth blue glow when connected`() {
        var observedPrimary: Color = Color.Unspecified
        var observedChassis: Color = Color.Unspecified
        var observedBtGlowActive = false

        val connectionState = mutableStateOf<HidConnectionState>(
            HidConnectionState.Disconnected
        )

        composeTestRule.setContent {
            MyApplicationTheme(connectionState = connectionState.value) {
                observedPrimary = MaterialTheme.colorScheme.primary
                observedChassis = LocalRemotePalette.current.chassisSurface
                observedBtGlowActive = LocalRemotePalette.current.isBluetoothGlowActive
            }
        }

        composeTestRule.waitForIdle()
        assertFalse(observedBtGlowActive)
        assertEquals(MiOrange, observedPrimary)
        assertEquals(CharcoalSurface, observedChassis)

        // Transition to Connected state and verify dynamic blue glow palette
        connectionState.value = HidConnectionState.Connected("Mi TV 4S", "AA:BB:CC:DD:EE:FF")
        composeTestRule.mainClock.advanceTimeBy(1000L)
        composeTestRule.waitForIdle()
        assertTrue(observedBtGlowActive)
        assertEquals(BtElectricBlue, observedPrimary)
        assertEquals(BtConnectedChassisSurface, observedChassis)
    }

    @Test
    fun `voice search dialog renders and sends transcribed query`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voiceHelper = VoiceInputHelper(context)
        var sentQuery = ""
        var sentEnter = false

        composeTestRule.setContent {
            MyApplicationTheme {
                VoiceSearchDialog(
                    voiceHelper = voiceHelper,
                    isBtConnected = true,
                    onDismiss = {},
                    onSendQuery = { query, enter ->
                        sentQuery = query
                        sentEnter = enter
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("voice_search_dialog").assertExists()
        composeTestRule.onNodeWithTag("voice_mic_orb").assertExists()

        // Simulate voice recognition success
        voiceHelper.simulateResultForTesting("Stranger Things")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("voice_send_enter_button").performClick()
        assertEquals("Stranger Things", sentQuery)
        assertTrue(sentEnter)
    }
}

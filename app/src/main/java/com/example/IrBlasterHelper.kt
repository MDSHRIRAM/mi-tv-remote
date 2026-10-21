package com.example

import android.content.Context
import android.hardware.ConsumerIrManager
import android.util.Log

/**
 * Offline Infrared (IR) Blaster controller for Xiaomi Mi TV.
 *
 * Many Xiaomi Mi TVs power down their Bluetooth controller during deep standby.
 * When the phone has a built-in IR emitter (common on Xiaomi, Redmi, POCO, Huawei, Honor, TCL phones),
 * this helper transmits 38kHz NEC / Xiaomi Mi TV pulse timing arrays to wake the TV from deep sleep
 * and control core functions without needing Bluetooth or Wi-Fi.
 */
class IrBlasterHelper(context: Context) {

    companion object {
        private const val TAG = "IrBlasterHelper"
        const val CARRIER_FREQUENCY_HZ = 38000 // Standard 38kHz IR carrier for Xiaomi Mi TV

        // NEC Protocol Timing Constants (in microseconds)
        private const val NEC_HDR_MARK = 9000
        private const val NEC_HDR_SPACE = 4500
        private const val NEC_BIT_MARK = 560
        private const val NEC_ONE_SPACE = 1690
        private const val NEC_ZERO_SPACE = 560
        private const val NEC_TRAIL_MARK = 560
        private const val NEC_FRAME_GAP = 40000

        /**
         * Builds a 32-bit NEC IR pulse pattern (in microseconds) from a 32-bit hex command.
         */
        fun buildNecPattern(code: Long): IntArray {
            val pulses = IntArray(2 + 32 * 2 + 2)
            var index = 0
            pulses[index++] = NEC_HDR_MARK
            pulses[index++] = NEC_HDR_SPACE

            for (bit in 31 downTo 0) {
                pulses[index++] = NEC_BIT_MARK
                val isOne = ((code shr bit) and 1L) == 1L
                pulses[index++] = if (isOne) NEC_ONE_SPACE else NEC_ZERO_SPACE
            }

            pulses[index++] = NEC_TRAIL_MARK
            pulses[index] = NEC_FRAME_GAP
            return pulses
        }

        // Standard Xiaomi Mi TV NEC & PatchWall IR pulse timing arrays (38kHz)
        // Primary Mi TV Power Toggle (0x20DF10EF / Xiaomi TV NEC standard)
        val MI_TV_POWER_NEC_PATTERN: IntArray = intArrayOf(
            9000, 4500,
            560, 560, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560,
            560, 1690, 560, 1690, 560, 560, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 1690,
            560, 560, 560, 560, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560,
            560, 1690, 560, 1690, 560, 1690, 560, 560, 560, 1690, 560, 1690, 560, 1690, 560, 1690,
            560, 40000
        )

        // Xiaomi Custom Short-Pulse IR Protocol for Mi TV / Mi Box Power (Wake from Deep Sleep)
        val MI_TV_POWER_CUSTOM_PATTERN: IntArray = intArrayOf(
            1060, 880,
            550, 880, 550, 570, 550, 570, 550, 570, 550, 1180, 550, 570,
            550, 570, 550, 570, 550, 1480, 550, 1480, 550, 1180, 550, 30000
        )

        val MI_TV_VOL_UP_PATTERN: IntArray = buildNecPattern(0x20DF40BFL)
        val MI_TV_VOL_DOWN_PATTERN: IntArray = buildNecPattern(0x20DFC03FL)
        val MI_TV_MUTE_PATTERN: IntArray = buildNecPattern(0x20DF906FL)
        val MI_TV_CH_UP_PATTERN: IntArray = buildNecPattern(0x20DF00FFL)
        val MI_TV_CH_DOWN_PATTERN: IntArray = buildNecPattern(0x20DF807FL)
        val MI_TV_DPAD_UP_PATTERN: IntArray = buildNecPattern(0x20DF02FDL)
        val MI_TV_DPAD_DOWN_PATTERN: IntArray = buildNecPattern(0x20DF827DL)
        val MI_TV_DPAD_LEFT_PATTERN: IntArray = buildNecPattern(0x20DFE01FL)
        val MI_TV_DPAD_RIGHT_PATTERN: IntArray = buildNecPattern(0x20DF609FL)
        val MI_TV_OK_PATTERN: IntArray = buildNecPattern(0x20DF22DDL)
        val MI_TV_BACK_PATTERN: IntArray = buildNecPattern(0x20DF14EBL)
        val MI_TV_HOME_PATTERN: IntArray = buildNecPattern(0x20DF3EC1L)
        val MI_TV_MENU_PATTERN: IntArray = buildNecPattern(0x20DFC23DL)
        val MI_TV_SOURCE_PATTERN: IntArray = buildNecPattern(0x20DFD02FL)
    }

    enum class IrCommand(val label: String, val pattern: IntArray) {
        POWER("IR Power (38kHz NEC + Mi Wake)", MI_TV_POWER_NEC_PATTERN),
        VOL_UP("IR Volume Up (38kHz)", MI_TV_VOL_UP_PATTERN),
        VOL_DOWN("IR Volume Down (38kHz)", MI_TV_VOL_DOWN_PATTERN),
        MUTE("IR Mute (38kHz)", MI_TV_MUTE_PATTERN),
        CH_UP("IR Channel Up (38kHz)", MI_TV_CH_UP_PATTERN),
        CH_DOWN("IR Channel Down (38kHz)", MI_TV_CH_DOWN_PATTERN),
        DPAD_UP("IR Up (38kHz)", MI_TV_DPAD_UP_PATTERN),
        DPAD_DOWN("IR Down (38kHz)", MI_TV_DPAD_DOWN_PATTERN),
        DPAD_LEFT("IR Left (38kHz)", MI_TV_DPAD_LEFT_PATTERN),
        DPAD_RIGHT("IR Right (38kHz)", MI_TV_DPAD_RIGHT_PATTERN),
        OK("IR OK/Enter (38kHz)", MI_TV_OK_PATTERN),
        BACK("IR Back (38kHz)", MI_TV_BACK_PATTERN),
        HOME("IR Home (38kHz)", MI_TV_HOME_PATTERN),
        MENU("IR Menu/PatchWall (38kHz)", MI_TV_MENU_PATTERN),
        SOURCE("IR Input/Source (38kHz)", MI_TV_SOURCE_PATTERN)
    }

    private val irManager: ConsumerIrManager? = try {
        context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
    } catch (e: Exception) {
        Log.w(TAG, "ConsumerIrManager not available on this device", e)
        null
    }

    /**
     * Checks whether the current Android phone has a hardware Infrared emitter.
     */
    fun hasIrEmitter(): Boolean {
        return try {
            irManager?.hasIrEmitter() == true
        } catch (e: Exception) {
            Log.w(TAG, "Error checking IR emitter availability", e)
            false
        }
    }

    /**
     * Transmits both the Xiaomi Mi TV 38kHz NEC Power-On frame and Xiaomi custom wake frame
     * so any Xiaomi Mi TV series wakes from deep standby.
     */
    fun transmitPowerWake(): Boolean {
        if (!hasIrEmitter()) return false
        return try {
            irManager?.transmit(CARRIER_FREQUENCY_HZ, MI_TV_POWER_NEC_PATTERN)
            irManager?.transmit(CARRIER_FREQUENCY_HZ, MI_TV_POWER_CUSTOM_PATTERN)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transmit IR Power signal", e)
            false
        }
    }

    fun transmitVolumeUp(): Boolean = transmitCommand(IrCommand.VOL_UP)

    fun transmitVolumeDown(): Boolean = transmitCommand(IrCommand.VOL_DOWN)

    fun transmitMute(): Boolean = transmitCommand(IrCommand.MUTE)

    /**
     * Transmits a specific Xiaomi Mi TV IR command at 38kHz carrier frequency.
     */
    fun transmitCommand(command: IrCommand): Boolean {
        if (command == IrCommand.POWER) {
            return transmitPowerWake()
        }
        if (!hasIrEmitter()) return false
        return try {
            irManager?.transmit(CARRIER_FREQUENCY_HZ, command.pattern)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to transmit IR command: ${command.name}", e)
            false
        }
    }
}

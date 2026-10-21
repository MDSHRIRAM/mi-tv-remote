package com.example

/**
 * USB HID Report Descriptor and Key Codes for Xiaomi Mi TV (Android TV / Google TV / PatchWall).
 *
 * Implements a valid combined HID descriptor supporting:
 * - Report ID 1: Standard Keyboard / Generic Desktop Key Codes (Usage Page 0x07)
 *   for D-Pad navigation, OK/Enter, Back/Escape, and full alphanumeric text input.
 * - Report ID 2: Consumer Control 16-bit Usage Codes (Usage Page 0x0C)
 *   for Power, Home, Back, Menu/PatchWall, Volume Up/Down, Mute, Channel Up/Down, Media & Source.
 * - Report ID 3: System Control (Usage Page 0x01, Usage 0x80)
 *   for System Wake Up (0x83), System Sleep (0x82), and System Power Down (0x81).
 */
object HidDescriptor {

    const val REPORT_ID_KEYBOARD: Int = 1
    const val REPORT_ID_CONSUMER: Int = 2
    const val REPORT_ID_SYSTEM: Int = 3

    // --- Report ID 1: USB HID Keyboard / Keypad Page (0x07) Key Codes ---
    const val KEY_ENTER_OK: Int = 0x28       // Keyboard Return (ENTER) -> Android TV DPAD_CENTER / ENTER
    const val KEY_ESCAPE_BACK: Int = 0x29    // Keyboard Escape -> Android TV BACK
    const val KEY_BACKSPACE: Int = 0x2A      // Keyboard Delete (Backspace)
    const val KEY_TAB: Int = 0x2B            // Keyboard Tab
    const val KEY_SPACE: Int = 0x2C          // Keyboard Spacebar
    const val KEY_DPAD_RIGHT: Int = 0x4F     // Keyboard RightArrow
    const val KEY_DPAD_LEFT: Int = 0x50      // Keyboard LeftArrow
    const val KEY_DPAD_DOWN: Int = 0x51      // Keyboard DownArrow
    const val KEY_DPAD_UP: Int = 0x52        // Keyboard UpArrow
    const val KEY_CONTEXT_MENU: Int = 0x65   // Keyboard Application (Context Menu on Android TV)
    const val KEY_TV_INPUT_F12: Int = 0x45   // F12 / Source shortcut fallback

    // Keyboard Modifiers (Bitmask in byte[0] of Report ID 1)
    const val MODIFIER_NONE: Int = 0x00
    const val MODIFIER_LEFT_CTRL: Int = 0x01
    const val MODIFIER_LEFT_SHIFT: Int = 0x02
    const val MODIFIER_LEFT_ALT: Int = 0x04
    const val MODIFIER_LEFT_GUI: Int = 0x08

    // --- Report ID 2: USB HID Consumer Page (0x0C) 16-bit Usage Codes ---
    const val CONSUMER_POWER: Int = 0x0030          // Power Toggle
    const val CONSUMER_SLEEP: Int = 0x0032          // Sleep Mode
    const val CONSUMER_MENU: Int = 0x0040           // Menu / PatchWall Options
    const val CONSUMER_SYSTEM_WAKE: Int = 0x0083    // Consumer Wake / Recall Last
    const val CONSUMER_MEDIA_TV: Int = 0x0089       // Media Select TV / Live TV
    const val CONSUMER_PROGRAM_GUIDE: Int = 0x008D  // Program Guide / PatchWall Launcher
    const val CONSUMER_CHANNEL_UP: Int = 0x009C     // Channel Increment
    const val CONSUMER_CHANNEL_DOWN: Int = 0x009D   // Channel Decrement
    const val CONSUMER_PLAY_PAUSE: Int = 0x00CD     // Play / Pause
    const val CONSUMER_MUTE: Int = 0x00E2           // Mute
    const val CONSUMER_VOLUME_UP: Int = 0x00E9      // Volume Increment
    const val CONSUMER_VOLUME_DOWN: Int = 0x00EA    // Volume Decrement
    const val CONSUMER_INPUT_SOURCE: Int = 0x0182   // Media Select / TV Input Source (Bookmark/Input)
    const val CONSUMER_HOME: Int = 0x0223           // AC Home (Android TV Home Screen)
    const val CONSUMER_BACK: Int = 0x0224           // AC Back (Android TV Back)

    // --- Report ID 3: Generic Desktop System Control (0x01 / 0x80) ---
    const val SYSTEM_POWER_DOWN: Int = 0x0081
    const val SYSTEM_SLEEP: Int = 0x0082
    const val SYSTEM_WAKE_UP: Int = 0x0083

    /**
     * Combined USB HID Report Descriptor supporting:
     * 1. Report ID 1: Standard 8-byte Keyboard Report (1 modifier + 1 reserved + 6 keycodes)
     * 2. Report ID 2: 2-byte 16-bit Consumer Control Report (0x0000..0x03FF)
     * 3. Report ID 3: 1-byte System Control Report (0x81 Power Down, 0x82 Sleep, 0x83 Wake Up)
     */
    val COMBINED_HID_DESCRIPTOR: ByteArray = byteArrayOf(
        // =====================================================================
        // Report ID 1: Keyboard / Keypad (Generic Desktop 0x01, Keyboard 0x06)
        // =====================================================================
        0x05.toByte(), 0x01.toByte(),       // Usage Page (Generic Desktop)
        0x09.toByte(), 0x06.toByte(),       // Usage (Keyboard)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), 0x01.toByte(),       //   Report ID (1)
        // Modifier byte (8 bits: Left Ctrl .. Right GUI)
        0x05.toByte(), 0x07.toByte(),       //   Usage Page (Key Codes)
        0x19.toByte(), 0xE0.toByte(),       //   Usage Minimum (224 - Left Control)
        0x29.toByte(), 0xE7.toByte(),       //   Usage Maximum (231 - Right GUI)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),       //   Report Size (1)
        0x95.toByte(), 0x08.toByte(),       //   Report Count (8)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data, Variable, Absolute)
        // Reserved byte (1 byte)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x81.toByte(), 0x01.toByte(),       //   Input (Constant, Array, Absolute)
        // 6-key rollover array (6 bytes)
        0x95.toByte(), 0x06.toByte(),       //   Report Count (6)
        0x75.toByte(), 0x08.toByte(),       //   Report Size (8)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x65.toByte(),       //   Logical Maximum (101)
        0x05.toByte(), 0x07.toByte(),       //   Usage Page (Key Codes)
        0x19.toByte(), 0x00.toByte(),       //   Usage Minimum (0)
        0x29.toByte(), 0x65.toByte(),       //   Usage Maximum (101)
        0x81.toByte(), 0x00.toByte(),       //   Input (Data, Array, Absolute)
        0xC0.toByte(),                      // End Collection (Keyboard)

        // =====================================================================
        // Report ID 2: Consumer Control (Consumer Page 0x0C, Usage 0x01)
        // =====================================================================
        0x05.toByte(), 0x0C.toByte(),       // Usage Page (Consumer)
        0x09.toByte(), 0x01.toByte(),       // Usage (Consumer Control)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), 0x02.toByte(),       //   Report ID (2)
        0x19.toByte(), 0x00.toByte(),       //   Usage Minimum (0x0000)
        0x2A.toByte(), 0xFF.toByte(), 0x03.toByte(), // Usage Maximum (0x03FF)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x26.toByte(), 0xFF.toByte(), 0x03.toByte(), // Logical Maximum (0x03FF)
        0x95.toByte(), 0x01.toByte(),       //   Report Count (1)
        0x75.toByte(), 0x10.toByte(),       //   Report Size (16)
        0x81.toByte(), 0x00.toByte(),       //   Input (Data, Array, Absolute)
        0xC0.toByte(),                      // End Collection (Consumer Control)

        // =====================================================================
        // Report ID 3: System Control (Generic Desktop 0x01, System Control 0x80)
        // =====================================================================
        0x05.toByte(), 0x01.toByte(),       // Usage Page (Generic Desktop)
        0x09.toByte(), 0x80.toByte(),       // Usage (System Control)
        0xA1.toByte(), 0x01.toByte(),       // Collection (Application)
        0x85.toByte(), 0x03.toByte(),       //   Report ID (3)
        0x19.toByte(), 0x81.toByte(),       //   Usage Minimum (0x81 - System Power Down)
        0x29.toByte(), 0x83.toByte(),       //   Usage Maximum (0x83 - System Wake Up)
        0x15.toByte(), 0x00.toByte(),       //   Logical Minimum (0)
        0x25.toByte(), 0x01.toByte(),       //   Logical Maximum (1)
        0x75.toByte(), 0x01.toByte(),       //   Report Size (1)
        0x95.toByte(), 0x03.toByte(),       //   Report Count (3)
        0x81.toByte(), 0x02.toByte(),       //   Input (Data, Variable, Absolute)
        0x95.toByte(), 0x05.toByte(),       //   Report Count (5 - padding bits)
        0x81.toByte(), 0x01.toByte(),       //   Input (Constant)
        0xC0.toByte()                       // End Collection (System Control)
    )

    /**
     * Constructs an 8-byte Keyboard press report for Report ID 1.
     */
    fun buildKeyboardPressReport(keyCode: Int, modifier: Int = MODIFIER_NONE): ByteArray {
        return byteArrayOf(
            modifier.toByte(),
            0x00.toByte(),
            keyCode.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte()
        )
    }

    /**
     * Constructs an 8-byte Keyboard release report (all zeros) for Report ID 1.
     */
    fun buildKeyboardReleaseReport(): ByteArray {
        return ByteArray(8) { 0x00.toByte() }
    }

    /**
     * Constructs a 2-byte little-endian Consumer Control press report for Report ID 2.
     */
    fun buildConsumerPressReport(usageCode: Int): ByteArray {
        val lowByte = (usageCode and 0xFF).toByte()
        val highByte = ((usageCode shr 8) and 0xFF).toByte()
        return byteArrayOf(lowByte, highByte)
    }

    /**
     * Constructs a 2-byte Consumer Control release report (0x0000) for Report ID 2.
     */
    fun buildConsumerReleaseReport(): ByteArray {
        return byteArrayOf(0x00.toByte(), 0x00.toByte())
    }

    /**
     * Constructs a 1-byte System Control press report for Report ID 3.
     * Bit 0 = 0x81 (System Power Down), Bit 1 = 0x82 (System Sleep), Bit 2 = 0x83 (System Wake Up).
     */
    fun buildSystemPressReport(systemUsageCode: Int): ByteArray {
        val bitMask = when (systemUsageCode) {
            SYSTEM_POWER_DOWN -> 0b0000_0001
            SYSTEM_SLEEP -> 0b0000_0010
            SYSTEM_WAKE_UP -> 0b0000_0100
            else -> 0b0000_0100
        }
        return byteArrayOf(bitMask.toByte())
    }

    /**
     * Constructs a 1-byte System Control release report for Report ID 3.
     */
    fun buildSystemReleaseReport(): ByteArray {
        return byteArrayOf(0x00.toByte())
    }

    /**
     * Generic helper to construct a key-down report for any reportId.
     */
    fun buildPressReport(reportId: Int, keyCode: Int, modifier: Int = MODIFIER_NONE): ByteArray {
        return when (reportId) {
            REPORT_ID_KEYBOARD -> buildKeyboardPressReport(keyCode, modifier)
            REPORT_ID_CONSUMER -> buildConsumerPressReport(keyCode)
            REPORT_ID_SYSTEM -> buildSystemPressReport(keyCode)
            else -> buildKeyboardPressReport(keyCode, modifier)
        }
    }

    /**
     * Generic helper to construct a key-up release report for any reportId.
     */
    fun buildReleaseReport(reportId: Int): ByteArray {
        return when (reportId) {
            REPORT_ID_KEYBOARD -> buildKeyboardReleaseReport()
            REPORT_ID_CONSUMER -> buildConsumerReleaseReport()
            REPORT_ID_SYSTEM -> buildSystemReleaseReport()
            else -> buildKeyboardReleaseReport()
        }
    }

    /**
     * Maps a printable ASCII character to a (modifier, hidKeyCode) pair for Report ID 1.
     */
    fun charToHidKey(ch: Char): Pair<Int, Int>? {
        return when (ch) {
            in 'a'..'z' -> MODIFIER_NONE to (0x04 + (ch - 'a'))
            in 'A'..'Z' -> MODIFIER_LEFT_SHIFT to (0x04 + (ch - 'A'))
            in '1'..'9' -> MODIFIER_NONE to (0x1E + (ch - '1'))
            '0' -> MODIFIER_NONE to 0x27
            '\n', '\r' -> MODIFIER_NONE to KEY_ENTER_OK
            ' ' -> MODIFIER_NONE to KEY_SPACE
            '\t' -> MODIFIER_NONE to KEY_TAB
            '!' -> MODIFIER_LEFT_SHIFT to 0x1E
            '@' -> MODIFIER_LEFT_SHIFT to 0x1F
            '#' -> MODIFIER_LEFT_SHIFT to 0x20
            '$' -> MODIFIER_LEFT_SHIFT to 0x21
            '%' -> MODIFIER_LEFT_SHIFT to 0x22
            '^' -> MODIFIER_LEFT_SHIFT to 0x23
            '&' -> MODIFIER_LEFT_SHIFT to 0x24
            '*' -> MODIFIER_LEFT_SHIFT to 0x25
            '(' -> MODIFIER_LEFT_SHIFT to 0x26
            ')' -> MODIFIER_LEFT_SHIFT to 0x27
            '-' -> MODIFIER_NONE to 0x2D
            '_' -> MODIFIER_LEFT_SHIFT to 0x2D
            '=' -> MODIFIER_NONE to 0x2E
            '+' -> MODIFIER_LEFT_SHIFT to 0x2E
            '[' -> MODIFIER_NONE to 0x2F
            '{' -> MODIFIER_LEFT_SHIFT to 0x2F
            ']' -> MODIFIER_NONE to 0x30
            '}' -> MODIFIER_LEFT_SHIFT to 0x30
            '\\' -> MODIFIER_NONE to 0x31
            '|' -> MODIFIER_LEFT_SHIFT to 0x31
            ';' -> MODIFIER_NONE to 0x33
            ':' -> MODIFIER_LEFT_SHIFT to 0x33
            '\'' -> MODIFIER_NONE to 0x34
            '"' -> MODIFIER_LEFT_SHIFT to 0x34
            '`' -> MODIFIER_NONE to 0x35
            '~' -> MODIFIER_LEFT_SHIFT to 0x35
            ',' -> MODIFIER_NONE to 0x36
            '<' -> MODIFIER_LEFT_SHIFT to 0x36
            '.' -> MODIFIER_NONE to 0x37
            '>' -> MODIFIER_LEFT_SHIFT to 0x37
            '/' -> MODIFIER_NONE to 0x38
            '?' -> MODIFIER_LEFT_SHIFT to 0x38
            else -> null
        }
    }
}

package com.inexplicable.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ColorUtilTest {
    @Test fun hexRoundTrip() {
        assertEquals("#3DDCFF", colorToHex(DEFAULT_PRIMARY))
        assertEquals(DEFAULT_PRIMARY, parseHex("#3ddcff"))
        assertEquals(0xFFFF0000L, parseHex("FF0000"))
        assertNull(parseHex("#12345"))
        assertNull(parseHex("#GGGGGG"))
    }

    @Test fun hsvRoundTrip() {
        for (c in listOf(0xFFFF0000L, 0xFF00FF00L, 0xFF3DDCFFL, 0xFFFFC857L, 0xFF808080L, 0xFF000000L)) {
            val hsv = rgbToHsv(c)
            assertEquals(c, hsvToRgb(hsv.h, hsv.s, hsv.v), colorToHex(c))
        }
    }

    @Test fun settingsKeepNewFields() {
        val store = object : KeyValueStore {
            val m = mutableMapOf<String, Any>()
            override fun getString(key: String) = m[key] as String?
            override fun getBoolean(key: String, default: Boolean) = m[key] as Boolean? ?: default
            override fun getLong(key: String, default: Long) = m[key] as Long? ?: default
            override fun putString(key: String, value: String) { m[key] = value }
            override fun putBoolean(key: String, value: Boolean) { m[key] = value }
            override fun putLong(key: String, value: Long) { m[key] = value }
        }
        val s = AppSettings(mode = MODE_DECK3D, hudLevel = 3, openMs = 2000, colorPrimary = 0xFFFF0000L)
        SettingsStore(store).save(s)
        assertEquals(s, SettingsStore(store).load())
    }
}

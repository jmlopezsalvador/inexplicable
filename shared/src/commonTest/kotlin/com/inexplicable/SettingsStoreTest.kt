package com.inexplicable

import com.inexplicable.effect.Texts
import com.inexplicable.ui.AppSettings
import com.inexplicable.ui.KeyValueStore
import com.inexplicable.ui.SettingsStore
import com.inexplicable.ui.moved
import com.inexplicable.ui.removedAt
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStoreTest {
    private class MemoryStore : KeyValueStore {
        val m = HashMap<String, Any>()
        override fun getString(key: String) = m[key] as String?
        override fun getBoolean(key: String, default: Boolean) = m[key] as Boolean? ?: default
        override fun getLong(key: String, default: Long) = m[key] as Long? ?: default
        override fun putString(key: String, value: String) { m[key] = value }
        override fun putBoolean(key: String, value: Boolean) { m[key] = value }
        override fun putLong(key: String, value: Long) { m[key] = value }
    }

    @Test fun defaultsWhenEmpty() {
        val s = SettingsStore(MemoryStore()).load()
        assertEquals(AppSettings(), s)
        assertEquals(24, s.faceDown)
    }

    @Test fun savesAndLoadsSettings() {
        val store = SettingsStore(MemoryStore())
        val s = AppSettings(30, 15, "5 de tréboles", listOf("Uno", "Dos, con coma", "Tres"), 4000, 300)
        store.save(s)
        assertEquals(s, store.load())
    }

    @Test fun blankPhasesAreDropped() {
        val store = SettingsStore(MemoryStore())
        store.save(AppSettings(customPhases = listOf("A", "  ", "B")))
        assertEquals(listOf("A", "B"), store.load().customPhases)
    }

    @Test fun effectivePhasesUseDefaultsOrCustom() {
        val t = Texts.forLanguage("es", AppSettings(faceDown = 20).params())
        assertEquals("Hay 20 cartas cara abajo.", AppSettings(faceDown = 20).effectivePhases(t)[0].text)
        assertEquals(listOf("X"), AppSettings(customPhases = listOf("X")).effectivePhases(t).map { it.text })
    }

    @Test fun listHelpers() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(0, 1))
        assertEquals(listOf("a", "b", "c"), listOf("a", "b", "c").moved(0, -1))
        assertEquals(listOf("a", "c"), listOf("a", "b", "c").removedAt(1))
    }
}

@file:OptIn(ExperimentalForeignApi::class)

package com.inexplicable.ui

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults

/** iOS: sin comportamiento especial (la barra de estado y el indicador de inicio se ocultan en ContentView.swift). */
@Composable
actual fun SystemBarsEffect() {}

class UserDefaultsStore : KeyValueStore {
    private val d = NSUserDefaults.standardUserDefaults
    override fun getString(key: String): String? = d.stringForKey(key)
    override fun getBoolean(key: String, default: Boolean) =
        if (d.objectForKey(key) == null) default else d.boolForKey(key)
    override fun getLong(key: String, default: Long) =
        if (d.objectForKey(key) == null) default else d.integerForKey(key)
    override fun putString(key: String, value: String) = d.setObject(value, key)
    override fun putBoolean(key: String, value: Boolean) = d.setBool(value, key)
    override fun putLong(key: String, value: Long) = d.setInteger(value, key)
}

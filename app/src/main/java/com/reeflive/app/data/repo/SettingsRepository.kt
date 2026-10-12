package com.reeflive.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.reeflive.core.units.ParamUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }
enum class LiveQuality { LOW, MEDIUM, HIGH }

/** User preferences (ARCHITECTURE §6.1). */
data class AppSettings(
    val loaded: Boolean = false,
    val onboarded: Boolean = false,
    val theme: ThemeMode = ThemeMode.DARK,
    val tempUnit: ParamUnit = ParamUnit.F,
    val salUnit: ParamUnit = ParamUnit.SG,
    val alkUnit: ParamUnit = ParamUnit.DKH,
    val reminderHour: Int = 8,
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val liveQuality: LiveQuality = LiveQuality.MEDIUM,
    val lastKitBrand: String? = null,
    val tileOrder: List<String> = emptyList(),
    val glassBlur: Boolean = true,
    val notifications: Boolean = true,
) {
    /** Preferred display unit for a parameter whose base unit is [base]. */
    fun displayUnitFor(paramId: String, base: ParamUnit): ParamUnit = when (paramId) {
        "temp" -> tempUnit
        "sal" -> salUnit
        "alk" -> alkUnit
        else -> base
    }
}

class SettingsRepository(private val store: DataStore<Preferences>) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val theme = stringPreferencesKey("theme")
        val tempUnit = stringPreferencesKey("tempUnit")
        val salUnit = stringPreferencesKey("salUnit")
        val alkUnit = stringPreferencesKey("alkUnit")
        val reminderHour = intPreferencesKey("reminderHour")
        val reduceMotion = booleanPreferencesKey("reduceMotion")
        val haptics = booleanPreferencesKey("haptics")
        val liveQuality = stringPreferencesKey("liveQuality")
        val lastKitBrand = stringPreferencesKey("lastKitBrand")
        val tileOrder = stringPreferencesKey("tileOrderJson")
        val glassBlur = booleanPreferencesKey("glassBlur")
        val notifications = booleanPreferencesKey("notifications")
    }

    private fun unit(name: String?, default: ParamUnit) = ParamUnit.entries.firstOrNull { it.name == name } ?: default

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            loaded = true,
            onboarded = p[K.onboarded] ?: false,
            theme = ThemeMode.entries.firstOrNull { it.name == p[K.theme] } ?: ThemeMode.DARK,
            tempUnit = unit(p[K.tempUnit], ParamUnit.F),
            salUnit = unit(p[K.salUnit], ParamUnit.SG),
            alkUnit = unit(p[K.alkUnit], ParamUnit.DKH),
            reminderHour = p[K.reminderHour] ?: 8,
            reduceMotion = p[K.reduceMotion] ?: false,
            haptics = p[K.haptics] ?: true,
            liveQuality = LiveQuality.entries.firstOrNull { it.name == p[K.liveQuality] } ?: LiveQuality.MEDIUM,
            lastKitBrand = p[K.lastKitBrand],
            tileOrder = p[K.tileOrder]?.split(',')?.filter { it.isNotBlank() } ?: emptyList(),
            glassBlur = p[K.glassBlur] ?: true,
            notifications = p[K.notifications] ?: true,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setOnboarded(v: Boolean) = store.edit { it[K.onboarded] = v }
    suspend fun setTheme(v: ThemeMode) = store.edit { it[K.theme] = v.name }
    suspend fun setTempUnit(v: ParamUnit) = store.edit { it[K.tempUnit] = v.name }
    suspend fun setSalUnit(v: ParamUnit) = store.edit { it[K.salUnit] = v.name }
    suspend fun setAlkUnit(v: ParamUnit) = store.edit { it[K.alkUnit] = v.name }
    suspend fun setReminderHour(v: Int) = store.edit { it[K.reminderHour] = v.coerceIn(0, 23) }
    suspend fun setReduceMotion(v: Boolean) = store.edit { it[K.reduceMotion] = v }
    suspend fun setHaptics(v: Boolean) = store.edit { it[K.haptics] = v }
    suspend fun setLiveQuality(v: LiveQuality) = store.edit { it[K.liveQuality] = v.name }
    suspend fun setLastKitBrand(v: String?) = store.edit { if (v.isNullOrBlank()) { it.remove(K.lastKitBrand) } else { it[K.lastKitBrand] = v } }
    suspend fun setTileOrder(ids: List<String>) = store.edit { it[K.tileOrder] = ids.joinToString(",") }
    suspend fun setGlassBlur(v: Boolean) = store.edit { it[K.glassBlur] = v }
    suspend fun setNotifications(v: Boolean) = store.edit { it[K.notifications] = v }
}

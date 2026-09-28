package com.ryccoatika.journeyrecorder.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val Context.appPrefsDataStore by preferencesDataStore(name = "app_prefs")

/** Small app-wide preferences. Only the theme choice for now. */
class AppPrefs(private val context: Context) {

    /** The user's theme choice; SYSTEM (follow device) until they pick otherwise. */
    fun observeThemeMode(): Flow<ThemeMode> =
        context.appPrefsDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM
            }

    suspend fun setThemeMode(mode: ThemeMode) {
        try {
            context.appPrefsDataStore.edit { prefs -> prefs[THEME_MODE] = mode.name }
        } catch (_: IOException) {
            // Best-effort: losing the write just keeps the previous theme.
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
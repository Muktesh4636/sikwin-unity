package com.sikwin.app.data.prefs

import android.content.Context
import android.content.SharedPreferences

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gunduata_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_APP_THEME = "app_theme"

        /** Current / classic home layout */
        const val THEME_CLASSIC = "classic"

        /** Hero casino lobby layout */
        const val THEME_HERO = "hero"

        /** Exact Dual Cards mock (Variant 7) — banners unchanged */
        const val THEME_DUAL_CARDS = "dual_cards"
    }

    fun setAppTheme(themeId: String) {
        prefs.edit().putString(KEY_APP_THEME, themeId).apply()
    }

    fun getAppTheme(): String {
        return prefs.getString(KEY_APP_THEME, THEME_CLASSIC) ?: THEME_CLASSIC
    }

    fun isHeroTheme(): Boolean = getAppTheme() == THEME_HERO

    fun isDualCardsTheme(): Boolean = getAppTheme() == THEME_DUAL_CARDS
}

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

        /** White background home — live odds, casino grid, quick bet */
        const val THEME_WHITE = "white"
    }

    fun setAppTheme(themeId: String) {
        prefs.edit().putString(KEY_APP_THEME, themeId).apply()
    }

    fun getAppTheme(): String {
        return prefs.getString(KEY_APP_THEME, THEME_DUAL_CARDS) ?: THEME_DUAL_CARDS
    }

    /** Dark / current default home (dual cards). Hero & classic map here for display. */
    fun isDarkTheme(): Boolean = getAppTheme() != THEME_WHITE

    fun isHeroTheme(): Boolean = getAppTheme() == THEME_HERO

    fun isDualCardsTheme(): Boolean =
        getAppTheme() == THEME_DUAL_CARDS || getAppTheme() == THEME_HERO || getAppTheme() == THEME_CLASSIC

    fun isWhiteTheme(): Boolean = getAppTheme() == THEME_WHITE

    /**
     * WebView theme for sports/casino: "light" or "dark".
     * App white theme → light; all other home themes → dark.
     */
    fun webThemeMode(): String = if (isWhiteTheme()) "light" else "dark"

    /** Theme picker shows only dark vs white. */
    fun pickerThemeId(): String =
        if (getAppTheme() == THEME_WHITE) THEME_WHITE else THEME_DUAL_CARDS
}

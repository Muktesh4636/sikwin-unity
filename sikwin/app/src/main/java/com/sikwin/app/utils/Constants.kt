package com.sikwin.app.utils

object Constants {
    /** Site origin for WebView games / sports (TLS). */
    const val WEB_ORIGIN = "https://gunduata.tech"

    /** Production API — use the domain (TLS). The app never “hits the IP” unless you point [BASE_URL] at an IP. */
    const val BASE_URL = "$WEB_ORIGIN/api/"

    /** Direct IP only for local/staging when that host is reachable (e.g. same VPN). Redis lives on the server, not in the app. */
    // const val BASE_URL = "http://72.61.254.74/api/"

    // APK / signup links for users: use BuildConfig.PUBLIC_SITE_URL (per franchise in app/build.gradle).

    /** Live sports lobby (Cricket / Football / Tennis tabs) — WebView + JWT like casino. */
    const val SPORTS_URL = "$WEB_ORIGIN/sports/"

    /** Cricket list + match — WebView + JWT. */
    const val CRICKET_URL = "$WEB_ORIGIN/cricket/"

    /** @deprecated Prefer [CRICKET_URL]. Kept for older call sites. */
    const val IPL_WEB_URL = CRICKET_URL

    /** Roulette (real wallet only) — opened in-app WebView with JWT. */
    const val ROULETTE_URL = "$WEB_ORIGIN/roulette/"
    const val ROULETTE_API_URL = "$WEB_ORIGIN/roulette/api"

    /** Stock Market / trading (real wallet only) — WebView + JWT like roulette. */
    const val TRADING_URL = "$WEB_ORIGIN/trading/"
    const val TRADING_API_URL = "$WEB_ORIGIN/api/trading"

    /** Chicken Road games (real wallet) — WebView + JWT like roulette/trading. */
    const val CHICKEN_ROAD_URL = "$WEB_ORIGIN/chicken-road/"
    const val CHICKEN_ROAD_2_URL = "$WEB_ORIGIN/chicken-road-2/"

    /** Vortex (real wallet) — WebView + JWT. */
    const val VORTEX_URL = "$WEB_ORIGIN/vortex/"

    /** Casino lobby (web) — WebView + JWT. */
    const val CASINO_URL = "$WEB_ORIGIN/casino/"

    /**
     * Gundu Ata virtual (web) — in-app WebView + JWT.
     */
    const val GUNDU_ATA_WEB_URL = "$WEB_ORIGIN/game/index.html"

    /**
     * WebGL build hosted on your site (alias of [GUNDU_ATA_WEB_URL]).
     */
    const val WEBGL_GAME_URL = GUNDU_ATA_WEB_URL
}

package com.sikwin.app.utils

object Constants {
    /** Production API — use the domain (TLS). The app never “hits the IP” unless you point [BASE_URL] at an IP. */
    const val BASE_URL = "https://gunduata.tech/api/"

    /** Direct IP only for local/staging when that host is reachable (e.g. same VPN). Redis lives on the server, not in the app. */
    // const val BASE_URL = "http://72.61.254.74/api/"

    // APK / signup links for users: use BuildConfig.PUBLIC_SITE_URL (per franchise in app/build.gradle).

    const val IPL_WEB_URL = "https://gunduata.tech/cricket/"

    /** Roulette (real wallet only) — opened in-app WebView with JWT. */
    const val ROULETTE_URL = "https://gunduata.tech/roulette/"
    const val ROULETTE_API_URL = "https://gunduata.tech/roulette/api"

    /** Stock Market / trading (real wallet only) — WebView + JWT like roulette. */
    const val TRADING_URL = "https://gunduata.tech/trading/"
    const val TRADING_API_URL = "https://gunduata.tech/api/trading"

    /** Chicken Road games (real wallet) — WebView + JWT like roulette/trading. */
    const val CHICKEN_ROAD_URL = "https://gunduata.tech/chicken-road/"
    const val CHICKEN_ROAD_2_URL = "https://gunduata.tech/chicken-road-2/"

    /**
     * WebGL build hosted on your site. Opening this in the system browser (from the app) loads the game.
     * Change to your own origin when you deploy, e.g. `https://yourdomain.com/game/index.html`.
     */
    const val WEBGL_GAME_URL = "https://gunduata.tech/game/index.html"
}

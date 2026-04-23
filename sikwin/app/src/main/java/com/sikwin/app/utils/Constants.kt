package com.sikwin.app.utils

object Constants {
    /** Production API — use the domain (TLS). The app never “hits the IP” unless you point [BASE_URL] at an IP. */
    const val BASE_URL = "https://gunduata.club/api/"

    /** Direct IP only for local/staging when that host is reachable (e.g. same VPN). Redis lives on the server, not in the app. */
    // const val BASE_URL = "http://72.61.254.74/api/"

    // APK / signup links for users: use BuildConfig.PUBLIC_SITE_URL (per franchise in app/build.gradle).

    const val IPL_WEB_URL = "https://gunduata.club/cricket/"
}

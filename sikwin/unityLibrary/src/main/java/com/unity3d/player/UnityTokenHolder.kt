package com.unity3d.player

/**
 * Stub for Unity export. Replace with real Unity library when exporting from Unity.
 */
object UnityTokenHolder {
    @Volatile
    private var accessToken: String = ""
    @Volatile
    private var refreshToken: String = ""

    @JvmStatic
    fun setTokens(access: String, refresh: String, @Suppress("UNUSED_PARAMETER") unused1: String, @Suppress("UNUSED_PARAMETER") unused2: String) {
        accessToken = access ?: ""
        refreshToken = refresh ?: ""
    }

    @JvmStatic
    fun getAccessToken(): String = accessToken

    @JvmStatic
    fun getRefreshToken(): String = refreshToken

    @JvmStatic
    fun clear() {
        accessToken = ""
        refreshToken = ""
    }
}

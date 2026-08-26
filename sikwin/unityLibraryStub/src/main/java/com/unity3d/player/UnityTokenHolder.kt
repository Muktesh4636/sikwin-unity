package com.unity3d.player

/**
 * Same API as the real Unity module so Kotlin keeps compiling while Unity is disabled.
 */
object UnityTokenHolder {
    @Volatile
    private var accessToken: String = ""
    @Volatile
    private var refreshToken: String = ""

    @JvmStatic
    fun setTokens(
        access: String,
        refresh: String,
        @Suppress("UNUSED_PARAMETER") unused1: String,
        @Suppress("UNUSED_PARAMETER") unused2: String
    ) {
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

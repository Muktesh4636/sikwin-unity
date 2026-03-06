package com.sikwin.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import com.unity3d.player.UnityTokenHolder
import android.util.Base64
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionManager(private val context: Context) {
    // IMPORTANT: keep Kotlin session isolated from Unity process PlayerPrefs writes.
    // Unity may read/write SharedPreferences in a separate process; if it overwrites flags/keys,
    // the Kotlin app should not lose its session.
    private val sessionPrefs: SharedPreferences =
        context.getSharedPreferences("sikwin_session_prefs", Context.MODE_PRIVATE)
    private val prefs: SharedPreferences =
        context.getSharedPreferences("gunduata_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val USER_TOKEN = "user_token"
        private const val REFRESH_TOKEN = "refresh_token"
        private const val USERNAME = "username"
        private const val USER_ID = "user_id"
        private const val USER_PASS = "user_pass"
        private const val REFERRAL_CODE = "referral_code"
        private const val LAST_UNITY_LAUNCH_TS = "last_unity_launch_ts"
    }

    private fun clearUnityStoredCredentials() {
        // Unity may auto-login using stored username/password; that can invalidate Kotlin session
        // (backend single-session iat check). We clear credentials in Unity-pref files so Unity
        // relies on tokens only.
        try {
            val pkg = context.packageName
            val unityPrefNames = arrayOf(
                "UnityPlayerPrefs",
                "PlayerPrefs",
                "dicegame.v2.playerprefs",
                "com.company.dicegame.v2.playerprefs",
                "$pkg.v2.playerprefs",
                "$pkg.playerprefs",
                "${pkg}.playerprefs",
                // Also include legacy ones we used in integration
                "com.sikwin.app.playerprefs",
                "com.sikwin.app_playerprefs",
                // IMPORTANT: gunduata_prefs is shared with Kotlin app and Unity reads it too
                "gunduata_prefs",
                "${pkg}_preferences",
                pkg
            )
            val keys = arrayOf(
                "username", "USERNAME_KEY", "UserName",
                "password", "PASSWORD_KEY", "Password", "user_pass"
            )
            for (prefName in unityPrefNames) {
                try {
                    val p = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    val e = p.edit()
                    for (k in keys) e.remove(k)
                    e.commit()
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    fun scrubUnityCredentials() {
        try {
            clearUnityStoredCredentials()
        } catch (_: Exception) {}
    }

    fun saveReferralCode(code: String?) {
        if (code != null) {
            prefs.edit().putString(REFERRAL_CODE, code).apply()
        }
    }

    fun fetchReferralCode(): String? {
        return prefs.getString(REFERRAL_CODE, null)
    }

    fun isLogoutRequested(): Boolean = sessionPrefs.getString("logout_requested", "false") == "true"

    fun markUnityLaunchNow() {
        try {
            sessionPrefs.edit().putLong(LAST_UNITY_LAUNCH_TS, System.currentTimeMillis()).apply()
        } catch (_: Exception) {}
    }

    fun wasUnityLaunchRecent(windowMs: Long = 2 * 60 * 1000L): Boolean {
        return try {
            val ts = sessionPrefs.getLong(LAST_UNITY_LAUNCH_TS, 0L)
            ts > 0 && (System.currentTimeMillis() - ts) <= windowMs
        } catch (_: Exception) {
            false
        }
    }

    fun saveAuthToken(token: String) {
        // Store in both formats for compatibility
        // Unity expects "auth_token" but we also keep "user_token" for our app
        sessionPrefs.edit()
            .putString(USER_TOKEN, token)
            .putString("is_logged_in", "true")
            // If user logs in again, clear any previous forced-logout flags.
            .remove("logout_requested")
            .remove("logout_timestamp")
            // commit() avoids race where Unity launches before apply() is persisted
            .commit()

        // Keep legacy keys in app prefs for compatibility with older code paths / Unity fallback reads.
        try {
            prefs.edit()
                .putString(USER_TOKEN, token)
                .putString("auth_token", token)
                .putString("access_token", token)
                .putString("access", token)
                .putString("is_logged_in", "true")
                .remove("logout_requested")
                .remove("logout_timestamp")
                .apply()
        } catch (_: Exception) {}
    }

    fun fetchAuthToken(): String? {
        // If logout was requested, treat session as ended.
        // NOTE: Do NOT rely solely on "is_logged_in" flag because Unity runs in a separate process
        // and may write its own PlayerPrefs keys; token presence is the source of truth.
        if (isLogoutRequested()) {
            return null
        }
        // Try session prefs first (source of truth)
        var token = sessionPrefs.getString(USER_TOKEN, null)
            ?: sessionPrefs.getString("auth_token", null)
            ?: sessionPrefs.getString("access_token", null)
            ?: sessionPrefs.getString("access", null)

        // Migration: if session prefs are empty but old app prefs still have tokens, copy them over.
        if (token.isNullOrBlank()) {
            val legacy = prefs.getString(USER_TOKEN, null)
                ?: prefs.getString("auth_token", null)
                ?: prefs.getString("access_token", null)
                ?: prefs.getString("access", null)
            if (!legacy.isNullOrBlank()) {
                sessionPrefs.edit()
                    .putString(USER_TOKEN, legacy)
                    .putString("is_logged_in", "true")
                    .remove("logout_requested")
                    .remove("logout_timestamp")
                    .commit()
                token = legacy
            }
        }
        
        // Migration: If we have user_token but not auth_token, copy it
        if (token != null && !sessionPrefs.contains("auth_token")) {
            sessionPrefs.edit()
                .putString("auth_token", token)
                .putString("access_token", token)
                .putString("access", token)
                .apply()
            android.util.Log.d("SessionManager", "Migrated token keys for Unity compatibility")
        }
        
        return token
    }

    fun saveRefreshToken(token: String) {
        // Store under multiple keys for backward/Unity compatibility.
        sessionPrefs.edit()
            .putString(REFRESH_TOKEN, token)
            .putString("is_logged_in", "true")
            // If user logs in again, clear any previous forced-logout flags.
            .remove("logout_requested")
            .remove("logout_timestamp")
            .commit()

        try {
            prefs.edit()
                .putString(REFRESH_TOKEN, token)
                .putString("refresh", token)
                .putString("refreshToken", token)
                .putString("is_logged_in", "true")
                .remove("logout_requested")
                .remove("logout_timestamp")
                .apply()
        } catch (_: Exception) {}
    }

    /**
     * Save access+refresh together and sync Unity once (prevents login→open-game timing races).
     */
    fun saveTokens(access: String, refresh: String) {
        sessionPrefs.edit()
            .putString(USER_TOKEN, access)
            .putString(REFRESH_TOKEN, refresh)
            .putString("is_logged_in", "true")
            // If user logs in again, clear any previous forced-logout flags.
            .remove("logout_requested")
            .remove("logout_timestamp")
            .commit()

        // Also update app prefs for compatibility + Unity fallbacks
        try {
            prefs.edit()
                .putString(USER_TOKEN, access)
                .putString("auth_token", access)
                .putString("access_token", access)
                .putString("access", access)
                .putString(REFRESH_TOKEN, refresh)
                .putString("refresh", refresh)
                .putString("refreshToken", refresh)
                .putString("is_logged_in", "true")
                .remove("logout_requested")
                .remove("logout_timestamp")
                .apply()
        } catch (_: Exception) {}

        // Prevent Unity auto-login using stale creds from a previous account.
        clearUnityStoredCredentials()

        try {
            syncAuthToUnity()
        } catch (e: Exception) {
            android.util.Log.e("SessionManager", "saveTokens syncAuthToUnity failed", e)
        }
    }

    fun fetchRefreshToken(): String? {
        if (isLogoutRequested()) return null
        var t = sessionPrefs.getString(REFRESH_TOKEN, null)
            ?: sessionPrefs.getString("refresh", null)
            ?: sessionPrefs.getString("refreshToken", null)

        if (t.isNullOrBlank()) {
            val legacy = prefs.getString(REFRESH_TOKEN, null)
                ?: prefs.getString("refresh", null)
                ?: prefs.getString("refreshToken", null)
            if (!legacy.isNullOrBlank()) {
                sessionPrefs.edit().putString(REFRESH_TOKEN, legacy).apply()
                t = legacy
            }
        }

        // Migration: if refresh exists under an old key, copy it to the canonical one.
        if (!t.isNullOrBlank() && !sessionPrefs.contains(REFRESH_TOKEN)) {
            sessionPrefs.edit().putString(REFRESH_TOKEN, t).apply()
        }
        return t
    }

    fun saveUsername(username: String) {
        prefs.edit()
            .putString(USERNAME, username)
            .putString("USERNAME_KEY", username)
            .putString("UserName", username)
            .apply()
    }

    fun fetchUsername(): String? {
        return prefs.getString(USERNAME, null)
    }

    fun saveUserId(userId: Int) {
        sessionPrefs.edit().putString(USER_ID, userId.toString()).apply()
        // Keep a copy for older UI expectations (not security sensitive)
        try { prefs.edit().putString(USER_ID, userId.toString()).apply() } catch (_: Exception) {}
    }

    fun fetchUserId(): String {
        return try {
            sessionPrefs.getString(USER_ID, null)
                ?: prefs.getString(USER_ID, "0")
                ?: "0"
        } catch (e: ClassCastException) {
            // Fallback if it was accidentally stored as an int
            try {
                sessionPrefs.getInt(USER_ID, 0).toString()
            } catch (_: Exception) {
                prefs.getInt(USER_ID, 0).toString()
            }
        }
    }

    fun savePassword(password: String) {
        // Store only in Kotlin session prefs. Do NOT store under Unity-known keys in gunduata_prefs,
        // otherwise Unity can auto-login and invalidate Kotlin session.
        sessionPrefs.edit()
            .putString(USER_PASS, password)
            .apply()
    }

    fun fetchPassword(): String? {
        return sessionPrefs.getString(USER_PASS, null)
    }

    fun registerSessionListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sessionPrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterSessionListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sessionPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun appendLogoutTrace(reason: String, t: Throwable) {
        try {
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val msg = buildString {
                append("\n---\n")
                append(ts).append(" reason=").append(reason).append('\n')
                append(t.stackTraceToString()).append('\n')
            }
            // Persist so we can inspect even if logcat is lost.
            context.applicationContext.openFileOutput("logout_trace.txt", Context.MODE_APPEND).use { out ->
                out.write(msg.toByteArray())
            }
        } catch (_: Exception) {}
    }

    fun forceLogout(reason: String) {
        appendLogoutTrace(reason, Throwable("forceLogout"))
        logout()
    }

    private fun isLikelyJwt(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        val parts = token.split('.')
        return parts.size >= 3 && token.length >= 40
    }

    private fun jwtIat(token: String): Long? {
        return try {
            val parts = token.split('.')
            if (parts.size < 2) return null
            val payloadB64 = parts[1]
            val padded = when (payloadB64.length % 4) {
                0 -> payloadB64
                2 -> payloadB64 + "=="
                3 -> payloadB64 + "="
                else -> payloadB64
            }
            val jsonStr = String(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP))
            val json = JSONObject(jsonStr)
            json.optLong("iat").takeIf { it > 0 }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Unity may login/refresh and write newer tokens to PlayerPrefs (SharedPreferences).
     * When that happens, backend invalidates Kotlin's older token ("session_invalidated").
     * This method pulls the newest tokens from Unity prefs back into Kotlin session.
     */
    fun syncAuthFromUnityPrefs(): Boolean {
        try {
            val pkg = context.packageName
            val prefNames = arrayOf(
                "UnityPlayerPrefs",
                "PlayerPrefs",
                "dicegame.v2.playerprefs",
                "com.company.dicegame.v2.playerprefs",
                "$pkg.v2.playerprefs",
                "$pkg.playerprefs",
                "${pkg}.playerprefs",
                "gunduata_prefs"
            )

            val candidates = mutableListOf<Pair<String, String>>() // (token, sourcePref)
            val refreshCandidates = mutableListOf<Pair<String, String>>()

            for (name in prefNames) {
                val p = context.getSharedPreferences(name, Context.MODE_PRIVATE)
                val a =
                    p.getString("access", null)
                        ?: p.getString("access_token", null)
                        ?: p.getString("accessToken", null)
                        ?: p.getString("AccessToken", null)
                        ?: p.getString("auth_token", null)
                        ?: p.getString("user_token", null)
                        ?: p.getString("token", null)
                        ?: p.getString("TOKEN", null)
                if (isLikelyJwt(a)) candidates.add(a!! to name)

                val r =
                    p.getString("refresh", null)
                        ?: p.getString("refresh_token", null)
                        ?: p.getString("refreshToken", null)
                        ?: p.getString("RefreshToken", null)
                        ?: p.getString("REFRESH_TOKEN", null)
                        ?: p.getString("REFRESH", null)
                if (isLikelyJwt(r)) refreshCandidates.add(r!! to name)
            }

            if (candidates.isEmpty()) return false

            fun newest(list: List<Pair<String, String>>): String {
                return list.maxByOrNull { jwtIat(it.first) ?: 0L }?.first ?: list[0].first
            }

            val newestAccess = newest(candidates)
            val newestRefresh = if (refreshCandidates.isNotEmpty()) newest(refreshCandidates) else null

            val currentAccess = fetchAuthToken()
            val changed = currentAccess.isNullOrBlank() || currentAccess != newestAccess
            if (!changed) return false

            // Save into Kotlin session prefs (source of truth) and clear logout flags.
            if (!newestRefresh.isNullOrBlank()) {
                saveTokens(newestAccess, newestRefresh)
            } else {
                saveAuthToken(newestAccess)
            }

            android.util.Log.w("SessionManager", "syncAuthFromUnityPrefs: adopted newer access token from Unity prefs")
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun clearSavedPassword() {
        sessionPrefs.edit().remove(USER_PASS).apply()
        // Also scrub any legacy saved password keys from app prefs / Unity prefs
        try { clearUnityStoredCredentials() } catch (_: Exception) {}
    }

    fun syncAuthToUnity() {
        // Sync authentication data to Unity PlayerPrefs for seamless login
        try {
            // Comprehensive list of all possible SharedPreferences files Unity might check
            val standalonePackageName = "com.company.dicegame"
            val pkg = context.packageName
            
            val authToken = fetchAuthToken()
            val refreshToken = fetchRefreshToken()
            val userId = fetchUserId()
            val isLoggedIn = !authToken.isNullOrBlank()
            
            // CRITICAL: Always push to the static holder FIRST.
            // This is the fastest way for Unity to see the tokens in Awake().
            if (!authToken.isNullOrBlank()) {
                com.unity3d.player.UnityTokenHolder.setTokens(authToken, refreshToken ?: "", "", "")
                android.util.Log.d("SessionManager", "syncAuthToUnity: Set static UnityTokenHolder (accessLen=${authToken.length})")
            }

            // Comprehensive list of all possible SharedPreferences files Unity might check
            val allPrefsToSync = arrayOf(
                "$standalonePackageName.v2.playerprefs",
                "$pkg.v2.playerprefs",
                "gunduata_prefs",
                "UnityPlayerPrefs",
                "dicegame.v2.playerprefs",
                "PlayerPrefs",
                "${pkg}_preferences",
                standalonePackageName,
                pkg,
                "${pkg}.playerprefs"
            )

            for (prefName in allPrefsToSync) {
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().also { e ->
                    if (isLoggedIn) {
                        e.putString("auth_token", authToken)
                        e.putString("access_token", authToken)
                        e.putString("access", authToken)
                        e.putString("user_token", authToken)
                        e.putString("token", authToken)
                        e.putString("bearer_token", authToken)

                        if (!refreshToken.isNullOrBlank()) {
                            e.putString("refresh_token", refreshToken)
                            e.putString("refresh", refreshToken)
                            e.putString("refreshToken", refreshToken)
                        }

                        e.putString("is_logged_in", "true")
                        // Only clear logout flags when we truly have a valid session.
                        e.remove("logout_requested")
                        e.remove("logout_timestamp")
                    } else {
                        // IMPORTANT: never flip the app back to logged-in when token is missing.
                        // Also clear any stale token keys Unity might read.
                        e.remove("auth_token")
                        e.remove("access_token")
                        e.remove("access")
                        e.remove("user_token")
                        e.remove("token")
                        e.remove("bearer_token")
                        e.remove("refresh_token")
                        e.remove("refresh")
                        e.remove("refreshToken")
                        e.putString("is_logged_in", "false")
                        // Leave logout_requested/logout_timestamp untouched.
                    }

                    e.putString("user_id", userId)
                    e.commit()
                }
            }
            
            // 10. BROADCAST: Send a broadcast that UnityPlayerGameActivity can catch
            if (!authToken.isNullOrBlank()) {
                val intent = Intent("com.sikwin.app.TOKEN_UPDATE")
                intent.putExtra("access", authToken)
                intent.putExtra("refresh", refreshToken ?: "")
                intent.setPackage(context.packageName)
                context.sendBroadcast(intent)
                android.util.Log.d("SessionManager", "Sent TOKEN_UPDATE broadcast for Unity")
            }
        } catch (e: Exception) {
            android.util.Log.e("SessionManager", "Failed to sync auth to Unity", e)
        }
    }

    /**
     * Sync raw username/password into Unity PlayerPrefs (SharedPreferences).
     * WARNING: Sensitive data. Do not log credentials.
     */
    fun syncCredentialsToUnity(username: String?, password: String?) {
        try {
            val user = username ?: return
            val pass = password ?: return
            if (user.isBlank() && pass.isBlank()) return

            val standalonePackageName = "com.company.dicegame"
            val pkg = context.packageName
            val allPrefsToSync = arrayOf(
                "$standalonePackageName.v2.playerprefs",
                "$pkg.v2.playerprefs",
                "gunduata_prefs",
                "UnityPlayerPrefs",
                "dicegame.v2.playerprefs",
                "PlayerPrefs",
                "${pkg}_preferences",
                standalonePackageName,
                pkg,
                "${pkg}.playerprefs"
            )

            for (prefName in allPrefsToSync) {
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().also { e ->
                    if (user.isNotBlank()) {
                        e.putString("username", user)
                        e.putString("USERNAME_KEY", user)
                        e.putString("UserName", user)
                    }
                    if (pass.isNotBlank()) {
                        e.putString("password", pass)
                        e.putString("PASSWORD_KEY", pass)
                        e.putString("Password", pass)
                        e.putString("user_pass", pass)
                    }
                    e.commit()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SessionManager", "Failed to sync credentials to Unity", e)
        }
    }

    fun syncAuthToUnityV2() {
        // Alternative sync for newer Android versions using a common prefix
        try {
            val standalonePackageName = "com.company.dicegame"
            val authToken = fetchAuthToken()
            if (authToken == null) return

            // Some Unity versions use this format
            val altPrefsName = "UnityPlayerPrefs"
            val altPrefs = context.getSharedPreferences(altPrefsName, Context.MODE_PRIVATE)
            altPrefs.edit()
                .putString("auth_token", authToken)
                .putString("is_logged_in", "true")
                .apply()
        } catch (e: Exception) {}
    }
    
    fun logout() {
        android.util.Log.w("SessionManager", "logout() called: forcing hard logout", Throwable("logout stack"))
        appendLogoutTrace("logout()", Throwable("logout"))
        // Preserve saved credentials for quick login, clear only auth data
        val savedUser = prefs.getString(USERNAME, null)
        val savedPass = prefs.getString(USER_PASS, null)
        
        // CRITICAL: We only want to clear tokens that Unity uses, 
        // NOT the main app's session if we want to stay logged in on the Kotlin side.
        // However, usually 'logout' means logging out of the whole app.
        // If the user says "it is asking me to login", it means the Kotlin app's session was cleared.
        
        // Hard logout: clear tokens + mark logout_requested synchronously so UI reacts immediately.
        sessionPrefs.edit()
            .remove(USER_TOKEN)
            .remove("auth_token")
            .remove("access_token")
            .remove("access")
            .remove(REFRESH_TOKEN)
            .remove("refresh")
            .remove("refreshToken")
            .putString("is_logged_in", "false")
            .putString("logout_requested", "true")
            .putLong("logout_timestamp", System.currentTimeMillis())
            .commit()

        // Also clear legacy token keys in app prefs so Unity doesn't reuse them.
        try {
            prefs.edit()
                .remove(USER_TOKEN)
                .remove("auth_token")
                .remove("access_token")
                .remove("access")
                .remove(REFRESH_TOKEN)
                .remove("refresh")
                .remove("refreshToken")
                .putString("is_logged_in", "false")
                // Do NOT set logout_requested here; sessionPrefs is the source of truth.
                .apply()
        } catch (_: Exception) {}
        
        try {
            // Use reflection to avoid NoClassDefFoundError at runtime
            val clazz = Class.forName("com.unity3d.player.UnityTokenHolder")
            val method = clazz.getMethod("clear")
            method.invoke(null)
            android.util.Log.d("SessionManager", "UnityTokenHolder cleared via reflection")
        } catch (e: Exception) {
            android.util.Log.e("SessionManager", "UnityTokenHolder cleanup skipped or failed: ${e.message}")
        }

        // Clear Unity PlayerPrefs to sync logout
        try {
            val standalonePackageName = "com.company.dicegame"
            val pkg = context.packageName
            
            // List of Unity-specific files to clear COMPLETELY
            val unitySpecificFiles = arrayOf(
                "$standalonePackageName.v2.playerprefs",
                "$pkg.v2.playerprefs",
                "UnityPlayerPrefs",
                "dicegame.v2.playerprefs",
                "PlayerPrefs",
                standalonePackageName,
                "${pkg}.playerprefs"
            )

            for (prefName in unitySpecificFiles) {
                try {
                    // Use apply() instead of commit() to avoid blocking
                    context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().apply()
                } catch (e: Exception) {
                    android.util.Log.e("SessionManager", "Failed to clear pref $prefName: ${e.message}")
                }
            }

            // For files shared with the main app, only clear the token keys
            val sharedFiles = arrayOf("gunduata_prefs", "${pkg}_preferences", pkg)
            for (prefName in sharedFiles) {
                try {
                    context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().also { e ->
                        e.remove("auth_token")
                        e.remove("access_token")
                        e.remove("access")
                        e.remove("token")
                        e.remove("refresh_token")
                        e.remove("refresh")
                        e.remove("refreshToken")
                        e.remove("user_token")
                        e.putString("is_logged_in", "false")
                        e.commit()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SessionManager", "Failed to update shared pref $prefName: ${e.message}")
                }
            }

            android.util.Log.d("SessionManager", "Cleared Unity tokens while preserving app session")

            // BROADCAST: Notify running Unity activity to logout immediately
            try {
                com.sikwin.app.utils.UnityTokenHelper.sendLogoutToUnity(context)
            } catch (e: Exception) {
                android.util.Log.e("SessionManager", "sendLogoutToUnity failed: ${e.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("SessionManager", "Failed to clear Unity prefs", e)
        }
    }

    fun isNewUser(): Boolean {
        return prefs.getBoolean("is_new_user", true)
    }

    fun setNewUser(isNew: Boolean) {
        prefs.edit().putBoolean("is_new_user", isNew).apply()
    }

    fun getContext(): Context = context
}

package com.sikwin.app.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import org.json.JSONObject

object UnityTokenHelper {
    private const val TAG = "UnityTokenHelper"
    private const val UNITY_GAMEOBJECT = "GameManager"
    // Unity receiver name differs between exports (some use singular Token, some plural Tokens).
    private val UNITY_METHODS = arrayOf("SetAccessAndRefreshTokens", "SetAccessAndRefreshToken")
    private const val UNITY_LOGIN_METHOD = "SetLoginCredential"

    /**
     * Send access and refresh tokens to Unity via UnitySendMessage.
     * Uses reflection so it works when the real Unity library is in the APK (full build).
     * No-op if UnityPlayer.UnitySendMessage is not available (stub build).
     */
    fun sendTokensToUnity(access: String, refresh: String) {
        if (access.isBlank() && refresh.isBlank()) return
        try {
            val json = JSONObject().apply {
                put("access", access)
                put("refresh", refresh)
                // Compatibility: some Unity builds parse different field names
                put("access_token", access)
                put("refresh_token", refresh)
                put("accessToken", access)
                put("refreshToken", refresh)
                put("token", access)
                put("auth_token", access)
            }.toString()
            val unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer")
            val method = unityPlayerClass.getMethod("UnitySendMessage", String::class.java, String::class.java, String::class.java)
            UNITY_METHODS.forEach { unityMethodName ->
                method.invoke(null, UNITY_GAMEOBJECT, unityMethodName, json)
            }
            Log.d(TAG, "UnitySendMessage($UNITY_GAMEOBJECT, SetAccessAndRefreshToken(s)) sent")
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "UnityPlayer not available (stub build), skip UnitySendMessage")
        } catch (e: NoSuchMethodException) {
            Log.d(TAG, "UnitySendMessage not available, skip")
        } catch (e: Exception) {
            Log.e(TAG, "UnitySendMessage failed: ${e.message}")
        }
    }

    /**
     * Sends tokens to Unity: UnitySendMessage (when Unity is running) + broadcast fallback.
     * Token-only: do NOT send username/password.
     */
    fun sendTokensToUnity(
        context: Context,
        access: String,
        refresh: String
    ) {
        try {
            sendTokensToUnity(access, refresh)
            val intent = Intent("com.sikwin.app.TOKEN_UPDATE").apply {
                putExtra("access", access)
                putExtra("refresh", refresh)
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
            Log.d(TAG, "Token broadcast sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending token broadcast: ${e.message}")
        }
    }

    /**
     * Trigger logout in Unity using Broadcast.
     */
    fun sendLogoutToUnity(context: Context) {
        try {
            Log.d(TAG, "Sending logout to Unity via Broadcast")
            val intent = Intent("com.sikwin.app.TOKEN_UPDATE").apply {
                putExtra("action", "logout")
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending logout broadcast: ${e.message}")
        }
    }

    /**
     * Send raw username/password to Unity via UnitySendMessage.
     * WARNING: Sensitive data. Do not log credentials.
     */
    fun sendCredentialsToUnity(username: String, password: String) {
        if (username.isBlank() && password.isBlank()) return
        try {
            val json = JSONObject().apply {
                put("username", username)
                put("password", password)
            }.toString()
            val unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer")
            val method = unityPlayerClass.getMethod("UnitySendMessage", String::class.java, String::class.java, String::class.java)
            method.invoke(null, UNITY_GAMEOBJECT, UNITY_LOGIN_METHOD, json)
            Log.d(TAG, "UnitySendMessage($UNITY_GAMEOBJECT, $UNITY_LOGIN_METHOD) sent")
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "UnityPlayer not available (stub build), skip UnitySendMessage")
        } catch (e: NoSuchMethodException) {
            Log.d(TAG, "UnitySendMessage not available, skip")
        } catch (e: Exception) {
            Log.e(TAG, "UnitySendMessage credentials failed: ${e.message}")
        }
    }
}

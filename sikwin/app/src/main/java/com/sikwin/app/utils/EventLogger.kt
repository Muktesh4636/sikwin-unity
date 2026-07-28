package com.sikwin.app.utils

import android.content.Context
import android.os.Build
import android.util.Log
import com.sikwin.app.BuildConfig
import com.sikwin.app.data.api.RetrofitClient
import com.sikwin.app.data.auth.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fire-and-forget click / error telemetry → POST /api/client-events/
 * Also mirrors to Logcat tag [SikwinEvent].
 */
object EventLogger {
    private const val TAG = "SikwinEvent"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var sessionManager: SessionManager? = null
    @Volatile private var deviceModel: String = Build.MODEL ?: ""
    @Volatile private var androidVersion: String = Build.VERSION.RELEASE ?: ""
    @Volatile private var appVersion: String = ""
    @Volatile private var currentScreen: String = ""

    fun init(context: Context, sessionManager: SessionManager) {
        this.sessionManager = sessionManager
        deviceModel = Build.MODEL ?: ""
        androidVersion = Build.VERSION.RELEASE ?: ""
        appVersion = try {
            val p = context.packageManager.getPackageInfo(context.packageName, 0)
            p.versionName ?: BuildConfig.VERSION_NAME
        } catch (_: Exception) {
            BuildConfig.VERSION_NAME
        }
    }

    fun setScreen(screen: String) {
        currentScreen = screen
        screen(screen)
    }

    fun click(name: String, props: Map<String, Any?> = emptyMap()) {
        emit("click", name, message = "", props = props)
    }

    fun screen(name: String, props: Map<String, Any?> = emptyMap()) {
        currentScreen = name
        emit("screen", name, message = "", props = props)
    }

    fun error(
        name: String,
        message: String,
        throwable: Throwable? = null,
        props: Map<String, Any?> = emptyMap()
    ) {
        val merged = props.toMutableMap()
        if (throwable != null) {
            merged["exception"] = throwable.javaClass.simpleName
            merged["exception_message"] = throwable.message
        }
        emit("error", name, message = message, props = merged)
    }

    private fun emit(
        eventType: String,
        name: String,
        message: String,
        props: Map<String, Any?>
    ) {
        val username = sessionManager?.fetchUsername().orEmpty()
        val screen = currentScreen
        val line = buildString {
            append(eventType).append('|').append(name)
            if (screen.isNotBlank()) append("|screen=").append(screen)
            if (message.isNotBlank()) append("|").append(message.take(200))
        }
        when (eventType) {
            "error" -> Log.e(TAG, line)
            else -> Log.i(TAG, line)
        }

        scope.launch {
            try {
                val body = HashMap<String, Any>()
                body["event_type"] = eventType
                body["name"] = name.take(128)
                body["message"] = message.take(4000)
                body["screen"] = screen.take(64)
                body["username"] = username
                body["device_model"] = deviceModel
                body["android_version"] = androidVersion
                body["app_version"] = appVersion
                if (props.isNotEmpty()) {
                    val clean = LinkedHashMap<String, Any>()
                    props.entries.take(40).forEach { (k, v) ->
                        if (v != null) clean[k] = v
                    }
                    if (clean.isNotEmpty()) body["props"] = clean
                }
                RetrofitClient.apiService.postClientEvent(body)
            } catch (e: Exception) {
                // Never crash the app for telemetry failures.
                Log.w(TAG, "telemetry send failed: ${e.message}")
            }
        }
    }
}

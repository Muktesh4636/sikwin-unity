package com.sikwin.app.network

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit

/**
 * Thin OkHttp WebSocket wrapper for the Android app (Kotlin).
 *
 * **Server:** `wss://yourdomain.com/path` (TLS recommended). Your backend must speak WebSocket.
 *
 * **Unity WebGL:** use a C# WebSocket library that supports WebGL (e.g. NativeWebSocket, or a JS plugin),
 * not this file — browsers are not OkHttp.
 *
 * Callbacks from OkHttp run on a **background thread**; [deliverOnMainThread] mirrors them to the main thread for UI.
 */
class AppWebSocket(
    private val url: String,
    private val listener: Listener,
    /** Optional `Authorization` header value, e.g. `Bearer <access_token>`. */
    private val authorization: String? = null,
    private val deliverOnMainThread: Boolean = true,
) {
    interface Listener {
        fun onOpen(webSocket: WebSocket, response: Response)
        fun onMessage(text: String)
        fun onClosing(code: Int, reason: String)
        fun onClosed(code: Int, reason: String)
        fun onFailure(t: Throwable, response: Response?)
    }

    private val main = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    fun connect() {
        val req = Request.Builder()
            .url(url)
            .apply { authorization?.let { header("Authorization", it) } }
            .build()

        socket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                dispatch { listener.onOpen(webSocket, response) }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                dispatch { listener.onMessage(text) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                dispatch { listener.onMessage(bytes.utf8()) }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                dispatch { listener.onClosing(code, reason) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                dispatch { listener.onClosed(code, reason) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                dispatch { listener.onFailure(t, response) }
            }
        })
    }

    private inline fun dispatch(crossinline block: () -> Unit) {
        if (deliverOnMainThread) {
            main.post { block() }
        } else {
            block()
        }
    }

    fun send(text: String): Boolean = socket?.send(text) == true

    fun send(bytes: ByteString): Boolean = socket?.send(bytes) == true

    fun close(code: Int = 1000, reason: String? = null) {
        socket?.close(code, reason)
        socket = null
    }

    fun cancel() {
        socket?.cancel()
        socket = null
    }
}

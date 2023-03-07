package com.example.for.audit

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.*
import com.example.for.audit.WebSocketClient

class WebSocketClientImpl(
    private val url: String,
    private val okHttpClient: OkHttpClient,
    private val exponentialBackoff: ExponentialBackoff = ExponentialBackoff()
) : WebSocketClient {
    companion object {
        private const val TAG = "WebSocketClientImpl"
        private const val CLOSE_NORMAL = 1000
    }

    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private var reconnectJob: Job? = null
    private var webSocket: WebSocket? = null
    private var listener: ((String) -> Unit)? = null
    private val isStarted: Boolean
        get() = webSocket != null

    override fun start(): Flow<String> {
        return callbackFlow {
            this@WebSocketClientImpl.listener = { trySend(it) }
            startWebSocket()
            try {
                awaitCancellation()
            } finally {
                stopWebSocket()
            }
        }
    }

    private fun reconnect() = synchronized(this) {
        reconnectJob = scope.launch {
            exponentialBackoff.delay()
            startWebSocket()
        }
    }

    private fun startWebSocket() = synchronized(this) {
        if (isStarted) {
            throw IllegalStateException("Web socket client has already been started on $url")
        }
        val request = Request.Builder().url(url).build()
        webSocket = okHttpClient.newWebSocket(request, WsListener())
        Log.i(TAG, "Web socket client has been started on $url")
        reconnectJob = null
    }

    private fun stopWebSocket() = synchronized(this) {
        reconnectJob?.cancel()
        webSocket?.close(CLOSE_NORMAL, null)
        webSocket = null
        exponentialBackoff.reset()
    }

    private inner class WsListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)
            Log.i(TAG, "onOpen response: $response")
            exponentialBackoff.reset()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            Log.i(TAG, "onMessage text=$text")
            listener?.invoke(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)
            Log.i(TAG, "onClosing code=$code, reason=$reason")
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            Log.i(TAG, "onClosed code=$code, reason=$reason")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
            Log.e(TAG, "onFailure: ${t.message}, response: $response")
            webSocket.cancel()
            exponentialBackoff.fail()
            reconnect()
        }
    }
}

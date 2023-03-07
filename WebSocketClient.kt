package com.example.for.audit

import kotlinx.coroutines.flow.Flow

/**
 * WebSocketClient will be stopped automatically on scope cancellation
 */
interface WebSocketClient {
    fun start(): Flow<String>
}

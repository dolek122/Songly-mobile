package com.songly.shared.session

import com.songly.shared.model.AuthTokens
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface TokenStorage {
	suspend fun getTokens(): AuthTokens?

	suspend fun saveTokens(tokens: AuthTokens)

	suspend fun clear()
}

class InMemoryTokenStorage : TokenStorage {

	private val mutex = Mutex()
	private var tokens: AuthTokens? = null

	override suspend fun getTokens(): AuthTokens? = mutex.withLock { tokens }

	override suspend fun saveTokens(tokens: AuthTokens) = mutex.withLock { this.tokens = tokens }

	override suspend fun clear() = mutex.withLock { this.tokens = null }
}

package com.songly.shared.session

import com.songly.shared.model.AuthTokens
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Trwałość tokenów sesji. Celowo interfejs — access i refresh token to dane
 * wrażliwe (Claude.md §9), więc produkcyjna implementacja powinna trzymać je w
 * bezpiecznym magazynie platformy:
 *  - Android: EncryptedSharedPreferences / DataStore + Keystore,
 *  - iOS: Keychain.
 *
 * [InMemoryTokenStorage] to domyślny fallback (sesja ginie po restarcie aplikacji)
 * — wystarcza do pierwszego ekranu logowania i testów, zanim wepniemy magazyn
 * platformowy.
 */
interface TokenStorage {
	suspend fun getTokens(): AuthTokens?

	suspend fun saveTokens(tokens: AuthTokens)

	suspend fun clear()
}

/** Domyślna, nietrwała implementacja — bezpieczna wątkowo. */
class InMemoryTokenStorage : TokenStorage {

	private val mutex = Mutex()
	private var tokens: AuthTokens? = null

	override suspend fun getTokens(): AuthTokens? = mutex.withLock { tokens }

	override suspend fun saveTokens(tokens: AuthTokens) = mutex.withLock { this.tokens = tokens }

	override suspend fun clear() = mutex.withLock { this.tokens = null }
}

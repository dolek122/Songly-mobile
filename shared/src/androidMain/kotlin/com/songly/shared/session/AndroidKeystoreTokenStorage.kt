package com.songly.shared.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.songly.shared.model.AuthTokens
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Trwały, szyfrowany magazyn tokenów sesji dla Androida. Tokeny to dane wrażliwe
 * (Claude.md §9), więc szyfrujemy je kluczem AES-256/GCM trzymanym w Android
 * Keystore (sprzętowo, gdy urządzenie ma TEE/StrongBox) — do SharedPreferences
 * trafia wyłącznie szyfrogram, nigdy tokeny w jawnej postaci.
 *
 * Świadomie bez `EncryptedSharedPreferences` (Jetpack security-crypto jest
 * zdeprecjonowany). Operacje kryptograficzne i I/O idą na [Dispatchers.IO].
 *
 * Utwórz z `applicationContext`, nie z Activity, żeby nie przeciekał kontekst.
 */
class AndroidKeystoreTokenStorage(context: Context) : TokenStorage {

	private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	private val json = Json { ignoreUnknownKeys = true }

	override suspend fun getTokens(): AuthTokens? = withContext(Dispatchers.IO) {
		val stored = prefs.getString(KEY_SESSION, null) ?: return@withContext null
		try {
			json.decodeFromString<AuthTokens>(decrypt(stored).decodeToString())
		} catch (e: Exception) {
			// Uszkodzony szyfrogram lub unieważniony klucz (np. zmiana zabezpieczeń
			// ekranu) — traktujemy jak brak sesji i czyścimy, żeby nie zapętlić błędu.
			clearBlocking()
			null
		}
	}

	override suspend fun saveTokens(tokens: AuthTokens): Unit = withContext(Dispatchers.IO) {
		val ciphertext = encrypt(json.encodeToString(tokens).encodeToByteArray())
		prefs.edit().putString(KEY_SESSION, ciphertext).apply()
	}

	override suspend fun clear(): Unit = withContext(Dispatchers.IO) { clearBlocking() }

	private fun clearBlocking() {
		prefs.edit().remove(KEY_SESSION).apply()
	}

	private fun encrypt(plaintext: ByteArray): String {
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
		val iv = cipher.iv
		val combined = iv + cipher.doFinal(plaintext)
		return Base64.encodeToString(combined, Base64.NO_WRAP)
	}

	private fun decrypt(stored: String): ByteArray {
		val combined = Base64.decode(stored, Base64.NO_WRAP)
		val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
		val ciphertext = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
		return cipher.doFinal(ciphertext)
	}

	private fun getOrCreateKey(): SecretKey {
		val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
		(keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

		val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
		generator.init(
			KeyGenParameterSpec.Builder(
				KEY_ALIAS,
				KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
			)
				.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
				.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
				.setKeySize(256)
				.build(),
		)
		return generator.generateKey()
	}

	private companion object {
		const val PREFS_NAME = "songly_session"
		const val KEY_SESSION = "tokens"
		const val ANDROID_KEYSTORE = "AndroidKeyStore"
		const val KEY_ALIAS = "songly_session_key"
		const val TRANSFORMATION = "AES/GCM/NoPadding"
		const val GCM_IV_LENGTH = 12
		const val GCM_TAG_LENGTH_BITS = 128
	}
}

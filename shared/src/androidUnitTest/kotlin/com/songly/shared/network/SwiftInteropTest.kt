package com.songly.shared.network

import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Funkcja `suspend` wywołana ze Swifta przepuszcza jako błąd wyłącznie
 * `CancellationException`. Każdy inny wyjątek — [com.songly.shared.model.SonglyApiException]
 * przy złym haśle, brak sieci — **kończy proces aplikacji iOS**, zamiast trafić
 * do `catch`. Dlatego każda publiczna metoda `suspend` klienta musi mieć
 * `@Throws(Exception::class)`.
 *
 * Test żyje na JVM, bo iOS nie zbuduje się poza macOS, a tu `@Throws` zamienia
 * się w klauzulę `throws` widoczną przez refleksję. Bez niego brak adnotacji na
 * nowej metodzie wyszedłby dopiero jako crash na iPhonie.
 */
class SwiftInteropTest {

	@Test
	fun everyPublicSuspendFunctionDeclaresThrows() {
		// Bez syntetycznych `access$…` — kompilator generuje je dla prywatnych metod
		// wołanych z lambd; są publiczne w bajtkodzie, ale Swift ich nie widzi.
		val suspendMethods = SonglyApiClient::class.java.declaredMethods.filter {
			Modifier.isPublic(it.modifiers) && !it.isSynthetic &&
				it.parameterTypes.lastOrNull() == Continuation::class.java
		}
		assertTrue(suspendMethods.isNotEmpty(), "refleksja nie znalazła metod suspend — test nic by nie sprawdzał")

		val missing = suspendMethods.filterNot { Exception::class.java in it.exceptionTypes }.map { it.name }.sorted()
		if (missing.isNotEmpty()) {
			fail("Brak @Throws(Exception::class) — wywołane ze Swifta wywrócą aplikację przy pierwszym błędzie: $missing")
		}
	}
}

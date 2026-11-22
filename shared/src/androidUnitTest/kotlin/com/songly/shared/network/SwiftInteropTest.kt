package com.songly.shared.network

import java.lang.reflect.Modifier
import kotlin.coroutines.Continuation
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class SwiftInteropTest {

	@Test
	fun everyPublicSuspendFunctionDeclaresThrows() {
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

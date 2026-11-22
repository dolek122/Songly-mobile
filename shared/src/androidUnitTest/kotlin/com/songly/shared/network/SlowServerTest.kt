package com.songly.shared.network

import com.songly.shared.config.ApiConfig
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class SlowServerTest {

	@Test
	fun requestSurvivesBackendWakingUp() = runBlocking {
		ServerSocket(0).use { server ->
			thread(isDaemon = true) {
				server.accept().use { socket ->
					val input = socket.getInputStream().bufferedReader()
					while (input.readLine().orEmpty().isNotEmpty()) Unit
					Thread.sleep(12_000)
					val body = """{"content":[],"page":{"size":100,"number":0,"totalElements":0,"totalPages":0}}"""
					socket.getOutputStream().write(
						("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${body.length}\r\n" +
							"Connection: close\r\n\r\n$body").toByteArray(),
					)
				}
			}

			val client = SonglyApiClient(ApiConfig(baseUrl = "http://127.0.0.1:${server.localPort}"))

			assertEquals(0, client.genres().totalElements)
		}
	}
}

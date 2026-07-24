package com.songly.shared.contract

import com.songly.shared.model.ApiError
import com.songly.shared.model.AuthTokens
import com.songly.shared.model.BlockedUser
import com.songly.shared.model.Comment
import com.songly.shared.model.Community
import com.songly.shared.model.CommunityMember
import com.songly.shared.model.ConnectResponse
import com.songly.shared.model.CreateCommentRequest
import com.songly.shared.model.CreateCommunityRequest
import com.songly.shared.model.CreatePostRequest
import com.songly.shared.model.DeleteAccountRequest
import com.songly.shared.model.Friend
import com.songly.shared.model.FriendRequest
import com.songly.shared.model.Genre
import com.songly.shared.model.LoginRequest
import com.songly.shared.model.ManualNowPlayingRequest
import com.songly.shared.model.Match
import com.songly.shared.model.Notification
import com.songly.shared.model.NowPlayingStatus
import com.songly.shared.model.PageMetadata
import com.songly.shared.model.Post
import com.songly.shared.model.Profile
import com.songly.shared.model.PublicProfile
import com.songly.shared.model.RefreshRequest
import com.songly.shared.model.RegisterRequest
import com.songly.shared.model.SendFriendRequestRequest
import com.songly.shared.model.SetFavoriteGenresRequest
import com.songly.shared.model.UnreadCount
import com.songly.shared.model.UpdateCommunityRequest
import com.songly.shared.model.UpdateProfileRequest
import com.songly.shared.model.UserSearchResult
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Wykrywa dryf między ręcznie pisanymi modelami mobilnymi a kontraktem OpenAPI
 * backendu ({@code shared/contract/openapi.json}, zsynchronizowana kopia
 * {@code docs/api/openapi.json} z Songly-backend).
 *
 * Reguła: **każde pole modelu musi istnieć w schemacie backendu**. Modelom wolno
 * być podzbiorem (świadomie pomijamy pola nieużywane w UI — Ktor je ignoruje),
 * ale nie wolno im czytać pola, którego backend już nie ma. To łapie właśnie
 * scenariusz „backend zmienił/usunął pole, a mobilka o tym nie wie" — inaczej
 * `ignoreUnknownKeys` po cichu podałby wartość domyślną zamiast danych.
 *
 * Gdy test padnie po odświeżeniu kontraktu: dostosuj wskazany model do nowego
 * kształtu DTO. Kontrakt odświeżasz kopiując plik z backendu (patrz README).
 */
class OpenApiContractTest {

	/** Model mobilny -> nazwa schematu w `components/schemas` backendu. */
	private val mapping: List<Pair<KSerializer<*>, String>> = listOf(
		RegisterRequest.serializer() to "RegisterRequest",
		LoginRequest.serializer() to "LoginRequest",
		RefreshRequest.serializer() to "RefreshRequest",
		DeleteAccountRequest.serializer() to "DeleteAccountRequest",
		AuthTokens.serializer() to "AuthTokensResponse",
		Post.serializer() to "PostResponse",
		CreatePostRequest.serializer() to "CreatePostRequest",
		PageMetadata.serializer() to "PageMetadata",
		Comment.serializer() to "CommentResponse",
		CreateCommentRequest.serializer() to "CreateCommentRequest",
		Friend.serializer() to "FriendResponse",
		FriendRequest.serializer() to "FriendRequestResponse",
		SendFriendRequestRequest.serializer() to "SendFriendRequestRequest",
		BlockedUser.serializer() to "BlockedUserResponse",
		UserSearchResult.serializer() to "UserSearchResponse",
		Notification.serializer() to "NotificationResponse",
		UnreadCount.serializer() to "UnreadCountResponse",
		Match.serializer() to "MatchResponse",
		Profile.serializer() to "ProfileResponse",
		Genre.serializer() to "GenreResponse",
		PublicProfile.serializer() to "PublicProfileResponse",
		UpdateProfileRequest.serializer() to "UpdateProfileRequest",
		SetFavoriteGenresRequest.serializer() to "SetFavoriteGenresRequest",
		Community.serializer() to "CommunityResponse",
		CommunityMember.serializer() to "CommunityMemberResponse",
		CreateCommunityRequest.serializer() to "CreateCommunityRequest",
		UpdateCommunityRequest.serializer() to "UpdateCommunityRequest",
		NowPlayingStatus.serializer() to "NowPlayingStatusResponse",
		ManualNowPlayingRequest.serializer() to "ManualNowPlayingRequest",
		ConnectResponse.serializer() to "ConnectResponse",
		ApiError.serializer() to "ApiError",
	)

	@Test
	fun everyModelFieldExistsInBackendSchema() {
		val schemas = loadSchemas()
		val violations = mutableListOf<String>()

		for ((serializer, schemaName) in mapping) {
			val schema = schemas[schemaName]?.jsonObject
			if (schema == null) {
				violations += "Brak schematu '$schemaName' w kontrakcie (model ${serializer.descriptor.serialName}) " +
					"— literówka w mapowaniu albo DTO usunięte z backendu."
				continue
			}
			val backendFields = schema["properties"]?.jsonObject?.keys.orEmpty()
			val descriptor = serializer.descriptor
			val modelFields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
			val missing = modelFields.filterNot { it in backendFields }
			if (missing.isNotEmpty()) {
				violations += "Model ${descriptor.serialName} (schemat $schemaName): pola $missing " +
					"nie istnieją w backendzie. Dostępne: ${backendFields.sorted()}"
			}
		}

		if (violations.isNotEmpty()) {
			fail("Dryf modeli mobilnych względem kontraktu OpenAPI:\n" + violations.joinToString("\n") { "  - $it" })
		}
	}

	private fun loadSchemas() = run {
		val path = System.getProperty("songly.openapi.contract")
			?: fail("Brak property 'songly.openapi.contract' — sprawdź testOptions w shared/build.gradle.kts")
		val file = File(path)
		if (!file.exists()) fail("Nie znaleziono kontraktu OpenAPI: $path")
		val root = Json.parseToJsonElement(file.readText()).jsonObject
		root["components"]?.jsonObject?.get("schemas")?.jsonObject
			?: fail("Kontrakt nie zawiera components/schemas")
	}
}

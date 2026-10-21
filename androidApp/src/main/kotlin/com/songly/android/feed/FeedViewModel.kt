package com.songly.android.feed

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.songly.shared.model.FriendNowPlaying
import com.songly.shared.model.Post
import com.songly.shared.model.SonglyApiException
import com.songly.shared.network.SonglyApiClient
import com.songly.shared.paging.plusDistinct
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20

/** Stan ekranu feedu z paginacją. */
data class FeedUiState(
	val isLoading: Boolean = false, // pierwsze wczytanie / odświeżenie (cała lista)
	val isLoadingMore: Boolean = false, // doczytywanie kolejnej strony
	val isRefreshing: Boolean = false, // odświeżanie gestem (lista zostaje na ekranie)
	val posts: List<Post> = emptyList(),
	val errorMessage: String? = null, // błąd pierwszego wczytania (pusta lista)
	val appendErrorMessage: String? = null, // błąd doczytywania (lista zostaje)
	// Chwilowy komunikat nad listą (snackbar): nieudane odświeżenie przy postach na
	// ekranie albo nieudane usunięcie. Nie blokuje doczytywania jak [appendErrorMessage].
	val snackbarMessage: String? = null,
	// Znajomi, którzy coś teraz grają — pasek nad feedem (best-effort, nie blokuje feedu).
	val friendsNowPlaying: List<FriendNowPlaying> = emptyList(),
	// Lista ma wrócić na górę (świeże posty po odświeżeniu gestem albo po dodaniu
	// posta) — ekran przewija ją i potwierdza przez [FeedViewModel.scrolledToTop].
	val scrollToTopPending: Boolean = false,
	val endReached: Boolean = false, // ostatnia strona wczytana
	val unreadNotifications: Long = 0, // do plakietki na dzwonku (best-effort, nie blokuje feedu)
	val currentUserId: String? = null, // do rozpoznania własnych postów (usuwanie)
	val pendingDeletePostId: String? = null, // post czekający na potwierdzenie usunięcia
	val isDeleting: Boolean = false,
	// Odświeżanie zwróciło 401 mimo auto-refreshu = refresh token też wygasł.
	val sessionExpired: Boolean = false,
	// Konto zawieszone w trakcie sesji — każde żądanie poza kilkoma dostaje 403
	// ACCOUNT_SUSPENDED (ADR 0022), więc feed i tak nie zadziała; przechodzimy do
	// ekranu zawieszenia zamiast pokazywać błąd bez wyjaśnienia.
	val accountSuspended: Boolean = false,
) {
	val isEmpty: Boolean
		get() = !isLoading && errorMessage == null && posts.isEmpty()
}

/**
 * Logika ekranu feedu z paginacją (infinite scroll). Woła [SonglyApiClient.feed]
 * z modułu `shared` (access token i refresh po 401 obsługuje warstwa sesji).
 *
 * [refresh] ładuje od strony 0 (odświeżenie), [loadNextPage] dokłada kolejną.
 * Flaga [loading] blokuje równoległe/zduplikowane żądania — infinite scroll
 * potrafi wyzwolić doczytanie wielokrotnie zanim pierwsze wróci.
 */
class FeedViewModel(private val client: SonglyApiClient) : ViewModel() {

	var uiState by mutableStateOf(FeedUiState(isLoading = true))
		private set

	private var nextPage = 0
	private var loading = false
	private var loaded = false

	/**
	 * Wejście na ekran. Za pierwszym razem pełne wczytanie; przy powrocie (z
	 * komentarzy, z innej zakładki) ciche odświeżenie pierwszej strony — lista
	 * zostaje tam, gdzie użytkownik ją zostawił, a liczniki i plakietka się
	 * aktualizują.
	 */
	fun onScreenShown() {
		if (loaded) {
			if (!loading) loadPage(reset = true, quiet = true)
		} else {
			refresh()
		}
	}

	/**
	 * Ładuje feed od początku (strona 0). [scrollToTop] po dodaniu posta: bez tego
	 * lista trzymałaby się posta, który był na górze, a nowy zostałby nad ekranem.
	 */
	fun refresh(scrollToTop: Boolean = false) {
		if (loading) return
		uiState = uiState.copy(isLoading = true, errorMessage = null)
		loadPage(reset = true, scrollToTop = scrollToTop)
	}

	/**
	 * Odświeżenie gestem: to samo żądanie co [refresh], ale bez chowania listy za
	 * spinnerem — przy pull-to-refresh użytkownik ma widzieć zawartość, którą
	 * właśnie ściąga w dół.
	 */
	fun pullToRefresh() {
		if (loading) return
		uiState = uiState.copy(isRefreshing = true, errorMessage = null)
		loadPage(reset = true, scrollToTop = true)
	}

	fun scrolledToTop() {
		uiState = uiState.copy(scrollToTopPending = false)
	}

	/** Dokłada kolejną stronę na koniec listy. Ignorowane, gdy nie ma czego/kiedy doczytywać. */
	fun loadNextPage() {
		if (loading || uiState.endReached || uiState.isLoading || uiState.appendErrorMessage != null) return
		uiState = uiState.copy(isLoadingMore = true)
		loadPage(reset = false)
	}

	/** Ponawia doczytywanie po błędzie strony (footer „Ponów"). */
	fun retryNextPage() {
		if (loading) return
		uiState = uiState.copy(appendErrorMessage = null, isLoadingMore = true)
		loadPage(reset = false)
	}

	/**
	 * Przełącza polubienie posta optymistycznie: najpierw aktualizuje UI, potem
	 * woła serwer; przy błędzie cofa zmianę. Lajk „czuje się" natychmiastowy, a
	 * spójność ze stanem serwera i tak wygrywa (rollback).
	 */
	fun toggleLike(post: Post) {
		val liked = !post.likedByMe
		updatePost(post.id) {
			it.copy(likedByMe = liked, likeCount = it.likeCount + if (liked) 1 else -1)
		}
		viewModelScope.launch {
			try {
				if (liked) client.likePost(post.id) else client.unlikePost(post.id)
			} catch (e: Exception) {
				// Rollback do stanu sprzed kliknięcia.
				updatePost(post.id) { it.copy(likedByMe = post.likedByMe, likeCount = post.likeCount) }
			}
		}
	}

	/** Przełącza udostępnienie posta optymistycznie (analogicznie do [toggleLike]). */
	fun toggleShare(post: Post) {
		val shared = !post.sharedByMe
		updatePost(post.id) {
			it.copy(sharedByMe = shared, shareCount = it.shareCount + if (shared) 1 else -1)
		}
		viewModelScope.launch {
			try {
				if (shared) client.sharePost(post.id) else client.unsharePost(post.id)
			} catch (e: Exception) {
				updatePost(post.id) { it.copy(sharedByMe = post.sharedByMe, shareCount = post.shareCount) }
			}
		}
	}

	/** Podbija licznik komentarzy posta po dodaniu komentarza na ekranie komentarzy. */
	fun incrementCommentCount(postId: String) {
		updatePost(postId) { it.copy(commentCount = it.commentCount + 1) }
	}

	/** Obniża licznik komentarzy posta po usunięciu komentarza (nie schodzi poniżej zera). */
	fun decrementCommentCount(postId: String) {
		updatePost(postId) { it.copy(commentCount = (it.commentCount - 1).coerceAtLeast(0)) }
	}

	/** Snackbar pokazany — bez tego wracałby przy każdej rekompozycji. */
	fun snackbarShown() {
		uiState = uiState.copy(snackbarMessage = null)
	}

	/** Prosi o potwierdzenie usunięcia posta (dialog). */
	fun requestDeletePost(postId: String) {
		uiState = uiState.copy(pendingDeletePostId = postId)
	}

	fun cancelDeletePost() {
		if (uiState.isDeleting) return
		uiState = uiState.copy(pendingDeletePostId = null)
	}

	/** Usuwa post po potwierdzeniu; po sukcesie znika z listy. */
	fun confirmDeletePost() {
		val postId = uiState.pendingDeletePostId ?: return
		if (uiState.isDeleting) return
		uiState = uiState.copy(isDeleting = true)
		viewModelScope.launch {
			try {
				client.deletePost(postId)
				uiState = uiState.copy(
					isDeleting = false,
					pendingDeletePostId = null,
					posts = uiState.posts.filterNot { it.id == postId },
				)
			} catch (e: SonglyApiException) {
				// Snackbar, nie stopka listy: stopka z „Ponów" ponawiałaby doczytywanie
				// strony, a nie usuwanie, i do tego blokowała infinite scroll.
				uiState = uiState.copy(
					isDeleting = false,
					pendingDeletePostId = null,
					snackbarMessage = e.error?.message ?: "Nie udało się usunąć posta (HTTP ${e.status})",
				)
			} catch (e: Exception) {
				uiState = uiState.copy(
					isDeleting = false,
					pendingDeletePostId = null,
					snackbarMessage = "Brak połączenia z serwerem.",
				)
			}
		}
	}

	private fun updatePost(postId: String, transform: (Post) -> Post) {
		uiState = uiState.copy(
			posts = uiState.posts.map { if (it.id == postId) transform(it) else it },
		)
	}

	// [quiet]: odświeżenie, o które nikt nie prosił (powrót na ekran) — jego błąd
	// nie zasługuje na komunikat przy każdym przełączeniu zakładki bez zasięgu.
	private fun loadPage(reset: Boolean, scrollToTop: Boolean = false, quiet: Boolean = false) {
		loading = true
		viewModelScope.launch {
			try {
				// Numer strony przestawiamy dopiero po sukcesie: nieudane odświeżenie
				// zostawia posty na ekranie, więc doczytywanie musi dalej iść od miejsca,
				// w którym lista się kończy, a nie od zera (dublowałoby pierwszą stronę).
				val page = client.feed(page = if (reset) 0 else nextPage, size = PAGE_SIZE)
				// Przy doczytanych dalszych stronach świeża pierwsza strona wymienia tylko
				// początek listy: obcięcie do 20 postów wyrzuciłoby użytkownika z miejsca,
				// w którym czyta (pozycja przewinięcia przeżywa wyjście z ekranu).
				val keepTail = reset && uiState.posts.size > page.content.size
				val merged = when {
					!reset -> uiState.posts.plusDistinct(page.content) { it.id }
					keepTail -> page.content.plusDistinct(uiState.posts) { it.id }
					else -> page.content
				}
				if (!keepTail) nextPage = page.number + 1
				// Licznik nieprzeczytanych odświeżamy tylko przy pełnym odświeżeniu i
				// best-effort — jego błąd nie może wywalić feedu.
				val unread = if (reset) {
					runCatching { client.unreadNotificationCount() }.getOrDefault(uiState.unreadNotifications)
				} else {
					uiState.unreadNotifications
				}
				// Id usera potrzebne tylko do pokazania usuwania własnych postów — best-effort.
				val userId = uiState.currentUserId
					?: runCatching { client.currentUserId() }.getOrNull()
				val nowPlaying = if (reset) {
					runCatching { client.friendsNowPlaying().filter { it.status != null } }
						.getOrDefault(uiState.friendsNowPlaying)
				} else {
					uiState.friendsNowPlaying
				}
				uiState = uiState.copy(
					isLoading = false,
					isLoadingMore = false,
					posts = merged,
					endReached = if (keepTail) uiState.endReached else page.last,
					unreadNotifications = unread,
					currentUserId = userId,
					friendsNowPlaying = nowPlaying,
					errorMessage = null,
					appendErrorMessage = if (keepTail) uiState.appendErrorMessage else null,
					accountSuspended = false,
					scrollToTopPending = uiState.scrollToTopPending || scrollToTop,
				)
				loaded = true
			} catch (e: SonglyApiException) {
				if (e.status == 401) {
					uiState = uiState.copy(isLoading = false, isLoadingMore = false, sessionExpired = true)
				} else if (e.error?.code == "ACCOUNT_SUSPENDED") {
					uiState = uiState.copy(isLoading = false, isLoadingMore = false, accountSuspended = true)
				} else {
					val message = e.error?.message ?: "Nie udało się wczytać feedu (HTTP ${e.status})"
					uiState = failure(reset, message, quiet)
				}
			} catch (e: Exception) {
				uiState = failure(reset, "Brak połączenia z serwerem. Spróbuj ponownie.", quiet)
			} finally {
				loading = false
				uiState = uiState.copy(isRefreshing = false)
			}
		}
	}

	// Błąd pierwszego wczytania (pusta lista) zasłania ekran (errorMessage). Błąd
	// odświeżenia przy postach na ekranie ich nie zabiera — chwilowy brak zasięgu
	// nie może kasować tego, co użytkownik właśnie czyta — więc idzie do snackbara.
	// Błąd doczytywania pokazuje stopkę z „Ponów" (appendErrorMessage).
	private fun failure(reset: Boolean, message: String, quiet: Boolean): FeedUiState = when {
		reset && uiState.posts.isEmpty() -> uiState.copy(isLoading = false, isLoadingMore = false, errorMessage = message)
		reset -> uiState.copy(
			isLoading = false,
			isLoadingMore = false,
			snackbarMessage = if (quiet) uiState.snackbarMessage else message,
		)
		else -> uiState.copy(isLoading = false, isLoadingMore = false, appendErrorMessage = message)
	}
}

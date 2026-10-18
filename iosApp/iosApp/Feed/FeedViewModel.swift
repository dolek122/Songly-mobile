import Foundation
import Shared

/// Post w wersji do wyświetlenia. Własna struktura zamiast kotlinowego `Post`, bo
/// lajk i udostępnienie zmieniają się optymistycznie, a `copy` z Kotlina wymaga w
/// Swifcie podania wszystkich pól naraz.
struct FeedPost: Identifiable, Equatable {
	let id: String
	let authorDisplayName: String
	let authorAvatarUrl: String?
	let content: String
	let createdAt: String
	let imageUrl: String?
	var likeCount: Int64
	var likedByMe: Bool
	let commentCount: Int64
	var shareCount: Int64
	var sharedByMe: Bool

	init(_ post: Post) {
		id = post.id
		authorDisplayName = post.authorDisplayName
		authorAvatarUrl = post.authorAvatarUrl
		content = post.content
		createdAt = post.createdAt
		imageUrl = post.imageUrl
		likeCount = post.likeCount
		likedByMe = post.likedByMe
		commentCount = post.commentCount
		shareCount = post.shareCount
		sharedByMe = post.sharedByMe
	}
}

/// Logika feedu — odpowiednik `FeedViewModel` z Androida: paginacja, odświeżanie
/// gestem, optymistyczne lajki i udostępnienia z wycofaniem przy błędzie.
@MainActor
final class FeedViewModel: ObservableObject {

	@Published private(set) var posts: [FeedPost] = []
	@Published private(set) var isLoading = true // pierwsze wczytanie (lista za spinnerem)
	@Published private(set) var isLoadingMore = false
	@Published private(set) var errorMessage: String? // błąd pierwszego wczytania
	@Published private(set) var appendErrorMessage: String? // błąd doczytywania (lista zostaje)
	@Published private(set) var endReached = false
	// 401 mimo automatycznego odświeżenia = refresh token też wygasł.
	@Published private(set) var sessionExpired = false
	// Zawieszone konto dostaje 403 ACCOUNT_SUSPENDED prawie wszędzie (ADR 0022).
	@Published private(set) var accountSuspended = false

	private static let pageSize: Int32 = 20

	private let client: SonglyApiClient
	private var nextPage: Int32 = 0
	// Blokuje równoległe żądania — przewijanie potrafi poprosić o stronę kilka razy,
	// zanim pierwsza wróci.
	private var loading = false
	private var loadedOnce = false

	init(client: SonglyApiClient) {
		self.client = client
	}

	var isEmpty: Bool {
		!isLoading && errorMessage == nil && posts.isEmpty
	}

	/// Pierwsze wczytanie przy wejściu na ekran — powrót na ekran nie przeładowuje listy.
	func loadIfNeeded() async {
		guard !loadedOnce else { return }
		loadedOnce = true
		await reload()
	}

	/// Od strony 0, z listą schowaną za spinnerem (przycisk „Spróbuj ponownie").
	func reload() async {
		guard !loading else { return }
		isLoading = true
		errorMessage = nil
		await loadPage(reset: true)
	}

	/// Odświeżenie gestem: lista zostaje na ekranie, spinner rysuje sam `refreshable`.
	func pullToRefresh() async {
		guard !loading else { return }
		await loadPage(reset: true)
	}

	func loadNextPage() async {
		guard !loading, !endReached, !isLoading, appendErrorMessage == nil else { return }
		isLoadingMore = true
		await loadPage(reset: false)
	}

	func retryNextPage() async {
		guard !loading else { return }
		appendErrorMessage = nil
		isLoadingMore = true
		await loadPage(reset: false)
	}

	func toggleLike(_ post: FeedPost) {
		let liked = !post.likedByMe
		update(post.id) {
			$0.likedByMe = liked
			$0.likeCount += liked ? 1 : -1
		}
		Task {
			do {
				if liked {
					try await client.likePost(postId: post.id)
				} else {
					try await client.unlikePost(postId: post.id)
				}
			} catch {
				update(post.id) {
					$0.likedByMe = post.likedByMe
					$0.likeCount = post.likeCount
				}
			}
		}
	}

	func toggleShare(_ post: FeedPost) {
		let shared = !post.sharedByMe
		update(post.id) {
			$0.sharedByMe = shared
			$0.shareCount += shared ? 1 : -1
		}
		Task {
			do {
				if shared {
					try await client.sharePost(postId: post.id)
				} else {
					try await client.unsharePost(postId: post.id)
				}
			} catch {
				update(post.id) {
					$0.sharedByMe = post.sharedByMe
					$0.shareCount = post.shareCount
				}
			}
		}
	}

	private func update(_ postId: String, _ change: (inout FeedPost) -> Void) {
		guard let index = posts.firstIndex(where: { $0.id == postId }) else { return }
		change(&posts[index])
	}

	private func loadPage(reset: Bool) async {
		loading = true
		defer {
			loading = false
			isLoading = false
			isLoadingMore = false
		}
		do {
			let page = try await client.feed(page: reset ? 0 : nextPage, size: Self.pageSize)
			let fresh = page.content.map(FeedPost.init)
			if reset {
				posts = fresh
			} else {
				// Paginacja po numerze strony przesuwa się, gdy w międzyczasie pojawi się
				// nowy post — ten sam post wraca wtedy na kolejnej stronie. Zdublowane id
				// psułoby `ForEach`, więc odsiewamy już pokazane.
				let known = Set(posts.map(\.id))
				posts += fresh.filter { !known.contains($0.id) }
			}
			nextPage = page.number + 1
			endReached = page.last
			errorMessage = nil
			appendErrorMessage = nil
		} catch {
			let api = ApiFailure.apiException(error)
			if api?.status == 401 {
				sessionExpired = true
			} else if api?.error?.code == "ACCOUNT_SUSPENDED" {
				accountSuspended = true
			} else {
				let message = ApiFailure.message(error, action: "wczytać feedu")
				if reset {
					// Odświeżenie gestem przy pokazanej liście nie chowa jej — błąd
					// pokazujemy pod listą, jak przy doczytywaniu.
					if posts.isEmpty { errorMessage = message } else { appendErrorMessage = message }
				} else {
					appendErrorMessage = message
				}
			}
		}
	}
}

import SwiftUI
import Shared

/// Feed znajomych z paginacją i odświeżaniem gestem.
struct FeedView: View {
	@EnvironmentObject private var session: AppSession
	@StateObject private var viewModel: FeedViewModel

	init(client: SonglyApiClient) {
		_viewModel = StateObject(wrappedValue: FeedViewModel(client: client))
	}

	var body: some View {
		NavigationStack {
			content
				.navigationTitle("Songly")
				.toolbar {
					ToolbarItem(placement: .navigationBarTrailing) {
						Button("Wyloguj") {
							Task { await session.logout() }
						}
					}
				}
		}
		.task { await viewModel.loadIfNeeded() }
		.onChange(of: viewModel.sessionExpired) { expired in
			if expired {
				Task { await session.logout(notice: "Sesja wygasła. Zaloguj się ponownie.") }
			}
		}
	}

	@ViewBuilder
	private var content: some View {
		if viewModel.accountSuspended {
			// Pełny ekran zawieszenia (powód, termin, odwołanie — ADR 0022/0023) jest na
			// razie tylko na Androidzie; tu przynajmniej mówimy, co się stało.
			CenteredMessage(
				text: "Twoje konto jest zawieszone.",
				buttonTitle: "Wyloguj"
			) {
				Task { await session.logout() }
			}
		} else if viewModel.isLoading && viewModel.posts.isEmpty {
			ProgressView()
		} else if let error = viewModel.errorMessage {
			CenteredMessage(text: error, buttonTitle: "Spróbuj ponownie") {
				Task { await viewModel.reload() }
			}
		} else if viewModel.isEmpty {
			CenteredMessage(text: "Brak postów w feedzie.", buttonTitle: "Odśwież") {
				Task { await viewModel.reload() }
			}
		} else {
			list
		}
	}

	private var list: some View {
		List {
			ForEach(viewModel.posts) { post in
				PostRow(
					post: post,
					onToggleLike: { viewModel.toggleLike(post) },
					onToggleShare: { viewModel.toggleShare(post) }
				)
				.onAppear {
					// Doczytujemy, gdy pokazuje się ostatni wiersz — jak infinite scroll na Androidzie.
					if post.id == viewModel.posts.last?.id {
						Task { await viewModel.loadNextPage() }
					}
				}
			}

			if viewModel.isLoadingMore {
				ProgressView()
					.frame(maxWidth: .infinity)
					.listRowSeparator(.hidden)
			} else if let error = viewModel.appendErrorMessage {
				VStack(spacing: 4) {
					Text(error).font(.footnote)
					Button("Ponów") {
						Task { await viewModel.retryNextPage() }
					}
					.buttonStyle(.borderless)
				}
				.frame(maxWidth: .infinity)
				.listRowSeparator(.hidden)
			}
		}
		.listStyle(.plain)
		.refreshable { await viewModel.pullToRefresh() }
	}
}

private struct CenteredMessage: View {
	let text: String
	let buttonTitle: String
	let action: () -> Void

	var body: some View {
		VStack(spacing: 12) {
			Text(text)
				.multilineTextAlignment(.center)
			Button(buttonTitle, action: action)
				.buttonStyle(.borderedProminent)
		}
		.padding(24)
	}
}

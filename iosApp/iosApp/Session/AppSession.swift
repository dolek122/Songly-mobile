import Foundation
import Shared

@MainActor
final class AppSession: ObservableObject {

	enum State: Equatable {
		case checking
		case loggedOut(notice: String?)
		case loggedIn
	}

	@Published private(set) var state: State = .checking

	let client: SonglyApiClient

	init() {
		KeychainTokenStorage.clearIfFreshInstall()
		#if DEBUG
		let config = ApiConfig.companion.LOCAL_HOST
		#else
		let config = ApiConfig.companion.STAGING
		#endif
		client = SonglyApiClient(config: config, storage: KeychainTokenStorage())
	}

	func restore() async {
		let loggedIn = (try? await client.isLoggedIn())?.boolValue ?? false
		state = loggedIn ? .loggedIn : .loggedOut(notice: nil)
	}

	func didLogIn() {
		state = .loggedIn
	}

	func logout(notice: String? = nil) async {
		try? await client.logout()
		state = .loggedOut(notice: notice)
	}
}

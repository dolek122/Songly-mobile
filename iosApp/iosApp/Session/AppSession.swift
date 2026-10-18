import Foundation
import Shared

/// Stan sesji całej aplikacji: sprawdzamy / wylogowany / zalogowany. Jedyny właściciel
/// `SonglyApiClient` — ekrany dostają go stąd, żeby wszystkie dzieliły jedną sesję
/// i jedno odświeżanie tokenów.
@MainActor
final class AppSession: ObservableObject {

	enum State: Equatable {
		case checking
		/// `notice` wyjaśnia, dlaczego użytkownik wylądował na logowaniu (np. wygasła sesja).
		case loggedOut(notice: String?)
		case loggedIn
	}

	@Published private(set) var state: State = .checking

	let client: SonglyApiClient

	init() {
		KeychainTokenStorage.clearIfFreshInstall()
		#if DEBUG
		// Symulator widzi backend hosta pod localhost (Android potrzebuje 10.0.2.2).
		let config = ApiConfig.companion.LOCAL_HOST
		#else
		let config = ApiConfig.companion.STAGING
		#endif
		client = SonglyApiClient(config: config, storage: KeychainTokenStorage())
	}

	/// Zapisane tokeny nie gwarantują ważnej sesji — wygasłą wykryje pierwsze żądanie
	/// (401 po nieudanym odświeżeniu) i odeśle do logowania z komunikatem.
	func restore() async {
		let loggedIn = (try? await client.isLoggedIn())?.boolValue ?? false
		state = loggedIn ? .loggedIn : .loggedOut(notice: nil)
	}

	func didLogIn() {
		state = .loggedIn
	}

	/// Wylogowanie jest best-effort po stronie serwera; lokalnie kończy sesję zawsze.
	func logout(notice: String? = nil) async {
		try? await client.logout()
		state = .loggedOut(notice: notice)
	}
}

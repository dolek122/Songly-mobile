import Foundation
import Shared

/// Wyjątki z `shared` docierają do Swifta jako `NSError`, a oryginalny wyjątek Kotlina
/// leży w `userInfo["KotlinException"]`. Działa to wyłącznie dla metod z
/// `@Throws(Exception::class)` — bez adnotacji wyjątek kończy proces aplikacji
/// (pilnuje tego `SwiftInteropTest` w `shared`).
enum ApiFailure {

	static func apiException(_ error: Error) -> SonglyApiException? {
		(error as NSError).userInfo["KotlinException"] as? SonglyApiException
	}

	/// Komunikat dla użytkownika — te same zasady co na Androidzie: treść `ApiError`
	/// z serwera, a gdy odpowiedzi nie było, osobny komunikat o braku połączenia.
	static func message(_ error: Error, action: String) -> String {
		if let api = apiException(error) {
			return api.error?.message ?? "Nie udało się \(action) (HTTP \(api.status))"
		}
		return "Brak połączenia z serwerem. Spróbuj ponownie."
	}
}

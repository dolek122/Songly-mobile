import Foundation
import Shared

enum ApiFailure {

	static func apiException(_ error: Error) -> SonglyApiException? {
		(error as NSError).userInfo["KotlinException"] as? SonglyApiException
	}

	static func message(_ error: Error, action: String) -> String {
		if let api = apiException(error) {
			return api.error?.message ?? "Nie udało się \(action) (HTTP \(api.status))"
		}
		return "Brak połączenia z serwerem. Spróbuj ponownie."
	}
}

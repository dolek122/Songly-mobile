import Foundation
import Shared

/// Logika ekranu logowania — odpowiednik `LoginViewModel` z Androida.
@MainActor
final class LoginViewModel: ObservableObject {

	@Published var email = "" {
		didSet { errorMessage = nil }
	}
	@Published var password = "" {
		didSet { errorMessage = nil }
	}
	@Published private(set) var isSubmitting = false
	@Published private(set) var errorMessage: String?

	private let client: SonglyApiClient

	init(client: SonglyApiClient) {
		self.client = client
	}

	var canSubmit: Bool {
		!trimmedEmail.isEmpty && !password.isEmpty && !isSubmitting
	}

	private var trimmedEmail: String {
		email.trimmingCharacters(in: .whitespacesAndNewlines)
	}

	/// Zwraca `true`, gdy sesja powstała — tokeny zapisał już klient w `shared`.
	func submit() async -> Bool {
		guard canSubmit else { return false }
		isSubmitting = true
		defer { isSubmitting = false }
		do {
			_ = try await client.login(email: trimmedEmail, password: password)
			return true
		} catch {
			errorMessage = ApiFailure.message(error, action: "zalogować")
			return false
		}
	}
}

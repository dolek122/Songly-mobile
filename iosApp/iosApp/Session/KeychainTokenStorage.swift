import Foundation
import Security
import Shared

final class KeychainTokenStorage: NSObject, TokenStorage {

	private static let baseQuery: [String: Any] = [
		kSecClass as String: kSecClassGenericPassword,
		kSecAttrService as String: "com.songly.session",
		kSecAttrAccount as String: "tokens",
	]

	static func clearIfFreshInstall(defaults: UserDefaults = .standard) {
		let key = "songly.installed"
		guard !defaults.bool(forKey: key) else { return }
		SecItemDelete(baseQuery as CFDictionary)
		defaults.set(true, forKey: key)
	}

	func getTokens(completionHandler: @escaping (AuthTokens?, Error?) -> Void) {
		completionHandler(read(), nil)
	}

	func saveTokens(tokens: AuthTokens, completionHandler: @escaping (Error?) -> Void) {
		write(tokens)
		completionHandler(nil)
	}

	func clear(completionHandler: @escaping (Error?) -> Void) {
		SecItemDelete(Self.baseQuery as CFDictionary)
		completionHandler(nil)
	}

	private func read() -> AuthTokens? {
		var query = Self.baseQuery
		query[kSecReturnData as String] = true
		query[kSecMatchLimit as String] = kSecMatchLimitOne
		var item: CFTypeRef?
		guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else {
			return nil
		}
		guard let stored = try? JSONDecoder().decode(StoredTokens.self, from: data) else {
			SecItemDelete(Self.baseQuery as CFDictionary)
			return nil
		}
		return AuthTokens(
			accessToken: stored.accessToken,
			refreshToken: stored.refreshToken,
			tokenType: stored.tokenType,
			expiresInSeconds: stored.expiresInSeconds
		)
	}

	private func write(_ tokens: AuthTokens) {
		let stored = StoredTokens(
			accessToken: tokens.accessToken,
			refreshToken: tokens.refreshToken,
			tokenType: tokens.tokenType,
			expiresInSeconds: tokens.expiresInSeconds
		)
		guard let data = try? JSONEncoder().encode(stored) else { return }
		SecItemDelete(Self.baseQuery as CFDictionary)
		var attributes = Self.baseQuery
		attributes[kSecValueData as String] = data
		attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
		SecItemAdd(attributes as CFDictionary, nil)
	}

	private struct StoredTokens: Codable {
		let accessToken: String
		let refreshToken: String
		let tokenType: String
		let expiresInSeconds: Int64
	}
}

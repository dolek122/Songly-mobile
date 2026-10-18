import SwiftUI
import Shared

/// Ekran logowania. Rejestracja i reset hasła są na razie tylko na Androidzie.
struct LoginView: View {
	@StateObject private var viewModel: LoginViewModel
	@FocusState private var focusedField: Field?

	private let notice: String?
	private let onLoggedIn: () -> Void

	private enum Field {
		case email, password
	}

	init(client: SonglyApiClient, notice: String?, onLoggedIn: @escaping () -> Void) {
		_viewModel = StateObject(wrappedValue: LoginViewModel(client: client))
		self.notice = notice
		self.onLoggedIn = onLoggedIn
	}

	var body: some View {
		NavigationStack {
			Form {
				if let notice {
					Section {
						Text(notice).foregroundStyle(.secondary)
					}
				}

				Section {
					TextField("E-mail", text: $viewModel.email)
						.textContentType(.username)
						.keyboardType(.emailAddress)
						.textInputAutocapitalization(.never)
						.autocorrectionDisabled()
						.focused($focusedField, equals: .email)
						.submitLabel(.next)
						.onSubmit { focusedField = .password }

					SecureField("Hasło", text: $viewModel.password)
						.textContentType(.password)
						.focused($focusedField, equals: .password)
						.submitLabel(.go)
						.onSubmit(submit)
				}

				if let error = viewModel.errorMessage {
					Section {
						Text(error).foregroundStyle(.red)
					}
				}

				Section {
					Button(action: submit) {
						Group {
							if viewModel.isSubmitting {
								ProgressView()
							} else {
								Text("Zaloguj się")
							}
						}
						.frame(maxWidth: .infinity)
					}
					.disabled(!viewModel.canSubmit)
				}
			}
			.navigationTitle("Songly")
		}
	}

	private func submit() {
		Task {
			if await viewModel.submit() {
				onLoggedIn()
			}
		}
	}
}

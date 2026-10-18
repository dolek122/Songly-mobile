import SwiftUI

// Punkt wejścia aplikacji iOS. UI natywny (SwiftUI, §14); logika sieci/domeny
// pochodzi z frameworka `Shared` (moduł KMP `shared`), współdzielona z Androidem.
@main
struct iOSApp: App {
	@StateObject private var session = AppSession()

	var body: some Scene {
		WindowGroup {
			RootView()
				.environmentObject(session)
		}
	}
}

/// Przełącza ekrany według stanu sesji. Na razie dwa: logowanie i feed.
struct RootView: View {
	@EnvironmentObject private var session: AppSession

	var body: some View {
		switch session.state {
		case .checking:
			ProgressView()
				.task { await session.restore() }

		case .loggedOut(let notice):
			LoginView(client: session.client, notice: notice) { session.didLogIn() }

		case .loggedIn:
			FeedView(client: session.client)
		}
	}
}

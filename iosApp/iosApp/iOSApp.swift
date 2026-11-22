import SwiftUI

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

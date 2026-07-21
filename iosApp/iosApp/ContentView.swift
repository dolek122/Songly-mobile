import SwiftUI
import Shared

// Pierwszy realny ekran (logowanie) dołożymy, gdy potwierdzimy URL stagingu i
// warstwę sesji tokenów. Na razie szkielet pokazujący, że framework `Shared`
// (KMP) linkuje się poprawnie — czytamy konfigurację backendu z modułu wspólnego.
struct ContentView: View {
	private let apiConfig = ApiConfig.companion.LOCAL_HOST

	var body: some View {
		VStack(spacing: 12) {
			Text("Songly")
				.font(.largeTitle)
				.bold()
			Text("Szkielet mobilki gotowy.\nBackend: \(apiConfig.baseUrl)")
				.multilineTextAlignment(.center)
				.foregroundStyle(.secondary)
		}
		.padding()
	}
}

#Preview {
	ContentView()
}

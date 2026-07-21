import SwiftUI

// Punkt wejścia aplikacji iOS. UI natywny (SwiftUI, §14); logika sieci/domeny
// pochodzi z frameworka `Shared` (moduł KMP `shared`), współdzielona z Androidem.
@main
struct iOSApp: App {
	var body: some Scene {
		WindowGroup {
			ContentView()
		}
	}
}

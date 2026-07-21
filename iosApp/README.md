# iosApp

Natywny UI iOS (SwiftUI). Logika sieci/domeny pochodzi z frameworka `Shared`
(moduł KMP `shared`).

## Projekt Xcode nie jest w repo — utwórz go raz lokalnie

`.xcodeproj` to plik generowany przez Xcode; nie tworzymy go ręcznie. Kroki:

1. Otwórz **Android Studio** z wtyczką **Kotlin Multiplatform** (lub użyj kreatora
   KMP), albo w Xcode: **File → New → Project → iOS App**, nazwa produktu `iosApp`,
   interfejs **SwiftUI**, język **Swift**. Zapisz projekt tak, aby `iOSApp.swift`
   i `ContentView.swift` z tego katalogu były jego źródłami (usuń wygenerowane
   duplikaty, dodaj te pliki do targetu).

2. Podłącz framework `Shared` z modułu `:shared`. Najprościej przez skrypt build
   phase „Run Script" wywołujący `embedAndSignAppleFrameworkForXcode` z Gradle:

   ```
   cd "$SRCROOT/.."
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```

   Ustaw **Framework Search Paths** na katalog wygenerowanego frameworka i dodaj
   `import Shared` (już jest w `ContentView.swift`).

3. Zbuduj i uruchom na symulatorze. Framework `Shared` udostępnia m.in.
   `ApiConfig` i `SonglyApiClient` z modułu wspólnego.

> Gdy tylko utworzysz `iosApp.xcodeproj`, zacommituj go — od tego momentu jest
> częścią repo.

# iosApp

Natywny UI iOS (SwiftUI). Logika sieci/domeny pochodzi z frameworka `Shared`
(moduł KMP `shared`).

## Co już jest

Logowanie i feed (paginacja, odświeżanie gestem, lajki i udostępnienia). Pozostałe
ekrany są na razie tylko na Androidzie.

```
iosApp/
├── iOSApp.swift          # punkt wejścia + RootView (przełącza ekrany po stanie sesji)
├── Info.plist            # wyjątek ATS dla localhost — patrz niżej
├── Session/              # AppSession (właściciel SonglyApiClient), Keychain, mapowanie błędów
├── Auth/                 # logowanie
└── Feed/                 # feed, wiersz posta, awatar
```

## Projekt Xcode nie jest w repo — utwórz go raz lokalnie

`.xcodeproj` to plik generowany przez Xcode; nie tworzymy go ręcznie. Kroki:

1. W Xcode: **File → New → Project → iOS App**, nazwa produktu `iosApp`, interfejs
   **SwiftUI**, język **Swift**, minimalny iOS **16**. Usuń wygenerowane
   `ContentView.swift`/`iosAppApp.swift` i dodaj do targetu **cały katalog**
   `iosApp/iosApp` (z podkatalogami).

2. Podłącz framework `Shared` z modułu `:shared` przez build phase „Run Script"
   (przed „Compile Sources"):

   ```
   cd "$SRCROOT/.."
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```

   Ustaw **Framework Search Paths** na
   `$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`
   i **Other Linker Flags** na `-framework Shared`.

3. **Info.plist File** (build setting `INFOPLIST_FILE`) ustaw na `iosApp/Info.plist`.
   Bez niego App Transport Security zablokuje `http://localhost:8080` i logowanie
   skończy się „Brak połączenia z serwerem".

4. Uruchom backend lokalnie (`docker compose up -d`, potem `spring-boot:run`) i
   aplikację na symulatorze. Build Debug łączy się z `ApiConfig.LOCAL_HOST`
   (`http://localhost:8080`), Release ze stagingiem.

> Gdy tylko utworzysz `iosApp.xcodeproj`, zacommituj go — od tego momentu jest
> częścią repo.

## Na czym łatwo się potknąć

- **Każda publiczna metoda `suspend` w `SonglyApiClient` musi mieć
  `@Throws(Exception::class)`.** Bez tego wyjątek z Kotlina (zły login, brak sieci)
  nie trafia do Swiftowego `catch`, tylko kończy proces aplikacji. Pilnuje tego
  `SwiftInteropTest` w `shared` — łapie brak adnotacji już na Windowsie, zanim
  ktokolwiek uruchomi iOS.
- Błąd z serwera wyciąga `ApiFailure.apiException(_:)` — oryginalny
  `SonglyApiException` leży w `NSError.userInfo["KotlinException"]`.
- **Profil `local` backendu podaje adresy zdjęć z `10.0.2.2`** (host widziany z
  emulatora Androida). Symulator iOS ich nie otworzy — awatary pokażą inicjały, a
  zdjęć w postach nie będzie. Do testów na iOS zostaw profil (daje konta testowe
  `admin@songly.local` / `user@songly.local`) i nadpisz tylko adres:
  `STORAGE_PUBLIC_BASE_URL=http://localhost:9000/songly-media`.
- Sesja leży w Keychainie, który na iOS **przeżywa odinstalowanie aplikacji**.
  `KeychainTokenStorage.clearIfFreshInstall()` czyści ją przy pierwszym uruchomieniu
  po instalacji. Bez tego reinstalacja nie wylogowywałaby.

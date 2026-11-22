# Songly — mobile

Aplikacja mobilna Songly: Kotlin Multiplatform (`shared`: modele i klient REST) oraz natywny UI — Jetpack Compose (`androidApp`) i SwiftUI (`iosApp`). Backend: [Songly-backend](https://github.com/dolek122/Songly-backend).

## Uruchomienie

Wymagania: JDK 17–21, Android Studio, Xcode (iOS, tylko macOS).

**Android:** otwórz projekt w Android Studio i uruchom `androidApp`. Emulator łączy się z lokalnym backendem pod `http://10.0.2.2:8080`.

**iOS:** projektu Xcode nie ma w repo, utwórz go raz:

1. Nowy projekt iOS App `iosApp` (SwiftUI, iOS 16) i dodaj do targetu katalog `iosApp/iosApp`.
2. Build phase „Run Script” przed „Compile Sources”: `cd "$SRCROOT/.." && ./gradlew :shared:embedAndSignAppleFrameworkForXcode`.
3. Framework Search Paths: `$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`, Other Linker Flags: `-framework Shared`, `INFOPLIST_FILE`: `iosApp/Info.plist`.

## Testy

```
./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest
```

Po zmianie API w backendzie odśwież kontrakt i uruchom testy:

```
cp ../Songly-backend/docs/api/openapi.json shared/contract/openapi.json
```

## Push

Bez `google-services.json` aplikacja działa bez pushy. Żeby je włączyć, połóż plik z Firebase w `androidApp/`, a na backendzie ustaw `PUSH_PROVIDER=fcm` i `FCM_CREDENTIALS_JSON`.

## Wydanie

```
./gradlew :androidApp:bundleRelease
```

Klucz podpisu: `SONGLY_KEYSTORE_FILE`, `SONGLY_KEYSTORE_PASSWORD`, `SONGLY_KEY_ALIAS`, `SONGLY_KEY_PASSWORD` w `~/.gradle/gradle.properties`. Każde wydanie wymaga wyższego `versionCode`.

# Songly — mobile

Aplikacja mobilna Songly. **Kotlin Multiplatform (KMP) dla logiki i sieci,
natywny UI** — Jetpack Compose na Androidzie, SwiftUI na iOS (zgodnie z §14
blueprintu w repo `Songly-backend`).

## Struktura

```
Songly-mobile/
├── shared/        # KMP: modele DTO, klient REST (Ktor), konfiguracja — wspólne dla obu platform
│   ├── commonMain # logika niezależna od platformy
│   ├── androidMain # silnik HTTP OkHttp
│   └── iosMain    # silnik HTTP Darwin
├── androidApp/    # aplikacja Android (Jetpack Compose)
└── iosApp/        # aplikacja iOS (SwiftUI) — projekt Xcode tworzony lokalnie, patrz iosApp/README.md
```

## Dlaczego KMP + natywny UI (a nie wszystko wspólne)

Mniej ryzyka na pierwszym produkcie: natywny UI daje pełną kontrolę nad rzeczami
platform-specific (powiadomienia, deep linki, integracje systemowe), a wspólny
`shared` eliminuje duplikację tam, gdzie boli najbardziej — w modelach i wywołaniach
API. Compose Multiplatform dla UI to naturalny kolejny krok, gdy MVP się sprawdzi.

## Wygląd (Android)

Ciemny, zielono-czarny motyw — zawsze ciemny, niezależnie od ustawienia systemu.
Paleta jest w `ui/theme/Color.kt` (`SonglyPalette`), a jej kontrast liczy
`PaletteContrastTest`, więc zmiana odcienia, po której tekst przestaje być czytelny,
wywali testy. Zieleń jest tylko dla akcji i stanów (główny przycisk, polubienie,
„na żywo"); reszta to czerń i szarości.

Znakiem rozpoznawczym jest **kropla** — koło z jednym ostrym rogiem w prawym górnym
rogu (`TeardropShape` w `ui/Brand.kt`). Ma ją ikona aplikacji, logo (`SonglyMark`:
zielona kropla z korektorem), przycisk głównej akcji, okładka „teraz gram" i kafelki
społeczności. Za zakładkami jest wspólna zielona poświata gasnąca w czerń
(`songlyBackdrop`), a paski zakładek są przezroczyste. To cały „ozdobnik" — reszta
zostaje minimalistyczna.

Nawigacja to dolny pasek z pięcioma zakładkami (`ui/MainNavigation.kt`): Główna,
Muzyka, Społeczności, Znajomi, Profil. Powiadomienia są pod dzwonkiem na głównej
(nad postami pasek „Teraz słuchają" ze znajomymi, którzy coś grają), dopasowani —
w zakładce Znajomi, ustawienia i wylogowanie — w zakładce Profil. Pozostałe ekrany
otwierają się nad zakładkami ze strzałką wstecz; pozycja na liście i wpisany tekst
przeżywają wyjście z ekranu i powrót (osobny `SaveableStateHolder` na ekrany i na
zakładki w `MainActivity`). Wspólne klocki (strzałka wstecz, pasek zakładki, przycisk
akcji, wiersz nawigacyjny, pusty stan, pigułka filtra) są w `ui/Components.kt`.

Aplikacja rysuje się od krawędzi do krawędzi na każdej wersji Androida (na 15+
system i tak to wymusza). Odstęp od klawiatury daje jeden `imePadding()` u góry
drzewa, a dolny pasek chowa się przy otwartej klawiaturze. Ekrany bez Scaffolda
(logowanie, zawieszone konto) same dodają odstęp od pasków systemu.

Ikona aplikacji to ikona adaptacyjna: czarne tło (`ic_launcher_background` w
`res/values/colors.xml`) i zielona kropla z wyciętym korektorem
(`res/drawable/ic_launcher_foreground.xml`) — te same proporcje co `SonglyMark`.
Słupki są wycięte (`evenOdd`), więc ten sam rysunek jest warstwą monochromatyczną i na
Androidzie 13+ ikona przyjmuje kolory motywu. Ikona pushy (`ic_stat_songly`) to ta
sama kropla z trzema słupkami. Na Androidzie 8–11 okno startowe pokazuje kroplę na
czerni (`Theme.Songly.Starting` + `splash_background`), na 12+ robi to systemowy ekran
powitalny. Wersja 512×512 do karty sklepu leży w
`androidApp/src/main/ic_launcher-playstore.png`.

## Kontrakt z backendem

Modele w `shared/.../model` odwzorowują DTO z backendu 1:1. Źródłem prawdy jest
kontrakt OpenAPI generowany w repo `Songly-backend` (`docs/api/openapi.json`) —
przy zmianie API po stronie serwera zaktualizuj modele tutaj. Format błędów jest
spójny (`ApiError`: `code`, `message`, `timestamp`, `path`), więc obsługa błędów
opiera się na jednym parserze.

## Presence na żywo (co grają znajomi)

`SonglyApiClient.friendsPresence()` zwraca `Flow<FriendsPresence>` napędzany strumieniem
SSE `GET /api/v1/now-playing/stream` (ADR 0007 backendu). Całość — podłączenie, migawka
startowa z `GET /now-playing/friends`, scalanie zmian, reconnect z backoffem 1→30 s —
siedzi w `shared`, więc iOS dostanie to samo zachowanie bez przepisywania logiki. UI tylko
zbiera kolejne migawki.

Rzeczy, o które łatwo się potknąć przy zmianach:
- `status == null` w zdarzeniu znaczy **„przestał słuchać"**, czyli zdejmij znajomego z
  listy (a nie „brak danych"),
- strumień leci z dłuższym socket timeoutem niż zwykłe REST-y — domyślne 10 s OkHttpa jest
  krótsze niż 20-sekundowy heartbeat backendu, więc bez tego zdrowe, tylko ciche połączenie
  zrywałoby się w kółko,
- rozłączenie **nie czyści listy** — ostatnio znane statusy zostają z oznaczeniem „offline",
  bo pusty ekran przy chwilowej utracie sieci wygląda jak „nikt nic nie słucha",
- backend limituje liczbę równoległych strumieni na użytkownika (429 `TOO_MANY_STREAMS`),
  więc trzymamy jeden na sesję i zamykamy go razem z ekranem.

## Sesja i tokeny

`SonglyApiClient` (w `shared`) obsługuje pełną sesję:
- `register` / `login` zapisują access + refresh token do `TokenStorage`,
- każde żądanie uwierzytelnione dostaje access token automatycznie (wtyczka Ktor Auth),
- po odpowiedzi `401` klient sam woła `/auth/refresh` refresh tokenem, zapisuje nowe
  tokeny i ponawia oryginalne żądanie — kod ekranów nie dotyka tokenów,
- `logout` unieważnia refresh token na serwerze i czyści lokalny stan.

> **Świadomy skrót:** domyślny `InMemoryTokenStorage` gubi sesję po restarcie aplikacji.
> Przed pierwszym realnym wydaniem podłącz magazyn platformowy (Android: EncryptedSharedPreferences/Keystore, iOS: Keychain) — `TokenStorage` to interfejs właśnie po to.

## Uruchomienie

**Wymagania:** JDK 17–21 (nowsze, np. 26, psują build — najprościej użyć JBR wbudowanego w Android Studio), Android Studio (z wtyczką KMP), Xcode (dla iOS, tylko na macOS).

1. **Wrapper Gradle:** repo nie zawiera binarki `gradle-wrapper.jar`. Wygeneruj ją raz:
   ```
   gradle wrapper --gradle-version 8.11.1
   ```
   (albo po prostu otwórz projekt w Android Studio — zrobi to za Ciebie).

2. **Android:** otwórz w Android Studio, wybierz konfigurację `androidApp`, uruchom
   na emulatorze. Backend dev jest widziany jako `http://10.0.2.2:8080`
   (patrz `ApiConfig.LOCAL_ANDROID_EMULATOR`).

3. **iOS:** patrz `iosApp/README.md` — projekt Xcode tworzysz lokalnie i linkujesz
   framework `Shared`.

## Konfiguracja środowiska

`ApiConfig` (w `shared`) trzyma URL backendu per środowisko: `LOCAL_ANDROID_EMULATOR`,
`LOCAL_HOST`, `STAGING`. Staging działa pod `https://songly-backend-q7zm.onrender.com`
(Render free tier — usypia po ~15 min, pierwszy request po przebudzeniu trwa ~30–60 s).
Klient czeka na odpowiedź do 90 s (`SOCKET_TIMEOUT_MILLIS`), żeby budzący się serwer nie
kończył się komunikatem „Brak połączenia".

## Powiadomienia push (Firebase)

Push działa przez Firebase Cloud Messaging (backend: ADR 0040). Bez konfiguracji
Firebase aplikacja buduje się i działa normalnie, tylko bez pushy — tak buduje ją CI.

Włączenie:

1. W [konsoli Firebase](https://console.firebase.google.com) załóż projekt i dodaj
   aplikację Android z pakietem `com.songly.android`.
2. Pobierz `google-services.json` i połóż go w `androidApp/` (jest w `.gitignore`).
   Gradle sam włączy wtedy plugin `google-services`.
3. W ustawieniach projektu → Konta usługi wygeneruj klucz prywatny (JSON) i ustaw go
   na backendzie: `PUSH_PROVIDER=fcm` oraz `FCM_CREDENTIALS_JSON` = cała treść pliku.

Po zalogowaniu aplikacja zgłasza token urządzenia i (Android 13+) prosi o zgodę na
powiadomienia. Wylogowanie wyrejestrowuje urządzenie, a zmiana hasła odpina je po
stronie backendu. iOS nie rejestruje się jeszcze — wymaga APNs, czyli konta Apple
Developer i Maca.

## Wydanie (Google Play)

Release łączy się ze stagingiem, przechodzi przez R8 (zmniejszanie kodu i zasobów) i jest
podpisywany kluczem spoza repo. Klucz wygeneruj raz i trzymaj w bezpiecznym miejscu (Play
App Signing pozwala go zresetować, ale to kilka dni czekania na Google):

```
keytool -genkeypair -v -keystore songly-upload.jks -keyalg RSA -keysize 2048 -validity 10000 -alias songly-upload
```

Ścieżka i hasła idą do `~/.gradle/gradle.properties` (albo do zmiennych środowiskowych
o tych samych nazwach — tak w CI):

```
SONGLY_KEYSTORE_FILE=C:/sciezka/songly-upload.jks
SONGLY_KEYSTORE_PASSWORD=...
SONGLY_KEY_ALIAS=songly-upload
SONGLY_KEY_PASSWORD=...
```

Paczka do sklepu: `./gradlew :androidApp:bundleRelease` →
`androidApp/build/outputs/bundle/release/androidApp-release.aab`. Każde wgranie do Play
wymaga wyższego `versionCode` w `androidApp/build.gradle.kts`.

Bez klucza release buduje się niepodpisany — CI sprawdza w ten sposób, że R8 przechodzi.
Żeby przeklikać build release na emulatorze, można go podpisać kluczem debug
(`~/.android/debug.keystore`, alias `androiddebugkey`, hasła `android`).

## Testy i kontrakt API

Testy współdzielonej logiki (`shared`) uruchamiasz na targecie Android/JVM:

```
./gradlew :shared:testDebugUnitTest
```

CI (GitHub Actions) odpala je razem z buildem aplikacji na każdy push/PR do `main`.

**Detekcja dryfu względem backendu.** `OpenApiContractTest` porównuje ręcznie pisane
modele (`shared/model`) z kontraktem OpenAPI backendu, którego kopię trzymamy w
`shared/contract/openapi.json`. Reguła: każde pole modelu musi istnieć w schemacie
backendu (modelom wolno pomijać pola nieużywane w UI, ale nie czytać pól, których
backend już nie ma). Gdy backend zmieni DTO, **odśwież snapshot** i uruchom testy —
wskażą, które modele wymagają dostosowania:

```
cp ../Songly-backend/docs/api/openapi.json shared/contract/openapi.json
./gradlew :shared:testDebugUnitTest
```

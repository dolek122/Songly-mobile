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

## Kontrakt z backendem

Modele w `shared/.../model` odwzorowują DTO z backendu 1:1. Źródłem prawdy jest
kontrakt OpenAPI generowany w repo `Songly-backend` (`docs/api/openapi.json`) —
przy zmianie API po stronie serwera zaktualizuj modele tutaj. Format błędów jest
spójny (`ApiError`: `code`, `message`, `timestamp`, `path`), więc obsługa błędów
opiera się na jednym parserze.

## Uruchomienie

**Wymagania:** JDK 17+, Android Studio (z wtyczką KMP), Xcode (dla iOS, tylko na macOS).

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

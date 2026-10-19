plugins {
	alias(libs.plugins.androidApplication)
	alias(libs.plugins.kotlinAndroid)
	alias(libs.plugins.composeCompiler)
}

// Push (backend ADR 0040): plugin google-services tylko, gdy jest konfiguracja
// projektu Firebase. Plik nie trafia do repo, więc CI i każdy bez niego budują
// aplikację bez pushy — w kodzie pilnuje tego PushRegistration (brak FirebaseApp
// = brak rejestracji), zamiast wywalać build na brakującym pliku.
if (file("google-services.json").exists()) {
	apply(plugin = libs.plugins.googleServices.get().pluginId)
}

// Klucz wydania nigdy nie trafia do repo: ścieżka i hasła przychodzą ze zmiennych
// środowiskowych (CI) albo z ~/.gradle/gradle.properties (maszyna wydającego).
// Bez nich release buduje się niepodpisany — CI dalej sprawdza, że R8 przechodzi,
// tylko nie powstaje paczka, którą da się wgrać do sklepu.
fun releaseSecret(name: String): String? =
	providers.environmentVariable(name).orElse(providers.gradleProperty(name)).orNull

val releaseKeystore = releaseSecret("SONGLY_KEYSTORE_FILE")

android {
	namespace = "com.songly.android"
	compileSdk = libs.versions.compileSdk.get().toInt()

	defaultConfig {
		applicationId = "com.songly.android"
		minSdk = libs.versions.minSdk.get().toInt()
		targetSdk = libs.versions.targetSdk.get().toInt()
		versionCode = 1
		versionName = "0.1.0"
	}

	testOptions {
		unitTests {
			// Robolectric potrzebuje zasobów aplikacji (motyw, stringi) — bez tego
			// testy UI wywalają się na inflacji themu.
			isIncludeAndroidResources = true
			// Każda klasa testowa w świeżej JVM. Compose trzyma globalny
			// GlobalSnapshotManager uruchamiany raz na JVM i przywiązany do pętli
			// Robolectrica pierwszego testu z Compose. Gdy test bez Compose (np.
			// ViewModel) zapisze potem stan, powiadomienie trafia do pętli, którą
			// Robolectric czyści między testami — manager staje na zawsze, a każdy
			// kolejny test z polem tekstowym wisi 60 s na „Compose did not get idle".
			// Wynik zależał więc od kolejności klas, a nie od kodu.
			all { it.forkEvery = 1 }
		}
	}

	buildFeatures {
		compose = true
		// BuildConfig.DEBUG wybiera backend (SonglyApplication.apiConfigFor).
		buildConfig = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
	}

	signingConfigs {
		if (releaseKeystore != null) {
			create("release") {
				storeFile = file(releaseKeystore)
				storePassword = releaseSecret("SONGLY_KEYSTORE_PASSWORD")
				keyAlias = releaseSecret("SONGLY_KEY_ALIAS")
				keyPassword = releaseSecret("SONGLY_KEY_PASSWORD")
			}
		}
	}

	buildTypes {
		release {
			// R8: usuwa nieużywany kod i zasoby. Reguły serializacji, Ktora i OkHttp
			// przychodzą z samych bibliotek; nasze są w proguard-rules.pro.
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
			signingConfig = signingConfigs.findByName("release")
		}
	}
}

dependencies {
	implementation(project(":shared"))

	implementation(libs.androidx.core.ktx)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.lifecycle.viewmodel.compose)
	implementation(platform(libs.androidx.compose.bom))
	implementation(libs.androidx.compose.ui)
	implementation(libs.androidx.compose.material3)
	implementation(libs.androidx.compose.material.icons.extended)
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.coil.compose)
	implementation(platform(libs.firebase.bom))
	implementation(libs.firebase.messaging)
	debugImplementation(libs.androidx.compose.ui.tooling)

	// Testy bez urządzenia: kontrast palety (czysta matematyka) oraz testy UI na
	// Robolectriku — ten sam kod ekranów, tylko środowisko Androida symulowane na JVM.
	testImplementation(libs.kotlin.test)
	testImplementation(libs.junit)
	testImplementation(libs.robolectric)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.ktor.client.mock)
	testImplementation(platform(libs.androidx.compose.bom))
	testImplementation(libs.androidx.compose.ui.test.junit4)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
}

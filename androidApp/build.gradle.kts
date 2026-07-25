plugins {
	alias(libs.plugins.androidApplication)
	alias(libs.plugins.kotlinAndroid)
	alias(libs.plugins.composeCompiler)
}

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
		}
	}

	buildFeatures {
		compose = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}

	kotlinOptions {
		jvmTarget = "17"
	}

	buildTypes {
		release {
			isMinifyEnabled = false
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
	implementation(libs.androidx.compose.ui.tooling.preview)
	implementation(libs.coil.compose)
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

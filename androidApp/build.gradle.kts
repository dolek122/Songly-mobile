plugins {
	alias(libs.plugins.androidApplication)
	alias(libs.plugins.kotlinAndroid)
	alias(libs.plugins.composeCompiler)
}

if (file("google-services.json").exists()) {
	apply(plugin = libs.plugins.googleServices.get().pluginId)
}

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
			isIncludeAndroidResources = true
			all { it.forkEvery = 1 }
		}
	}

	buildFeatures {
		compose = true
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

	testImplementation(libs.kotlin.test)
	testImplementation(libs.junit)
	testImplementation(libs.robolectric)
	testImplementation(libs.kotlinx.coroutines.test)
	testImplementation(libs.ktor.client.mock)
	testImplementation(platform(libs.androidx.compose.bom))
	testImplementation(libs.androidx.compose.ui.test.junit4)
	debugImplementation(libs.androidx.compose.ui.test.manifest)
}

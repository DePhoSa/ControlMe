plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

fun prop(name: String): String =
    (System.getenv(name) ?: project.findProperty(name)?.toString() ?: "")

android {
    namespace = "de.controlme.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.controlme.app"
        minSdk = 34          // Android 14+ (Pixel 8 wird mit Android 14 ausgeliefert)
        targetSdk = 36       // Android 16
        versionCode = (prop("VERSION_CODE").ifBlank { "1" }).toInt()
        versionName = prop("VERSION_NAME").ifBlank { "1.0" }

        // Pixel 8 und neuer sind reine 64-Bit-ARM-Geräte – spart ca. 75 % APK-Größe
        ndk { abiFilters += "arm64-v8a" }

        // Optionaler eigener TURN-Server (nur nötig, wenn Verbindungen über Mobilfunk scheitern)
        buildConfigField("String", "TURN_URL", "\"${prop("TURN_URL")}\"")
        buildConfigField("String", "TURN_USER", "\"${prop("TURN_USER")}\"")
        buildConfigField("String", "TURN_PASS", "\"${prop("TURN_PASS")}\"")
    }

    signingConfigs {
        create("release") {
            val ks = prop("KEYSTORE_FILE")
            if (ks.isNotBlank() && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = prop("KEYSTORE_PASSWORD")
                keyAlias = prop("KEY_ALIAS")
                keyPassword = prop("KEY_PASSWORD")
            } else {
                // Lokal ohne eigenen Schlüssel: Debug-Schlüssel von Android Studio verwenden
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.fragment) // hebt eine alte transitive Version an

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.tooling.preview)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.coroutines.play.services)

    implementation(libs.webrtc)
}

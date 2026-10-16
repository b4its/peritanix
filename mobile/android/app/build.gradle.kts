// =============================================================================
// DAPE Android app — Gradle project (separate from the pure-JVM core).
// -----------------------------------------------------------------------------
// The Kotlin core at ../src is consumed as a source dependency so the physics
// and crypto stay in ONE place (no duplication). The Android layer adds
// Jetpack Compose UI, Room, WorkManager and Camera2.
//
// Build:  cd mobile/android && ./gradlew assembleDebug   (needs Android SDK)
// Or via make: make mobile-build   (uses the `android` Docker target)
// =============================================================================
plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

android {
    namespace = "id.peritanix.dape.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.peritanix.dape"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // NDK for the OpenCV C++ (burst aligner / sediment edge detector).
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildFeatures { compose = true }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    // Consume the shared JVM core sources directly.
    sourceSets["main"].java.srcDirs("src/main/java", "../src/main/kotlin")

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // R8 obfuscation + resource shrinking + native stripping for small APKs.
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk { debugSymbolLevel = "none" }
        }
    }
}

dependencies {
    // Compose (BOM keeps versions aligned)
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Room (offline SQLite)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // WorkManager (offline sync queue)
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Camera2 is in the platform; CameraX helpers optional.

    // BouncyCastle (Ed25519), already used by the core.
    implementation("org.bouncycastle:bcprov-jdk18on:1.79")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

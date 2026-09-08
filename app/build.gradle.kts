import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.whatschat.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.whatschat.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Real phones are arm64-v8a (or armeabi-v7a on older/budget devices) — x86/x86_64
        // only matter for some emulators, and WebRTC's native libraries for all four
        // architectures are a big chunk of the installed app's size.
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// The old `android { kotlinOptions { jvmTarget = "17" } }` shorthand became a hard
// error in this Kotlin version — this compilerOptions DSL is its replacement.
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Core / lifecycle / activity
    implementation("androidx.core:core-ktx:1.13.1")
    // Only used for its per-app language API (AppCompatDelegate.setApplicationLocales) —
    // the app itself is plain Compose/ComponentActivity, not AppCompatActivity.
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    // Firebase stopped releasing separate "-ktx" modules in mid-2025 and dropped them
    // from the BoM entirely — the Kotlin extension functions now live in these same,
    // un-suffixed artifacts, so nothing about how the code calls them changes.
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    // Gemini access for photo-menu translation, called straight from the app (no separate
    // backend needed) — kept safe from key-extraction because there's no API key embedded
    // at all; access is gated by App Check (below) instead.
    implementation("com.google.firebase:firebase-ai")
    // Proves to Google's servers that Firebase AI Logic calls are coming from a genuine,
    // unmodified install of this app (via Play Integrity), not a scraped/embedded key
    // being called from somewhere else — required for Firebase AI Logic since mid-2026.
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Voice calls (WebRTC signaled over Firestore)
    implementation("io.getstream:stream-webrtc-android:1.1.1")

    // Face-tracked selfie filters
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // CameraX's ProcessCameraProvider.getInstance() returns a Guava ListenableFuture;
    // not pulled in transitively, so it must be declared explicitly.
    implementation("com.google.guava:guava:31.0.1-android")

    // Voice translation (translate a typed message, speak it, send as a voice note)
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")

    // QR code quick-connect: scan a contact's code to jump straight into a chat with them
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.zxing:core:3.5.3")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

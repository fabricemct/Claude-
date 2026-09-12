plugins {
    id("com.android.application") version "8.5.2" apply false
    // Bumped from 2.0.20: the Firebase libraries pulled in by the newer BoM (34.18.0,
    // needed for Firebase AI Logic) were compiled with Kotlin 2.3.0 metadata, which
    // 2.0.20's compiler can't read ("incompatible version of Kotlin"). AGP 8.5.2 and
    // Gradle 8.7 (this project's versions) both stay within Kotlin 2.3.x's supported range.
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

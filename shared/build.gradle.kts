// KMP module: platform-agnostic logic, shared across targets.
//
// Phase 1 scope - deliberately minimal. This module exists to prove the Kotlin
// Multiplatform toolchain works on this repo at all (Kotlin 2.4.10 + AGP
// 9.1.1), with the Android app building and its test suite unchanged. Exactly
// one dependency-free file lives here so far; nothing Android-only has moved.
//
// Since AGP 9.0 the plain `com.android.library` plugin is NOT compatible with
// the Kotlin Multiplatform plugin. AGP's supported path is the KMP-native
// `com.android.kotlin.multiplatform.library`, configured inside `kotlin { }`.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    jvmToolchain(21)

    // Console (JVM) target - the Linux app target later on.
    jvm()

    // Android target, consumed by :app.
    android {
        namespace = "com.m57.hermescontrol.shared"
        compileSdk = 37
        minSdk = 26
    }
}

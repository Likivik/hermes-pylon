// KMP module: platform-agnostic logic, shared across targets.
//
// Phase 2: protocol/model core moved into commonMain. The :app module still
// owns Android-only collaborators (OkHttp, Room, the WS client, the theme),
// so data classes and pure-Kotlin collaborators live here.
//
// Since AGP 9.0 the plain `com.android.library` plugin is NOT compatible with
// the Kotlin Multiplatform plugin. AGP's supported path is the KMP-native
// `com.android.kotlin.multiplatform.library`, configured inside `kotlin { }`.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
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

    sourceSets {
        commonMain {
            dependencies {
                // Data classes use kotlinx.serialization @Serializable.
                implementation(libs.kotlinx.serialization.json)
                // Flow / StateFlow / MutableStateFlow — moved from :app.
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

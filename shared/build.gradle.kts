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
    // ktlint was only applied to :app, so every file moved into :shared silently
    // dropped out of the lint gate. Same engine pin as :app; CI runs the root
    // `ktlintCheck`, so this restores coverage for this module.
    alias(libs.plugins.ktlint)
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
                // io.ktor.http.Url: multiplatform URL parsing — replaces the
                // jvm-only okhttp3.HttpUrl so ServerEndpoint can move out of
                // :app and into :shared's common source set.
                implementation(libs.ktor.http)
                // The Ktor transport floor (HermesHttpClient). This is NEW code
                // alongside Retrofit, not a replacement for it: :app's
                // HermesApiService and its frozen tests stay exactly as
                // upstream wrote them.
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                // MockEngine: the multiplatform stand-in for MockWebServer.
                implementation(libs.ktor.client.mock)
            }
        }

        androidMain {
            dependencies {
                // Engine for the Android target. OkHttp underneath, so an
                // Android HttpClient can later be handed OkHttpProvider's
                // OkHttpClient via `engine { preconfigured = ... }`.
                implementation(libs.ktor.client.okhttp)
            }
        }

        jvmMain {
            dependencies {
                // Console target: no OkHttp dependency at all.
                implementation(libs.ktor.client.cio)
            }
        }
    }
}

ktlint {
    version.set(libs.versions.ktlintEngine.get())
}

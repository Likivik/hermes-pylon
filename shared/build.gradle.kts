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

// The wasm toolchain downloads Node.js (https://nodejs.org/dist) and Yarn
// (https://github.com/yarnpkg/yarn/releases/download) into the Gradle cache,
// which registers PROJECT-level ivy repositories. settings.gradle.kts sets
// FAIL_ON_PROJECT_REPOS, so that registration fails the build at task-graph
// time ("repository 'Distributions at ...' was added by unknown code").
// AbstractSetupTask only touches project.repositories when download == true, so
// turning the downloads off skips it entirely and the plugin falls back to
// `node`/`yarn` on PATH. Any host running the wasm tier therefore needs node
// and yarn installed — see scripts/local-tests.sh `wasm` and the unit-tests
// workflow.
// The node knob is the root extension's `download`, which every
// WasmNodeJsEnvSpec conventions from (it covers both `:kotlinWasmNodeJsSetup`
// and `:shared:kotlinWasmNodeJsSetup`); it is DeprecationLevel.ERROR in Kotlin
// 2.4, hence the @Suppress. Yarn uses the non-deprecated EnvSpec property.
@Suppress("DEPRECATION", "DEPRECATION_ERROR")
fun org.gradle.api.Project.enableSystemToolchainForWasm() {
    plugins.withType(org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin::class.java) {
        extensions.getByType(
            org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootExtension::class.java,
        ).download = false
    }
    plugins.withType(org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnPlugin::class.java) {
        extensions.getByType(
            org.jetbrains.kotlin.gradle.targets.wasm.yarn.WasmYarnRootEnvSpec::class.java,
        ).download.set(false)
    }
}
rootProject.enableSystemToolchainForWasm()

kotlin {
    jvmToolchain(21)

    // Web target — the litmus test that keeps `commonMain` honest: with every
    // target JVM-based, `compileCommonMainKotlinMetadata` is SKIPPED, so
    // commonMain is only ever compiled per target against the JVM stdlib. That
    // is how 38 JVM-only APIs sat here undetected — see docs/kmp-boundary.md.
    // The test run is wired up (scripts/local-tests.sh `wasm`, plus the
    // unit-tests workflow). It is the only target exercising commonMain on a
    // non-JVM runtime, and it has already caught one JVM-only assumption.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                useKarma {
                    // CI runners ship Chrome; the dev boxes here do not, so the
                    // browser is switchable: -PwasmTestBrowser=firefox.
                    if (providers.gradleProperty("wasmTestBrowser").orNull == "firefox") {
                        useFirefoxHeadless()
                    } else {
                        useChromeHeadless()
                    }
                }
            }
        }
    }

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
                // Multiplatform lock for HermesWsClient's critical sections.
                implementation(libs.kotlinx.atomicfu)
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
                // Curated engine per target (Android -> OkHttp, JVM -> CIO), so a
                // new target needs no engine declaration of its own. This is the
                // documented KMP recommendation over per-source-set engines.
                implementation(libs.ktor.client.engine.defaults)
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
                // Engine for the Android target comes from engine-defaults in
                // commonMain. OkHttp stays declared because
                // HermesHttpClientOkHttp.kt names the engine type explicitly to
                // hand it OkHttpProvider's OkHttpClient via `preconfigured`.
                implementation(libs.ktor.client.okhttp)
            }
        }

        jvmMain {
            dependencies {
                // No engine declared: ktor-client-engine-defaults resolves one
                // for this target. Nothing here depends on OkHttp.
            }
        }
    }
}

ktlint {
    version.set(libs.versions.ktlintEngine.get())
}

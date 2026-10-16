// `:ui` — Phase 1 of the Compose Multiplatform migration.
//
// Targets android + jvm. Only `androidMain` exists yet; `commonMain` is added
// incrementally as more code moves. See `.hermes/plans/2026-10-15_135602-cmp-migration.md`.
//
// This module owns the Compose UI tree, the Room database (schemas under
// `ui/schemas/`), the OkHttp/WS network collaborators, encryption helpers,
// theme/data/util packages, and navigation. `:app` shrinks to a thin Android
// entry point (HermesControlApp + MainActivity + notification/).
//
// Namespace is `com.m57.hermescontrol` so the ~70 existing
// `import com.m57.hermescontrol.R` statements keep resolving unchanged.
// `:app` is reassigned to `com.m57.hermescontrol.app` so the two modules'
// generated R classes don't collide.
//
// Note on BuildConfig: `com.android.kotlin.multiplatform.library` does NOT
// support the standard AGP `buildConfig` / `buildConfigField` DSL — the
// `android {}` extension is intentionally not exposed for KMP modules (see
// AGP 9 migration guide). Three moved files (data/remote/ServerEndpointExtras.kt,
// data/remote/ApiClient.kt, util/AndroidLogSink.kt) plus two UI files
// (ui/system/SystemViewModel.kt, ui/settings/components/AboutSection.kt) need
// `BuildConfig.DEBUG` / `ALLOW_CLEARTEXT` / `VERSION_NAME` / `VERSION_CODE` /
// `GIT_SHA`. We replicate those constants here via a generated Kotlin file
// (`com.m57.hermescontrol.BuildConfig`) so the moved source compiles
// unchanged. The generation task re-runs whenever versionName / versionCode /
// gitSha change, and matches `:app`'s `buildConfigField` declarations exactly.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.compiler)
    // Compose Multiplatform plugin (Phase 2). Pairs with compose-compiler above:
    // compose-compiler is the Kotlin-side compiler plugin, this one declares
    // the runtime artifact versions and configures Compose for every target.
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktlint)
}

// Build-time constants that need to flow into the generated BuildConfig.kt.
// Captured at configure time so the task input is stable for caching.
val versionNameVal: String =
    (project.findProperty("versionName") as? String) ?: "1.0-dev"
val versionCodeVal: Int =
    (project.findProperty("versionCode") as? String)?.toIntOrNull() ?: 1
val gitShaVal: String =
    providers.exec {
        commandLine("git", "rev-parse", "HEAD")
    }.standardOutput.asText.get().trim().take(7)

// `kspAndroid` is the per-target KSP configuration for the AGP-KMP library
// `android` target — `:app` (a single-platform Android module) uses `ksp(...)`
// in a flat `dependencies { }` block; here we must use `add(...)` because the
// KMP source-set layout doesn't expose a `ksp` configuration on the project.
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)

    // The Compose BOM is applied to the `androidMain` configuration so every
    // androidx.compose.* resolved below it picks up the version constraints.
    // KMP libraries don't expose build-type-specific configurations (they
    // ship a single variant); Compose tooling stays in `:app` only.
    add("androidMainImplementation", platform(libs.androidx.compose.bom))
}

// Generate BuildConfig.kt so the moved code's `com.m57.hermescontrol.BuildConfig`
// references resolve. The output directory is wired into androidMain below.
//
// The KMP library plugin generates no BuildConfig, so this stands in for it.
// BLOCKING ISSUE (release builds): `:ui` is single-variant, so it cannot know
// whether the consuming `:app` build is debug or release. These two flags are
// therefore pinned to the DEBUG values, which is what the unit tests and dev
// builds exercise - but it means a RELEASE build now gets
// ALLOW_CLEARTEXT = true, where `:app`'s old per-build BuildConfig denied
// cleartext. That is a real security regression on the release path and must be
// fixed before any release build, by injecting these flags at runtime from
// `:app` (or routing them through `:shared`'s existing LogSink seam) instead of
// reading a library-level constant. See the CMP plan, Phase 1.
val buildConfigOutDir = layout.buildDirectory.dir("generated/source/buildConfig/androidMain")
val generateBuildConfig =
    tasks.register("generateUiBuildConfig") {
        group = "build"
        description = "Generates com.m57.hermescontrol.BuildConfig for the :ui KMP module."
        inputs.property("versionName", versionNameVal)
        inputs.property("versionCode", versionCodeVal)
        inputs.property("gitSha", gitShaVal)
        outputs.dir(buildConfigOutDir)
        doLast {
            val pkgDir = buildConfigOutDir.get().asFile.resolve("com/m57/hermescontrol")
            pkgDir.mkdirs()
            val lines =
                listOf(
                    "// Generated by ui/build.gradle.kts - do not edit.",
                    "package com.m57.hermescontrol",
                    "",
                    "/** Stand-in for the AGP-generated BuildConfig (KMP libraries get none). */",
                    "object BuildConfig {",
                    "    const val VERSION_NAME: String = \"$versionNameVal\"",
                    "    const val VERSION_CODE: Int = $versionCodeVal",
                    "    const val GIT_SHA: String = \"$gitShaVal\"",
                    "    const val DEBUG: Boolean = true",
                    "    const val ALLOW_CLEARTEXT: Boolean = true",
                    "}",
                )
            pkgDir.resolve("BuildConfig.kt").writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    jvmToolchain(21)

    jvm()

    android {
        namespace = "com.m57.hermescontrol"
        compileSdk = 37
        minSdk = 26

        // Opt in to Android resource processing — disabled by default in the
        // KMP library plugin (see CMP-9547 / AGP 9 migration guide). Without
        // this the `res/` folder is ignored and `R.string.*` / `R.drawable.*`
        // references fail to resolve silently.
        androidResources {
            enable = true
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                // Compose Multiplatform runtime — Phase 2 of the CMP plan. The
                // minimal set that the proof composable needs: runtime + ui +
                // foundation(/-layout) + material3. The :ui androidMain source
                // set already has the AndroidX-backed compose UI on its
                // classpath; CMP 1.12.x republishes these as the SAME types,
                // so Android's `api(libs.androidx.compose.ui)` keeps compiling
                // unchanged — the compiler sees one merged type tree.
                implementation(libs.compose.runtime)
                implementation(libs.compose.ui)
                implementation(libs.compose.foundation)
                implementation(libs.compose.foundation.layout)
                implementation(libs.compose.material3)
                // Adaptive fork (Phase 2). All four artifacts + the
                // navigation-suite chrome so any commonMain composable can
                // build ListDetailPaneScaffold + NavigationSuiteScaffold
                // without reaching for AndroidX.
                implementation(libs.compose.adaptive)
                implementation(libs.compose.adaptive.layout)
                implementation(libs.compose.adaptive.navigation)
                implementation(libs.compose.adaptive.navigation3)
                implementation(libs.compose.material3.adaptive.navigation.suite)
                // Compose Resources: generates the typed `Res` accessor from
                // ui/src/commonMain/composeResources/. Configured by the
                // top-level `compose.resources { }` block at the bottom of
                // this file — without `publicResClass = true` the generated
                // class is `internal` and unreachable from other modules.
                api(compose.components.resources)
                // Phase 1: depends on the multiplatform data model + HTTP primitives
                // that already live in :shared.
                api(project(":shared"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                // runComposeUiTest for commonTest (multiplatform) — JVM runs
                // Skiko headless, Android needs the androidx driver; this
                // artifact is the common entry point.
                implementation(libs.compose.ui.test)
            }
        }
        // The native Skiko binary is platform-specific. linux-x64 is the
        // build host; a portable multi-host setup would select by
        // System.getProperty("os.name")/os.arch. The proof test only has to
        // run on this one machine.
        jvmTest {
            dependencies {
                implementation(libs.skiko.awt.runtime.linux.x64)
            }
        }
        androidMain {
            kotlin.srcDir(buildConfigOutDir)
            dependencies {
                // Core Android
                api(libs.androidx.core.ktx)
                api(libs.androidx.lifecycle.runtime.ktx)
                api(libs.androidx.activity.compose)
                api(libs.androidx.browser)

                // Arch Components
                api(libs.androidx.lifecycle.runtime.compose)
                api(libs.androidx.lifecycle.viewmodel.compose)

                // Compose
                api(libs.androidx.compose.ui)
                api(libs.androidx.compose.ui.tooling.preview)
                api(libs.androidx.compose.material3)
                api(libs.androidx.compose.material.icons.extended)

                // Networking
                api(libs.okhttp)
                api(libs.okhttp.logging)
                api(libs.ktor.client.core)
                api(libs.kotlinx.serialization.json)
                api(libs.emoji.kt)
                api(libs.emoji.compose)
                api(libs.reorderable)
                api(libs.crashwatcher)

                // Native Compose LaTeX rendering
                api(libs.latex.base)
                api(libs.latex.parser)
                api(libs.latex.renderer)

                // Image loading
                api(libs.coil.compose)
                api(libs.coil.gif)

                // Encrypted storage
                api(libs.androidx.security.crypto)
                api(libs.androidx.datastore)

                // Local database (Room)
                api(libs.androidx.room.runtime)
                api(libs.androidx.room.ktx)
                api(libs.sqlcipher)

                // Navigation
                api(libs.androidx.navigation3.ui)
                api(libs.androidx.navigation3.runtime)
                api(libs.androidx.lifecycle.viewmodel.navigation3)

                // Phase 1: depends on the multiplatform data model + HTTP primitives
                // that already live in :shared.
                api(project(":shared"))
            }
        }
    }
}

// Wire the generation task into the Android compilation pipeline so the
// generated file is on the classpath before Kotlin compiles.
tasks.matching {
    it.name == "compileAndroidMainKotlinMetadata" ||
        it.name == "kspAndroidMainKotlin" ||
        it.name == "kspAndroidMain"
}.configureEach {
    dependsOn(generateBuildConfig)
}

ktlint {
    version.set(libs.versions.ktlintEngine.get())
    // Compose Resources generates Kotlin under build/generated/ (Res.kt plus the
    // per-platform resource collectors) in a style ktlint rejects - object names,
    // indentation, import order. The files are machine-generated and not ours to
    // style. NOTE: this lambda form excludes the common generated class, but NOT
    // `androidMainResourceCollectors/`, because the Compose plugin registers that
    // directory as a source root after ktlint's filter is applied. The string
    // pattern form (`exclude("**/build/generated/**")`) excludes nothing at all and
    // makes this worse. Open issue: the collectors dir still needs a real exclusion.
    filter {
        exclude { it.file.path.contains("/build/generated/") }
    }
}

// Compose Resources registers its generated Kotlin under build/generated/ as an
// AGP/Android source root, which ktlint's `filter` block does not reach (it covers
// the KMP-level source sets only - which is why Res.kt was excluded but
// androidMainResourceCollectors/ was not). Exclude the generated tree on the lint
// tasks themselves; they are SourceTasks, so this actually applies. Without it the
// gate fails on machine-written files that we do not control the style of.
tasks.matching { it.name.startsWith("ktlint") }.configureEach {
    if (this is SourceTask) {
        exclude("**/build/generated/**")
    }
}

// Compose Resources — Phase 2. Generates the typed `Res` accessor from
// ui/src/commonMain/composeResources/.
compose.resources {
    // Required: the default is `internal`, which would leave `Res`
    // unreachable from :app and any other consumer module.
    publicResClass = true
    packageOfResClass = "com.m57.hermescontrol.generated.resources"
    // `auto` only generates when the module has a direct resources
    // dependency; `always` is explicit and survives refactors of how the
    // dependency arrives.
    generateResClass = always
}

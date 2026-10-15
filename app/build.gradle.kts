// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.
//
// After Phase 1 of the CMP migration (`.hermes/plans/2026-10-15_135602-cmp-migration.md`),
// this module is a thin Android entry point: Application + Activity + notification
// service. All Compose UI, data, network, theme, and util code lives in `:ui`.

import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.ktlint)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.screenshot)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.m57.hermescontrol.app"
    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.jvmArgs("-Xmx2g") }
        }
        managedDevices {
            localDevices {
                create("e2eApi34") {
                    device = "Pixel 5"
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }

    lint {
        disable += "MissingTranslation"
    }
    compileSdk = 37
    defaultConfig {
        applicationId = "sh.slb.hermesmobile"
        minSdk = 26
        targetSdk = 36
        versionCode =
            (project.findProperty("versionCode") as? String)?.toIntOrNull()
                ?: 1
        versionName =
            (project.findProperty("versionName") as? String) ?: "1.0-dev"
        manifestPlaceholders["usesCleartextTraffic"] = "false"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("hermes") {
            dimension = "distribution"
        }
        create("iris") {
            dimension = "distribution"
            applicationId = "sh.slb.irismobile"
        }
    }

    val releaseStorePath = System.getenv("KEYSTORE_PATH")
    val releaseStorePassword = System.getenv("KEYSTORE_PASSWORD")
    val releaseKeyAlias = System.getenv("KEY_ALIAS")
    val releaseKeyPassword = System.getenv("KEY_PASSWORD")
    val releaseSigningReady =
        listOf(
            releaseStorePath,
            releaseStorePassword,
            releaseKeyAlias,
            releaseKeyPassword,
        ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs["release"]
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    dependenciesInfo {
        includeInApk = false
    }

    packaging {
        resources {
            pickFirsts += "META-INF/AL2.0"
            pickFirsts += "META-INF/LGPL2.1"
            pickFirsts += "META-INF/LICENSE"
            pickFirsts += "META-INF/LICENSE.md"
            pickFirsts += "META-INF/LICENSE-notice.md"
            pickFirsts += "META-INF/NOTICE"
            pickFirsts += "META-INF/NOTICE.md"
        }
    }
}

tasks.matching { it.name.endsWith("VersionControlInfo") }.configureEach {
    doLast {
        outputs.files.filter { it.exists() }.forEach { it.writeText("") }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Phase 1 (KMP): platform-agnostic logic lives in `:ui`, which depends on
    // `:shared`. `:app` itself only needs the entry-point collaborators it
    // references directly.
    implementation(project(":ui"))
    implementation(project(":shared"))

    // @PreviewTest annotation + validation for the screenshotTest source set.
    "screenshotTestImplementation"(libs.screenshot.validation.api)

    // Roborazzi: JVM (Robolectric) screenshot capture/verify for Compose UI.
    "testImplementation"(libs.roborazzi.core)
    "testImplementation"(libs.roborazzi.rule)
    "testImplementation"(libs.robolectric)
    "testImplementation"(libs.androidx.compose.ui.test.junit4)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose only here because MainActivity is Compose. Everything else lives
    // in `:ui` and is reached transitively via project(":ui").
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // HermesControlApp owns the coil ImageLoader.
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // HermesControlApp wires the CrashWatcher into Android's crash handler.
    implementation(libs.crashwatcher)

    // HermesControlApp constructs the Ktor-backed HermesHttpClient and
    // NotificationEntryActivity uses NavKey for navigation; both come from
    // `:ui`'s transitive types but the runtime types themselves must be on
    // `:app`'s classpath.
    implementation(libs.ktor.client.core)
    implementation(libs.androidx.navigation3.runtime)

    // Tooling
    debugImplementation(libs.leakcanary.android)

    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Local tests: jUnit, coroutines, Android runner. Most assertions live in
    // code that has moved into `:ui`; only what `:app`'s test code itself uses
    // directly is declared here. `:ui` exposes its own test deps for tests
    // that move with the source code (none in Phase 1).
    testImplementation(libs.junit)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.mockk.android)
}

ktlint {
    version.set(libs.versions.ktlintEngine.get())
}

// ── Hardcoded Color Guard (issue #622) ────────────────────────────────────
//
// Fails the build if a hardcoded Color literal (Color(0x...) or named
// Color.White/Black/Red/Green/Gray/etc.) is introduced outside the theme
// module, *Preview.kt files, and _test sources. Pairing/Auth screens are
// exempt via the per-path overlay list below.
//
// Phase 1: theme/ has moved into `:ui`, so this guard now scans `:ui`'s
// source tree. `:app`'s own sources are limited to MainActivity, the
// notification service, HermesControlApp — none define colors.

val colorGuardSrcDir =
    layout.projectDirectory
        .dir("../ui/src/androidMain/kotlin/com/m57/hermescontrol")
val colorGuardExemptions =
    listOf(
        "/theme/",
        "PairingScreen.kt",
        "AuthLoginScreen.kt",
        "Preview.kt",
    )
val colorGuardHexPattern = Regex("""Color\(\s*0x[0-9A-Fa-f]{6,8}\s*\)""")
val colorGuardNamedPattern =
    Regex("""Color\.(White|Black|Red|Green|Gray|LightGray|DarkGray|Yellow|Blue|Cyan|Magenta)\b""")

tasks.register("checkColorLiterals") {
    group = "verification"
    description = "Fails if hardcoded Color(...) literals appear outside theme/, *Preview.kt, and _test."

    val srcDir = colorGuardSrcDir
    val exemptions = colorGuardExemptions
    val hexPattern = colorGuardHexPattern
    val namedPattern = colorGuardNamedPattern

    doLast {
        val offenders = mutableListOf<Pair<String, Int>>()
        srcDir.asFile.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            if (exemptions.any { file.absolutePath.contains(it) }) return@forEach
            file.useLines { lines ->
                lines.forEachIndexed { idx, raw ->
                    val line = raw.trim()
                    if (line.startsWith("import ")) return@forEachIndexed
                    if (line.startsWith("//") || line.startsWith("*")) return@forEachIndexed
                    if (hexPattern.containsMatchIn(line) || namedPattern.containsMatchIn(line)) {
                        offenders.add(file.absolutePath to (idx + 1))
                    }
                }
            }
        }

        if (offenders.isNotEmpty()) {
            val report =
                offenders.joinToString("\n") { (path, line) ->
                    "  - $path:$line"
                }
            throw GradleException(
                "Hardcoded Color literals found outside theme/ + *Preview.kt + _test:\n$report\n\n" +
                    "Replace with MaterialTheme.colorScheme.<token>, " +
                    "LocalHermesStatusColors.current.<semantic>, or " +
                    "Color.Transparent / Color.Unspecified (intentional).\n" +
                    "Update the theme token or the exemption list in app/build.gradle.kts.",
            )
        }
        logger.lifecycle("checkColorLiterals: no hardcoded Color literals found. ✅")
    }
}

tasks.named("check") {
    dependsOn("checkColorLiterals", "checkLocalizedResources")
}

tasks.register<Exec>("testLocalizedResourceChecker") {
    group = "verification"
    description = "Tests localized-resource placeholder validation."
    workingDir = rootProject.projectDir
    commandLine("python3", "-m", "unittest", "scripts/test_check_localized_resources.py")
}

tasks.register<Exec>("checkLocalizedResources") {
    group = "verification"
    description = "Checks zh, ja, and ko string key and format-placeholder parity."
    dependsOn("testLocalizedResourceChecker")
    workingDir = rootProject.projectDir
    commandLine("python3", "scripts/check-localized-resources.py")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

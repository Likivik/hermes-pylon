// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    // Compose Multiplatform: Phase 2 of the CMP plan. Declared at the root
    // with apply false; only :ui applies it, where commonMain + the adaptive
    // fork live. See hermes-pylon-kmp skill: every plugin version is owned by
    // the root plugin block, modules apply by alias.
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.atomicfu) apply false
    alias(libs.plugins.ktlint) apply false
}

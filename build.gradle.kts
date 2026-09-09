// Top-level build file where you can add configuration options common to all sub-projects/modules.

// AGP 9 compiles Kotlin itself ("built-in Kotlin"), which is why no org.jetbrains.kotlin.android
// plugin appears below — that plugin is incompatible with the new DSL and must not be added back.
// The flip side is that nothing in this project declares the Kotlin version: AGP pins KGP to its
// own runtime dependency (2.2.10 for AGP 9.x) unless a newer one is placed on the buildscript
// classpath. That classpath entry is the documented way to raise it:
// https://developer.android.com/r/tools/built-in-kotlin
//
// It is raised for KSP's sake. Only KSP 2.3.1+ registers its generated sources through
// android.sourceSets; everything older uses the kotlin.sourceSets DSL that AGP 9 rejects outright,
// which is what forced android.disallowKotlinSourceSets=false into gradle.properties. The KSP 2.3
// line serves the Kotlin 2.3 line, so KGP has to move with it.
//
// The version is a literal because the version catalog is not visible inside a buildscript block.
// It must stay equal to `kotlin` in gradle/libs.versions.toml, which pins the Compose compiler
// plugin — the two Kotlin-versioned plugins have to agree.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}
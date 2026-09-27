import org.gradle.api.JavaVersion.VERSION_11
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11

plugins {
    alias(libs.plugins.android.application)
}

val versionMajor = 1
val versionMinor = 6
val versionPatch = 3
val versionBuild = 0

/**
 * `M…Mmmppbb`: every component gets its own two digits (four for major), so codes never overlap and always increase.
 * e.g. `1.6.3` → `1060300` (the previous `major*10000 + minor*1000 + patch*100` scheme produced `16300`, still lower).
 * Google Play caps versionCode at 2100000000.
 */
fun versionCodeOf(major: Int, minor: Int, patch: Int, build: Int): Int {
    require(major in 0..2099) { "versionMajor must be in 0..2099, was $major" }
    require(minor in 0..99) { "versionMinor must be in 0..99, was $minor" }
    require(patch in 0..99) { "versionPatch must be in 0..99, was $patch" }
    require(build in 0..99) { "versionBuild must be in 0..99, was $build" }
    return major * 1_000_000 + minor * 10_000 + patch * 100 + build
}

android {
    compileSdk = 37
    defaultConfig {
        applicationId = "fr.smarquis.sleeptimer"
        namespace = "fr.smarquis.sleeptimer"
        minSdk = 26
        targetSdk = 37
        versionCode = versionCodeOf(versionMajor, versionMinor, versionPatch, versionBuild)
        versionName = "$versionMajor.$versionMinor.$versionPatch"
    }
    signingConfigs {
        getByName("debug") {
            keyAlias = "sleeptimer"
            keyPassword = "sleeptimer"
            storePassword = "sleeptimer"
            storeFile = file("debug.keystore")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFile(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
        }
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    packaging {
        resources.excludes.add("kotlin-tooling-metadata.json")
        resources.excludes.add("**/*.kotlin_builtins")
    }
    compileOptions {
        sourceCompatibility = VERSION_11
        targetCompatibility = VERSION_11
    }
}
kotlin {
    compilerOptions {
        jvmTarget = JVM_11
    }
}

dependencies {
    testImplementation(libs.junit)
}

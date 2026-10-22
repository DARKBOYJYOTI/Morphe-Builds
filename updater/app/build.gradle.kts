import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionCodeFromCi = System.getenv("UPDATER_VERSION_CODE")?.toIntOrNull() ?: 1
val versionNameFromCi = System.getenv("UPDATER_VERSION_NAME") ?: "1.0.0"

val keystorePath = System.getenv("UPDATER_KEYSTORE_PATH").orEmpty()
val hasReleaseKeystore = keystorePath.isNotBlank() && File(keystorePath).isFile

android {
    namespace = "io.github.darkboyjyoti.morpheupdater"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.darkboyjyoti.morpheupdater"
        minSdk = 21
        targetSdk = 36
        versionCode = versionCodeFromCi
        versionName = versionNameFromCi
        resourceConfigurations += listOf("en")
        vectorDrawables.useSupportLibrary = true
    }

    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = File(keystorePath)
                storePassword = System.getenv("UPDATER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPDATER_KEY_ALIAS")
                keyPassword = System.getenv("UPDATER_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
            "META-INF/*.kotlin_module",
        )
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

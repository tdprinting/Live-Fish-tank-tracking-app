import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(libs.plugins.android.application.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
    id(libs.plugins.kotlin.compose.get().pluginId)
    id(libs.plugins.kotlin.serialization.get().pluginId)
    id(libs.plugins.ksp.get().pluginId)
}

// CI passes -PversionCode=<run number + 100> and -PversionName=0.1.<date>.<run> so every build installs as an
// upgrade over the previous one. Local builds default to 1 / 0.1.0.
val reefVersionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val reefVersionName = (findProperty("versionName") as String?)?.takeIf { it.isNotBlank() } ?: "0.1.0"

android {
    namespace = "com.reeflive.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reeflive"
        minSdk = 26
        targetSdk = 35
        // Same applicationId and signing key for debug and release: never change them, or updates stop
        // installing over the old app (Android would require an uninstall, which deletes the data).
        versionCode = reefVersionCode
        versionName = reefVersionName
        vectorDrawables.useSupportLibrary = true
    }

    // Hobby sideload key, committed on purpose (see README "Signing"). Debug and release
    // share it so every CI build installs over the previous one without uninstalling.
    signingConfigs {
        create("reeflive") {
            storeFile = file("keystore/reeflive-debug.jks")
            storePassword = "reeflive"
            keyAlias = "reeflive"
            keyPassword = "reeflive"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("reeflive")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("reeflive")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Exported Room schemas (app/schemas) are the reference for migration tests; never use destructive fallback.
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    androidResources {
        noCompress += listOf("glb", "bin", "ktx")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.animation)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
}

// Root build. Plugin artifacts go on ONE shared buildscript classpath so the Kotlin
// Gradle plugin is loaded once and can see AGP (applying KGP from separate subproject
// classloaders is unsupported). Android/Google-Maven artifacts are added only when the
// build is not core-only, so `./gradlew -PcoreOnly :core:test` never touches Google Maven.
buildscript {
    val coreOnly = gradle.startParameter.projectProperties.containsKey("coreOnly")
    repositories {
        if (!coreOnly) google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath(libs.gradleplugin.kotlin)
        classpath(libs.gradleplugin.kotlin.serialization)
        if (!coreOnly) {
            classpath(libs.gradleplugin.android)
            classpath(libs.gradleplugin.kotlin.compose)
            classpath(libs.gradleplugin.ksp)
        }
    }
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(libs.plugins.kotlin.jvm.get().pluginId)
    id(libs.plugins.kotlin.serialization.get().pluginId)
}

// No jvmToolchain (no foojay resolver): compile for Java 17 from whatever JDK runs Gradle
// (JDK 21 locally, JDK 17 in CI).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    testLogging { events("passed", "skipped", "failed") }
    // the long whole-tank runs are independent: one JVM per core (CI's single fork sat at ~48 of its 60 minutes)
    maxParallelForks = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
    maxHeapSize = "1g"
}

// Track C: headless simulation runner for tuning (prints a 24 h scenario or a long health run).
tasks.register<JavaExec>("simRunner") {
    group = "verification"
    description = "Runs the headless ReefLive simulation scenario (Track C tuning tool)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.reeflive.core.sim.SimRunnerMainKt")
}

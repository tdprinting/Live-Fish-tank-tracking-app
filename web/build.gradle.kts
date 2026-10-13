plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id(libs.plugins.kotlin.serialization.get().pluginId)
}

kotlin {
    js(IR) {
        compilerOptions { freeCompilerArgs.addAll("-Xallow-kotlin-package", "-Xsuppress-version-warnings") }
        // No JS tests: `browser()` would otherwise download Node/Yarn/Karma for `check`.
        browser { testTask { enabled = false } }
        useEsModules()
        binaries.executable()
    }
    sourceSets {
        jsMain {
            // Compile :core's sources unchanged. Tracker helpers that need java.time stay JVM-only.
            kotlin.srcDir("../core/src/main/kotlin")
            // The Android 3D renderer, unchanged: android.opengl.GLES30 & co. are mapped onto WebGL2 by shims/.
            kotlin.srcDir("../app/src/main/java")
            kotlin.include("com/reeflive/core/**", "com/reeflive/app/render/**", "com/reeflive/web/**", "shims/**", "java/**", "android/**", "javax/**")
            kotlin.exclude(
                "com/reeflive/core/care/**", "com/reeflive/core/extras/**", "com/reeflive/core/io/**",
                "com/reeflive/app/render/TankGlView.kt", "com/reeflive/app/render/TankView.kt",
                "com/reeflive/app/render/DebugHud.kt",
            )
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

// ---- static site (GitHub Pages): web/site + compiled Kotlin + the app's shared assets and :core resources ----

val appAssets = rootProject.layout.projectDirectory.dir("app/src/main/assets")
val coreResources = rootProject.layout.projectDirectory.dir("core/src/main/resources")
val assetIndex = layout.buildDirectory.file("generated/assets-index/index.json")

/** assets/index.json: folder → file names, for AssetManager.list() in the browser. */
val assetIndexTask = tasks.register("assetIndex") {
    val src = appAssets
    val out = assetIndex
    inputs.dir(src)
    outputs.file(out)
    doLast {
        val root = src.asFile
        val dirs = root.walkTopDown().filter { it.isDirectory && it != root }.sortedBy { it.path }
        val body = dirs.joinToString(",\n") { d ->
            val names = d.listFiles().orEmpty().filter { it.isFile }.map { it.name }.sorted()
            "  \"${d.relativeTo(root).invariantSeparatorsPath}\": [${names.joinToString(", ") { "\"$it\"" }}]"
        }
        out.get().asFile.apply { parentFile.mkdirs(); writeText("{\n$body\n}\n") }
    }
}

val buildStamp = providers.gradleProperty("webBuild").orElse(providers.environmentVariable("GITHUB_SHA")).orElse("dev")

tasks.register<Sync>("site") {
    group = "build"
    description = "Assembles the installable web app (PWA) in build/site."
    val compile = tasks.named("compileProductionExecutableKotlinJs")
    dependsOn(compile, assetIndexTask)
    into(layout.buildDirectory.dir("site"))
    from(layout.projectDirectory.dir("site")) {
        exclude("sw.js")
    }
    from(layout.projectDirectory.file("site/sw.js")) {
        val stamp = buildStamp
        filter { line -> line.replace("__BUILD__", stamp.get().take(12)) }
    }
    from(layout.buildDirectory.dir("compileSync/js/main/productionExecutable/kotlin")) {
        into("kotlin")
        exclude("*.map")
    }
    from(appAssets) { into("assets") }
    from(assetIndex) { into("assets") }
    from(coreResources) { into("res") }
}

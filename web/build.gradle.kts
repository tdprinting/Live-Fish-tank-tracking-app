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
            kotlin.include("com/reeflive/core/**", "com/reeflive/app/render/**", "com/reeflive/web/**", "shims/**",
                // Plain-Kotlin pieces of the Android live screen, shared so both apps offer the same sandbox.
                "com/reeflive/app/ui/live/SandboxParams.kt", "com/reeflive/app/ui/live/LiveTips.kt",
                "com/reeflive/app/ui/live/LiveUiState.kt", "com/reeflive/app/ui/live/ThoughtLines.kt", "com/reeflive/app/audio/TankSoundMix.kt", "java/**", "android/**", "javax/**")
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

/**
 * whats-new.json for the "What's new" popup: one line per update merged to main, newest first, read from main's
 * merge history so every thread's feature shows up without anyone remembering to list it. docs/whats-new.json can
 * reword a line in plain language ("87": "...") or hide a behind-the-scenes one ("69": "").
 */
val whatsNew = layout.buildDirectory.file("generated/whats-new/whats-new.json")
val whatsNewTask = tasks.register("whatsNew") {
    val repo = rootProject.layout.projectDirectory
    val overridesFile = repo.file("docs/whats-new.json")
    val out = whatsNew
    inputs.file(overridesFile)
    outputs.file(out)
    outputs.upToDateWhen { false }
    doLast {
        val log = runCatching {
            val p = ProcessBuilder("git", "log", "--first-parent", "-n", "150", "--format=%cs%x09%s", "HEAD")
                .directory(repo.asFile).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            if (p.waitFor() == 0) text else ""
        }.getOrDefault("")
        @Suppress("UNCHECKED_CAST")
        val overrides = groovy.json.JsonSlurper().parse(overridesFile.asFile) as Map<String, Any?>
        val pr = Regex("""\s*\(#(\d+)\)\s*$""")
        val items = log.lines().mapNotNull { line ->
            val (date, subject) = line.split('\t', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            val m = pr.find(subject) ?: return@mapNotNull null
            val n = m.groupValues[1]
            val text = (overrides[n] as? String) ?: subject.removeRange(m.range)
            if (text.isBlank()) null else mapOf("n" to n.toInt(), "date" to date, "text" to text)
        }
        out.get().asFile.apply { parentFile.mkdirs(); writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(items)) + "\n") }
    }
}

/**
 * Safari can't decode every Ogg file, so the tank sounds also ship as AAC (.m4a) when ffmpeg is available (it is on
 * GitHub's Ubuntu runners). The page tries .m4a first and falls back to the .ogg.
 */
val soundsM4a = layout.buildDirectory.dir("generated/sounds")
val soundsTask = tasks.register("webSounds") {
    val src = rootProject.layout.projectDirectory.dir("app/src/main/res/raw")
    val out = soundsM4a
    inputs.dir(src)
    outputs.dir(out)
    doLast {
        val dir = out.get().asFile.apply { mkdirs() }
        val ffmpeg = listOf("/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg", "/opt/homebrew/bin/ffmpeg").map(::File).firstOrNull { it.canExecute() }
        if (ffmpeg == null) { logger.warn("ffmpeg not found: web sounds ship as .ogg only"); return@doLast }
        src.asFile.listFiles().orEmpty().filter { it.extension == "ogg" }.forEach { f ->
            val dst = File(dir, f.nameWithoutExtension + ".m4a")
            val p = ProcessBuilder(ffmpeg.path, "-y", "-loglevel", "error", "-i", f.path, "-c:a", "aac", "-b:a", "96k", dst.path).inheritIO().start()
            if (p.waitFor() != 0) logger.warn("ffmpeg failed on ${f.name}")
        }
    }
}

tasks.register<Sync>("site") {
    group = "build"
    description = "Assembles the installable web app (PWA) in build/site."
    val compile = tasks.named("compileProductionExecutableKotlinJs")
    dependsOn(compile, assetIndexTask, soundsTask, whatsNewTask)
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
    from(whatsNew)
    from(coreResources) { into("res") }
    // the species pictures the Android app shows (UI_STYLE §5.2), for the Add life tiles
    from(rootProject.layout.projectDirectory.dir("app/src/main/res/drawable-nodpi")) {
        include("species_*.webp")
        rename("species_(.*)", "$1")
        into("species")
    }
    from(rootProject.layout.projectDirectory.dir("app/src/main/res/raw")) { into("sounds") }
    from(soundsM4a) { into("sounds") }
}

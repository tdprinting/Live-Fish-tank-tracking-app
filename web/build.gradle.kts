import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

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
                "com/reeflive/app/ui/live/LiveUiState.kt", "com/reeflive/app/ui/live/ThoughtLines.kt", "com/reeflive/app/audio/TankSoundMix.kt",
                "java/**", "android/**", "javax/**")
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
 * GitHub's Ubuntu runners). The page tries .m4a first and falls back to the .ogg. The radio songs are already
 * band-limited like a small speaker, so they take a low bitrate.
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
            val p = ProcessBuilder(ffmpeg.path, "-y", "-loglevel", "error", "-i", f.path, "-c:a", "aac", "-b:a", if (f.name.startsWith("radio_")) "40k" else "96k", dst.path).inheritIO().start()
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
    // the radio's record sleeves (tools/graphics/vinyl_art.py), for the record shelf
    from(rootProject.layout.projectDirectory.dir("app/src/main/res/drawable-nodpi")) {
        include("vinyl_*.webp")
        into("vinyl")
    }
    // the fish stores' storefront pictures (tools/blender/stores.py), for My tank's store screens
    from(rootProject.layout.projectDirectory.dir("app/src/main/res/drawable-nodpi")) {
        include("store_*.webp")
        rename("store_(.*)", "$1")
        into("stores")
    }
    from(rootProject.layout.projectDirectory.dir("app/src/main/res/raw")) { into("sounds") }
    from(soundsM4a) { into("sounds") }
    // load time (docs/WEB.md "Loading fast"): pack the boot files, gzip the models, minify the code, and
    // give the service worker every file's content hash
    val siteDir = layout.buildDirectory.dir("site")
    val esbuild = providers.gradleProperty("esbuild").orElse("esbuild@0.24.2")
    doLast { SiteOptimizer.run(siteDir.get().asFile, esbuild.get(), logger) }
}

/**
 * Post-processes the assembled site for a fast first load and instant repeat visits:
 * - `boot.json`: the asset index, shaders, sim tables and species sheets Main.kt needs before the first frame, as
 *   one download instead of ~120 (Main.kt loadPack).
 * - `*.glb.gz` / `*.bin.gz`: GitHub Pages sends models uncompressed; gzipped they are about half the bytes. The page
 *   (index.html reefFetch) takes the .gz where the browser has DecompressionStream, else the plain file.
 * - `app.js`: the page modules and the compiled Kotlin bundled and minified by esbuild (via npx; skipped with a
 *   warning when Node is missing, leaving the plain modules, which still work).
 * - `sw.js`: every file's content hash, so the service worker serves repeat visits from its cache and fetches only
 *   files that changed.
 */
object SiteOptimizer { fun run(site: File, esbuild: String, log: org.gradle.api.logging.Logger) {
    fun rel(f: File) = f.relativeTo(site).invariantSeparatorsPath
    fun json(s: String) = groovy.json.JsonOutput.toJson(s)

    // boot pack: keys are kotlin.Resources keys ("assets/x" as is, "res/x" as the classpath resource "x")
    val boot = buildList {
        add(File(site, "assets/index.json"))
        File(site, "assets/shaders").listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach(::add)
        add(File(site, "assets/sim/anchors.json"))
        add(File(site, "res/sim/interactions.json"))
        File(site, "res/species").listFiles().orEmpty().filter { it.isFile && it.extension == "json" }.sortedBy { it.name }.forEach(::add)
    }.filter { it.isFile }
    File(site, "boot.json").writeText(boot.joinToString(",\n", "{\n", "\n}\n") { f ->
        val key = rel(f).let { if (it.startsWith("res/")) it.removePrefix("res/") else it }
        "${json(key)}: ${json(f.readText())}"
    })

    site.walkTopDown().filter { it.isFile && (it.extension == "glb" || it.extension == "bin") }.forEach { f ->
        // GZIPOutputStream has no level setting: the gzip header + raw deflate at the best level + CRC and size
        val bytes = f.readBytes()
        val crc = CRC32().apply { update(bytes) }.value
        val body = ByteArrayOutputStream()
        DeflaterOutputStream(body, Deflater(Deflater.BEST_COMPRESSION, true), 1 shl 16).use { it.write(bytes) }
        File(f.path + ".gz").outputStream().buffered().use { o ->
            o.write(byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 0, 0, 0, 0, 2, 0xff.toByte()))
            o.write(body.toByteArray())
            for (v in longArrayOf(crc, bytes.size.toLong())) for (s in 0 until 32 step 8) o.write((v shr s and 0xff).toInt())
        }
    }

    // one minified module for the page (app.js and everything it imports); the plain kotlin/*.mjs stay beside it
    val index = File(site, "index.html")
    val modules = listOf("app.js") + site.listFiles().orEmpty().filter { it.extension == "js" && it.name != "sw.js" && it.name != "app.js" }.map { it.name }.sorted() +
        File(site, "kotlin").listFiles().orEmpty().filter { it.extension == "mjs" }.map { "kotlin/${it.name}" }.sorted()
    val bundled = runCatching {
        val out = File(site, "app.bundle.js")
        val p = ProcessBuilder("npx", "--yes", esbuild, "app.js", "--bundle", "--format=esm", "--minify", "--log-level=warning", "--outfile=${out.name}")
            .directory(site).redirectErrorStream(true).start()
        val msg = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0 && out.isFile) { msg }
        File(site, "app.js").delete()
        out.renameTo(File(site, "app.js"))
    }.onFailure { log.warn("esbuild unavailable, the page loads its modules unminified: ${it.message?.take(300)}") }.isSuccess
    // the bundle imports nothing; plain modules are preloaded together instead of level by level
    val preload = if (bundled) "" else modules.joinToString("\n") { "<link rel=\"modulepreload\" href=\"$it\">" }
    index.writeText(index.readText().replace("<!--modulepreload-->", preload))

    // content hashes for the service worker (sw.js FILES)
    val md = MessageDigest.getInstance("SHA-256")
    val hashes = site.walkTopDown().filter { it.isFile && rel(it) != "sw.js" && !rel(it).startsWith("sounds/radio_") }.sortedBy { rel(it) }
        .joinToString(",\n", "{\n", "\n}") { f ->
            md.reset()
            f.inputStream().buffered().use { s -> val buf = ByteArray(1 shl 16); while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
            val hex = md.digest().take(8).joinToString("") { b -> (b.toInt() and 0xff).toString(16).padStart(2, '0') }
            json(rel(f)) + ":\"" + hex + "\""
        }
    val sw = File(site, "sw.js")
    sw.writeText(sw.readText().replace("__FILES__", hashes))
} }

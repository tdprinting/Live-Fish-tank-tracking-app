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

// Opt-in benchmarks (#207): wall-clock measurements, not checks. `-Pbench` adds them to the run
// (`./gradlew -PcoreOnly -Pbench :core:test --tests '*SimBenchmark'`, or the "Sim benchmark" workflow).
val benchClasses = listOf("com.reeflive.core.sim.SimBenchmark")
val runBench = providers.gradleProperty("bench").isPresent

tasks.withType<Test>().configureEach {
    testLogging { events("passed", "skipped", "failed") }
    if (!runBench) filter { benchClasses.forEach { excludeTestsMatching(it) } }
    // SimBenchmark knobs: -PbenchDetail=true (frame histograms), -PbenchTicks=N
    listOf("benchDetail", "benchTicks").forEach { k -> providers.gradleProperty(k).orNull?.let { systemProperty("reeflive.$k", it) } }
    // -PbenchJfr=<file.jfr>: record a Java Flight Recording of the test JVM (allocation / CPU profiles)
    providers.gradleProperty("benchJfr").orNull?.let { jvmArgs("-XX:StartFlightRecording=filename=$it,settings=profile") }
}

tasks.test {
    // the long whole-tank runs are independent: one JVM per core (CI's single fork sat at ~48 of its 60 minutes)
    maxParallelForks = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
}

// ---------------------------------------------------------------------------------------------------------------
// CI sharding: `-PtestShards=N -PtestShard=k :core:testShard` runs shard k of N (CI runs the N shards as parallel jobs).
// Gradle hands test classes to forks round-robin, so one fork drew the 20-minute RealismAcceptanceTest plus its share of
// everything else. Here the work is packed instead: test-weights.txt lists the measured seconds of the slow classes and
// of single methods of the slowest (one unit always runs in one JVM, so methods that share a cached fixture are listed
// together); every other test class found under src/test counts as 10 s. The units are packed
// longest-first into N x F bins (F = -PtestForks, default 4), each bin its own Test task with one JVM; the configuration
// cache lets a shard's F bins run side by side. `testUnlisted` (shard 0) runs whatever the scan and the list missed, so
// every test runs exactly once over the N shards. Without -PtestShards, `testShard` runs all bins (same tests as `test`).
val shardCount = providers.gradleProperty("testShards").orNull?.toInt() ?: 1
val shardIndex = providers.gradleProperty("testShard").orNull?.toInt()
val binsPerShard = providers.gradleProperty("testForks").orNull?.toInt() ?: 4

data class TestUnit(val seconds: Double, val patterns: List<String>)

val testSrc = file("src/test/kotlin")
// top-level test classes (simple name -> FQN): files with @Test, `class Name` at column 0
val testClasses: Map<String, String> = testSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
    val text = f.readText()
    if ("@Test" !in text) return@flatMap emptySequence<Pair<String, String>>()
    val pkg = Regex("^package\\s+([\\w.]+)", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)
    Regex("^(?:internal |open )?class\\s+(\\w+)", RegexOption.MULTILINE).findAll(text)
        .map { it.groupValues[1] to (if (pkg == null) it.groupValues[1] else "$pkg.${it.groupValues[1]}") }
}.toMap().filterValues { it !in benchClasses }

fun fqn(pattern: String): String {
    val fq = testClasses[pattern.substringBefore('.')] ?: return "*.$pattern"
    return if ('.' in pattern) "$fq.${pattern.substringAfter('.')}" else fq
}

// "<seconds> <Class>" or "<seconds> <Class.method> [+ <Class.method> ...]" (simple class names)
val listedSimple: List<Pair<Double, List<String>>> = file("test-weights.txt").readLines().map { it.substringBefore('#').trim() }
    .filter { it.isNotEmpty() }.map { line -> line.split(Regex("\\s+")).let { p -> p[0].toDouble() to p.drop(1).filter { it != "+" } } }
val listedUnits = listedSimple.map { (sec, pats) -> TestUnit(sec, pats.map(::fqn)) }
val listedPatterns = listedUnits.flatMap { it.patterns }.toSet()
// classes listed method by method: their unlisted methods fall to testUnlisted
val splitClasses = listedSimple.flatMap { it.second }.filter { '.' in it }.map { fqn(it.substringBefore('.')) }.toSet()
val defaultUnits = testClasses.values.filter { it !in listedPatterns && it !in splitClasses }.sorted().map { TestUnit(10.0, listOf(it)) }

val binCount = shardCount * binsPerShard
val binLoad = DoubleArray(binCount)
val bins: List<List<String>> = run {
    val load = binLoad
    val out = List(binCount) { ArrayList<String>() }
    for (u in (listedUnits + defaultUnits).sortedWith(compareByDescending<TestUnit> { it.seconds }.thenBy { it.patterns.first() })) {
        val b = load.indices.minBy { load[it] }
        load[b] += u.seconds; out[b] += u.patterns
    }
    out
}

val testSourceSet = sourceSets["test"]
fun Test.shardBase() {
    group = "verification"
    testClassesDirs = testSourceSet.output.classesDirs
    classpath = testSourceSet.runtimeClasspath
    maxParallelForks = 1
    filter.isFailOnNoMatchingTests = false
}
val binTasks = bins.mapIndexed { i, patterns ->
    tasks.register<Test>("testBin$i") {
        description = "Test bin $i of $binCount (shard ${i / binsPerShard}): ${patterns.size} units."
        shardBase()
        filter { patterns.forEach { includeTestsMatching(it) } }
    }
}
val unlisted = tasks.register<Test>("testUnlisted") {
    description = "Tests that no shard bin lists (new classes the source scan missed)."
    shardBase()
    filter { (listedPatterns + testClasses.values.filter { it !in splitClasses }).forEach { excludeTestsMatching(it) } }
}
tasks.register("testShard") {
    group = "verification"
    description = "Runs shard -PtestShard of -PtestShards (all bins when -PtestShard is not set)."
    val mine = if (shardIndex == null) binTasks.indices.toList() else (shardIndex * binsPerShard until (shardIndex + 1) * binsPerShard).toList()
    dependsOn(mine.map { binTasks[it] })
    if (shardIndex == null || shardIndex == 0) dependsOn(unlisted)
}

tasks.register("testPlan") {
    group = "verification"
    description = "Prints the shard bins and their estimated seconds."
    val plan = bins.mapIndexed { i, p -> "bin $i (shard ${i / binsPerShard}) ~${binLoad[i].toInt()} s: ${p.joinToString(" ") { it.substringAfterLast("core.") }}" }
    doLast { plan.forEach { println(it) } }
}

// Track C: headless simulation runner for tuning (prints a 24 h scenario or a long health run).
tasks.register<JavaExec>("simRunner") {
    group = "verification"
    description = "Runs the headless ReefLive simulation scenario (Track C tuning tool)."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.reeflive.core.sim.SimRunnerMainKt")
}

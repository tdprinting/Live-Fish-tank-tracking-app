# ReefLive — Architecture

Repo: `tdprinting/live-fish-tank-tracking-app`. Package root `com.reeflive`. Kotlin 2.1, JVM 17 target (JDK 21 toolchain OK), minSdk 26, targetSdk 35, compileSdk 35.

---

## 1. Module layout and the two-environment constraint

```
/settings.gradle.kts
/build.gradle.kts                  (empty of android plugins; only repositories/versions via catalog)
/gradle/libs.versions.toml
/gradle/wrapper/                   (gradle-wrapper.jar + properties, distribution 8.11.1)
/core/                             pure Kotlin/JVM: chemistry, status engine, calculators, species DB,
                                   simulation, fish AI, math, GLB-independent geometry (SDF, flow field)
/app/                              Android: Compose UI, Room, DataStore, WorkManager, GLES renderer
/tools/blender/*.py                bpy scripts that generate assets (run locally, outputs committed)
/tools/textures/*.py               caustic tile, noise textures (Pillow/numpy)
/tools/sdf/bake_sdf.py             bakes rock SDF + anchors from the rockscape .glb
/app/src/main/assets/models/*.glb  committed assets
/app/src/main/assets/sim/          rock_sdf.bin, anchors.json, flow_probes.json
/docs/
/.github/workflows/android.yml
```

Rule: **`:core` must configure and test with no Android SDK and no Google Maven.** `settings.gradle.kts` includes `:app` only when not running core-only:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories { gradlePluginPortal(); google(); mavenCentral() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ReefLive"
include(":core")
if (!providers.gradleProperty("coreOnly").isPresent) include(":app")
```

Local dev: `./gradlew -PcoreOnly :core:test`. CI: full build. The root `build.gradle.kts` declares **no** plugins with `apply false` for Android (declaring them forces resolution from Google Maven). As implemented in M0: the root `buildscript {}` puts the plugin artifacts (`libs.gradleplugin.*`) on one shared classpath, adding AGP, the Compose compiler plugin and KSP (and the `google()` repo) only when `-PcoreOnly` is absent. Modules apply plugins by id without a version (`id(libs.plugins.x.get().pluginId)`). This keeps the core-only build off Google Maven while loading the Kotlin Gradle plugin once, in a classloader that can see AGP (per-subproject `alias(...)` would load KGP twice, which Kotlin flags as unsupported).

---

## 2. Version catalog (`gradle/libs.versions.toml`)

Conservative, known-published versions.

```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.12.01"
navigation = "2.8.5"
room = "2.6.1"
datastore = "1.1.1"
work = "2.10.0"
serialization = "1.7.3"
coroutines = "1.9.0"
junit = "4.13.2"
kotest = "5.9.1"
coil = "2.7.0"
exifinterface = "1.3.7"
desugar = "2.1.3"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
compose-foundation = { module = "androidx.compose.foundation:foundation" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
coil-compose = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
androidx-exifinterface = { module = "androidx.exifinterface:exifinterface", version.ref = "exifinterface" }
junit = { module = "junit:junit", version.ref = "junit" }
desugar-jdk-libs = { module = "com.android.tools:desugar_jdk_libs", version.ref = "desugar" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`core/build.gradle.kts`:
```kotlin
plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization) }
kotlin { jvmToolchain(17) }   // if JDK 17 not installed locally, use: java { sourceCompatibility = JavaVersion.VERSION_17 } and kotlin { compilerOptions { jvmTarget = JVM_17 } }
dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```
Note: do not use `jvmToolchain` unless foojay resolver is configured; prefer explicit `jvmTarget = JVM_17` with `-Xjdk-release=17` so JDK 21 locally and CI's JDK 17 both work.

`app/build.gradle.kts` (essentials):
```kotlin
plugins {
    alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose); alias(libs.plugins.kotlin.serialization); alias(libs.plugins.ksp)
}
android {
    namespace = "com.reeflive.app"; compileSdk = 35
    defaultConfig { applicationId = "com.reeflive"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0"
        vectorDrawables.useSupportLibrary = true }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        signingConfig = signingConfigs.getByName("debug") } }   // debug-signed release APK for sideload
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    androidResources { noCompress += listOf("glb", "bin", "ktx") }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":core"))
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui); implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3); implementation(libs.compose.foundation); implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx); implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose); implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.room.runtime); implementation(libs.room.ktx); ksp(libs.room.compiler)
    implementation(libs.datastore.preferences); implementation(libs.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json); implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose); implementation(libs.androidx.exifinterface)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
}
```

`gradle.properties`: `org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8`, `android.useAndroidX=true`, `kotlin.code.style=official`, `org.gradle.caching=true`, `org.gradle.configuration-cache=true`.

Wrapper: `gradle/wrapper/gradle-wrapper.properties` → `distributionUrl=https\://services.gradle.org/distributions/gradle-8.11.1-bin.zip`. Generate the wrapper jar locally with the installed Gradle 8.14 (`gradle wrapper --gradle-version 8.11.1`) and commit `gradle-wrapper.jar`.

---

## 3. CI workflow (`.github/workflows/android.yml`)

```yaml
name: Android CI
on:
  push: { branches: [main] }
  pull_request:
  workflow_dispatch:
permissions: { contents: write }
concurrency: { group: android-${{ github.ref }}, cancel-in-progress: true }
jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: android-actions/setup-android@v3
        with: { packages: 'platform-tools platforms;android-35 build-tools;35.0.0' }
      - uses: gradle/actions/setup-gradle@v4
        with: { cache-read-only: ${{ github.ref != 'refs/heads/main' }} }
      - name: Core unit tests
        run: ./gradlew :core:test --stacktrace
      - name: App unit tests
        run: ./gradlew :app:testDebugUnitTest --stacktrace
      - name: Build APKs
        run: ./gradlew :app:assembleDebug :app:assembleRelease --stacktrace
      - name: Version
        id: ver
        run: echo "tag=v0.1.$(date -u +%Y%m%d).${GITHUB_RUN_NUMBER}" >> "$GITHUB_OUTPUT"
      - uses: actions/upload-artifact@v4
        with:
          name: reeflive-apks-${{ steps.ver.outputs.tag }}
          path: |
            app/build/outputs/apk/debug/app-debug.apk
            app/build/outputs/apk/release/app-release.apk
          if-no-files-found: error
      - name: Release (main only)
        if: github.ref == 'refs/heads/main' && github.event_name == 'push'
        uses: softprops/action-gh-release@v2
        with:
          tag_name: ${{ steps.ver.outputs.tag }}
          name: ReefLive ${{ steps.ver.outputs.tag }}
          generate_release_notes: true
          files: |
            app/build/outputs/apk/debug/app-debug.apk
            app/build/outputs/apk/release/app-release.apk
```

Notes: release APK is signed with the debug keystore generated on the runner (deterministic install name `com.reeflive`; reinstall-over works because the same debug key is regenerated? No — the runner's debug key differs each run). **Therefore commit a dedicated keystore** `app/keystore/reeflive-debug.jks` (password `reeflive`, alias `reeflive`, generated with `keytool`) and point both debug and release `signingConfigs` at it so updates install over each other. This key is not secret (sideload-only app); document that in README.

`.gitattributes`: `*.glb binary`, `*.bin binary`, `*.webp binary`, `*.jks binary`.

---

## 4. `:core` packages and key classes

```
com.reeflive.core
  units/        Unit.kt, UnitConverter.kt
  params/       ParameterDef.kt, ParameterTable.kt, Targets.kt
  status/       StatusEngine.kt, Trend.kt, Alert.kt, InsightEngine.kt
  chem/         Ammonia.kt (Emerson), Oxygen.kt, Dosing.kt, WaterChange.kt, SaltMix.kt, Evaporation.kt
  species/      Species.kt, SpeciesDb.kt (loads species/*.json), Tolerance.kt, InteractionMatrix.kt
  seed/         SeedData.kt (parses seed/tee_tank.json), SeedModels.kt
  sim/          see SIMULATION.md: Clock.kt, WaterSim.kt, Organism.kt, HealthModel.kt, CoralSim.kt, Simulation.kt
  ai/           Perception.kt, Steering.kt, Behaviors*.kt, UtilityBrain.kt, SpeciesBrains.kt
  geom/         Vec3.kt, Mat4.kt, Sdf.kt (SdfGrid), FlowField.kt, SpatialHash.kt, Spline.kt, Rng.kt
  model/        Pure data: Measurement, TestSession, Livestock, Equipment, Task, Events (used by both app and sim)
  io/           CsvCodec.kt, BackupCodec.kt (JSON)
```

### 4.1 Units
```kotlin
// Implemented as `ParamUnit` (so it never shadows kotlin.Unit in UI code).
enum class ParamUnit(val symbol: String, val family: Family) { F("°F", TEMP), C("°C", TEMP), SG("SG", SAL), PPT("ppt", SAL), MS_CM("mS/cm", SAL),
  DKH("dKH", ALK), MEQ_L("meq/L", ALK), PPM_CACO3("ppm CaCO3", ALK), PPM("ppm", CONC), PPB("ppb", CONC), PPM_NO3_N("ppm NO3-N", CONC_NO3),
  PPM_PO4_P("ppm PO4-P", CONC_PO4), PH("pH", PH), MV("mV", ORP), MG_L("mg/L", DO), PCT_SAT("% sat", DO), NONE("", NONE) }
object UnitConverter { fun convert(value: Double, from: ParamUnit, to: ParamUnit, tempC: Double = 25.0): Double }
```
Alkalinity hub is dKH: ×0.357 → meq/L, ×17.9 → ppm CaCO3 (so meq/L → ppm goes through dKH, ×50.14), which makes 8 dKH → 2.856 meq/L → 143.2 ppm consistent.
```kotlin
```
Factors exactly as 01_reef_care §1.2. SG↔ppt uses the lookup table (SG 1.020→26.7, 1.021→28.0, 1.022→29.3, 1.023→30.6, 1.024→31.9, 1.025→33.3, 1.026→34.6, 1.027→35.9, 1.028→37.2, 1.029→38.5, 1.030→39.8 ppt at 25 °C; linear interpolation). ppt↔mS/cm: ×1.5 near 35 ppt (53 mS/cm at 35 ppt). DO % sat uses Oxygen.saturation(tempC, ppt).

### 4.2 Parameter table (`ParameterTable.kt`) — canonical data

```kotlin
data class Range(val lo: Double, val hi: Double)
data class ParameterDef(
    val id: String, val name: String, val glyph: String, val baseUnit: Unit, val altUnits: List<Unit>,
    val precision: Int, val ideal: Range, val acceptable: Range, val dangerLow: Double?, val dangerHigh: Double?,
    val maxChangePerDay: Double?, val cadenceDays: Int?, val favorite: Boolean, val order: Int, val higherIsWorse: Boolean? )

object ParameterTable {
  val SOFT_CORAL: List<ParameterDef> = listOf(
    ParameterDef("temp","Temperature","thermometer",Unit.F,listOf(Unit.C),1,Range(76.0,78.0),Range(74.0,80.0),72.0,84.0,1.0,1,true,0,null),
    ParameterDef("sal","Salinity","droplet",Unit.SG,listOf(Unit.PPT,Unit.MS_CM),3,Range(1.024,1.026),Range(1.023,1.027),1.020,1.029,0.001,7,true,1,null),
    ParameterDef("alk","Alkalinity","layers",Unit.DKH,listOf(Unit.MEQ_L,Unit.PPM_CACO3),1,Range(8.0,9.0),Range(7.0,11.0),6.0,12.0,1.0,7,true,2,null),
    ParameterDef("ca","Calcium","ca",Unit.PPM,emptyList(),0,Range(400.0,440.0),Range(380.0,460.0),350.0,500.0,20.0,7,true,3,null),
    ParameterDef("mg","Magnesium","mg",Unit.PPM,emptyList(),0,Range(1250.0,1350.0),Range(1200.0,1400.0),1100.0,1500.0,50.0,30,true,4,null),
    ParameterDef("ph","pH","flask",Unit.PH,emptyList(),2,Range(8.1,8.4),Range(7.9,8.5),7.8,8.6,0.2,7,true,5,null),
    ParameterDef("no3","Nitrate","no3",Unit.PPM,listOf(Unit.PPM_NO3_N),1,Range(3.0,15.0),Range(2.0,25.0),1.0,25.0,5.0,7,true,6,true),
    ParameterDef("po4","Phosphate","po4",Unit.PPM,listOf(Unit.PPB,Unit.PPM_PO4_P),3,Range(0.02,0.06),Range(0.01,0.10),null,0.15,0.02,7,true,7,true),
    ParameterDef("nh3","Ammonia (total)","nh3",Unit.PPM,emptyList(),2,Range(0.0,0.0),Range(0.0,0.02),null,0.25,null,null,false,8,true),
    ParameterDef("no2","Nitrite","no2",Unit.PPM,emptyList(),2,Range(0.0,0.0),Range(0.0,0.05),null,0.25,null,null,false,9,true),
    ParameterDef("k","Potassium","k",Unit.PPM,emptyList(),0,Range(380.0,420.0),Range(360.0,440.0),340.0,460.0,10.0,30,false,10,null),
    ParameterDef("sr","Strontium","sr",Unit.PPM,emptyList(),1,Range(8.0,9.0),Range(7.0,11.0),6.0,null,0.5,90,false,11,null),
    ParameterDef("i","Iodine","i",Unit.PPM,emptyList(),3,Range(0.05,0.06),Range(0.04,0.08),null,0.1,null,90,false,12,null),
    ParameterDef("orp","ORP","bolt",Unit.MV,emptyList(),0,Range(325.0,375.0),Range(300.0,400.0),250.0,420.0,null,7,false,13,null),
    ParameterDef("do","Dissolved O2","bubbles",Unit.MG_L,listOf(Unit.PCT_SAT),1,Range(6.5,7.0),Range(5.5,9.0),5.0,null,null,null,false,14,false),
    ParameterDef("tds","RO/DI TDS","filter",Unit.PPM,emptyList(),0,Range(0.0,0.0),Range(0.0,5.0),null,10.0,null,null,false,15,true),
    ParameterDef("sio2","Silicate","sio2",Unit.PPM,emptyList(),2,Range(0.0,0.5),Range(0.0,1.0),null,1.0,null,30,false,16,true),
    ParameterDef("fe","Iron","fe",Unit.PPM,emptyList(),3,Range(0.001,0.005),Range(0.0,0.01),null,0.02,null,365,false,17,true))
  val FOWLR, LPS, SPS: variants differing only in ideal ranges per 01_reef_care §1.1.
}
```
User overrides are stored in Room (`ParameterOverride`) and merged by `TargetsRepository.effective(paramId)`.

### 4.3 Status engine
```kotlin
enum class Status { GOOD, CAUTION, DANGER, UNKNOWN }
data class Reading(val paramId: String, val value: Double /*base unit*/, val epochMs: Long)
data class Evaluation(val status: Status, val reasons: List<Reason>, val trend: Trend, val staleDays: Double?)
sealed class Reason { data class OutOfIdeal(..), OutOfAcceptable(..), Danger(..), FastChange(val delta: Double, val days: Double, val cap: Double), Stale(val days: Double, val cadence: Int) }
object StatusEngine {
  fun evaluate(def: ParameterDef, history: List<Reading>, nowMs: Long): Evaluation
}
```
Rules: latest value vs ranges → GOOD/CAUTION/DANGER. FastChange: `|v_n − v_{n−1}| > cap × max(1, Δdays)` → DANGER (if cap non-null). Stale: `age > 1.5 × cadence` → CAUTION reason (does not downgrade GOOD→CAUTION in the tile color; shown as a stale chip). Trend: least-squares slope over last 3 points (≥ 2 days span) → `Trend.UP/DOWN/FLAT` using threshold 0.25 × cap/day (or 2% of the ideal span/day when cap null).

### 4.4 Chemistry
- `Ammonia.freeNh3(tanPpm, ph, tempC)`: `pKa = 0.09018 + 2729.92/(tempC+273.15)`; `f = 1/(1+10^(pKa−ph))`; returns `tan × f`. Danger if > 0.02.
- `Oxygen.saturation(tempC, ppt)`: Weiss-style approximation table at 35 ppt: 20→7.4, 22→7.1, 24→6.8, 25→6.6, 26→6.5, 28→6.2, 30→5.9 mg/L; subtract 0.2 per +1 °C beyond; add 0.05 mg/L per ppt below 35.
- `Dosing.grams(product, deltaPpmOrDkh, volumeGal)`: exactly 01_reef_care §3.3 constants (`CaCl2 anh w=0.361`, `CaCl2·2H2O 0.273`, `MgCl2·6H2O 0.120`, `MgSO4·7H2O 0.099`, NaHCO3 0.318 g/gal/meq, Na2CO3 0.200 g/gal/meq; dKH→meq ×0.357). Products are a data list with `gramsPerUnitPerGallon`; custom product supports `ppmPerMlPerGal` or `dkhPerMlPerGal`.
- `WaterChange.predict(before, mix, fraction)`: `after = before(1−f) + mix·f` per parameter.
- `SaltMix.grams(targetSg, gallons, saltGramsPerGalAt1025 = 136.0)`: linear in (SG−1.000)/0.025.
- `Evaporation.sgAfter(sg, totalGal, evapGalPerDay, days)`: `sg' = 1 + (sg−1) × V/(V − e·d)`.

### 4.5 Species DB
`core/src/main/resources/species/<id>.json`, one per species, listed in `species/index.json` (classpath folders cannot be listed on Android) and loaded by `SpeciesDb` (kotlinx.serialization). Pair risks live in `species/interactions.json` (02 §9 + 04 §14). Sheets are generated by `tools/species/gen_species.py` — edit the script, re-run, commit both. Schema in SIMULATION §3.2 (`SpeciesSheet`). Fish, inverts and corals share the file format with a `kind` discriminator.

---

## 5. `:app` packages

```
com.reeflive.app
  ReefLiveApp.kt            Application: builds AppGraph (manual DI), WorkManager init, notification channels
  di/AppGraph.kt            lazy singletons: db, repos, dataStore, clock, speciesDb, simulationHolder
  data/db/                  ReefDatabase.kt, entities/*.kt, dao/*.kt, Converters.kt, Migrations.kt
  data/repo/                MeasurementRepository, TaskRepository, LivestockRepository, EquipmentRepository,
                            JournalRepository, EventRepository, LightScheduleRepository, TargetsRepository, SettingsRepository (DataStore)
  data/seed/SeedInstaller.kt
  data/export/              CsvExporter, BackupManager (uses core/io)
  work/                     AlertWorker (periodic daily), TaskReminderWorker (one-time), ReminderScheduler, NotificationActions (BroadcastReceiver "Done")
  notif/Notifier.kt
  ui/theme/                 Color.kt (tokens), Type.kt, Shape.kt, Theme.kt, GlassCard.kt, CausticBackground.kt
  ui/nav/                   NavGraph.kt, Routes.kt, BottomBar.kt
  ui/components/            ParameterTile, HeroTile, StatusPill, TaskCard, LivestockCard, Chips, NumericField, EventMarker
  ui/charts/                TimeSeriesChart, Sparkline, ChartScale, ChartGestures, LightScheduleChart
  ui/dashboard/             DashboardScreen, DashboardViewModel
  ui/log/                   ParametersScreen, ParameterDetailScreen, QuickLogSheet, TestSessionScreen + VMs
  ui/care/                  CareScreen, TaskDetailScreen, calculators/*Screen, CareViewModel, CalculatorViewModels
  ui/more/                  MoreScreen, livestock/, equipment/, journal/, lights/, settings/, export/, tank/
  ui/onboarding/            OnboardingScreens, OnboardingViewModel
  ui/live/                  LiveTankScreen, LiveTankViewModel, LiveOverlay (HUD, sheet, FishCard), SandboxPanel
  render/                   see §8: GlView, Renderer, Scene, GlbLoader, Shaders, Materials, Camera, Effects, FishRig, CoralRig
```

### 5.1 DI (manual)
```kotlin
class AppGraph(app: Application) {
  val db by lazy { Room.databaseBuilder(app, ReefDatabase::class.java, "reeflive.db").addMigrations(*Migrations.ALL).build() }
  val settings by lazy { SettingsRepository(app.dataStore) }
  val speciesDb by lazy { SpeciesDb.load() }
  val measurements by lazy { MeasurementRepository(db.measurementDao(), db.sessionDao(), targets) }
  val targets by lazy { TargetsRepository(db.overrideDao(), settings) }
  ... (one lazy per repo)
  val simulationHolder by lazy { SimulationHolder(measurements, livestock, lights, speciesDb, assets = app.assets) }
}
val Context.appGraph get() = (applicationContext as ReefLiveApp).graph
```
ViewModels are created with a `viewModelFactory { initializer { XViewModel(appGraph.repoA, appGraph.repoB) } }` helper in `ui/nav/VmFactory.kt`. No Hilt, no KAPT.

### 5.2 ViewModels (StateFlow<UiState>, events via Channel)
- `DashboardViewModel`: combines latest evaluations for all parameters (`MeasurementRepository.latestEvaluations(): Flow<List<ParamEval>>`), due tasks, recent events, alerts → `DashboardState`.
- `QuickLogViewModel`: rows: `List<RowState(paramId, text, unit, status)>`, `save()` → `MeasurementRepository.saveSession(...)` then `AlertEvaluator.runNow()`.
- `ParameterDetailViewModel(paramId)`: history window, range, overlay param, stats, events.
- `CareViewModel`, `CalculatorViewModel(calcId)`, `LivestockViewModel`, `LivestockDetailViewModel(id)`, `EquipmentViewModel`, `JournalViewModel`, `LightScheduleViewModel`, `SettingsViewModel`, `ExportViewModel`, `OnboardingViewModel`.
- `LiveTankViewModel`: owns `SimulationHolder` lifecycle: `mode (REALITY|SANDBOX)`, `speed`, `selectedOrganismId`, `quality`, `simSnapshot: StateFlow<SimSnapshot>` (throttled 10 Hz for the HUD), sandbox actions → `Simulation.enqueue(Command)`.

---

## 6. Room schema (`ReefDatabase`, version 1)

All timestamps are epoch millis UTC (`Long`). IDs are `String` UUIDs except where noted. Values stored in the parameter's **base unit** (converted on input).

| Entity | Fields | Indices / relations |
|---|---|---|
| `TankEntity` | id PK, name, lengthCm, widthCm, heightCm, displayGallons, sumpGallons, reefType (FOWLR/SOFT/LPS/SPS), sandDepthCm, createdAt | single row in v1 |
| `TestSessionEntity` | id PK, tankId, takenAt, kitBrand?, note?, createdAt | idx(tankId, takenAt) |
| `MeasurementEntity` | id PK, sessionId FK→TestSession(cascade), tankId, paramId, value (base unit), enteredUnit, takenAt (denormalized copy), createdAt | idx(tankId, paramId, takenAt) unique(paramId, takenAt) |
| `ParameterOverrideEntity` | paramId PK, idealLo?, idealHi?, accLo?, accHi?, dangerLo?, dangerHi?, maxChange?, cadenceDays?, hidden, favorite, order, displayUnit? | |
| `AlertEntity` | id PK, tankId, paramId, kind (DANGER/FAST_CHANGE/CAUTION/STALE), message, createdAt, acknowledgedAt?, dayKey (yyyyMMdd) | unique(paramId, kind, dayKey) |
| `TaskEntity` | id PK, tankId, title, intervalDays?, weekdaysMask?, reminderHour, reminderMinute, equipmentId?, notes?, active, createdAt | idx(tankId) |
| `TaskCompletionEntity` | id PK, taskId FK(cascade), completedAt, note?, amount? | idx(taskId, completedAt) |
| `EventEntity` | id PK, tankId, type (FEED/WATER_CHANGE/DOSE/NOTE/LIGHT_CHANGE/ADD_LIVESTOCK/REMOVE_LIVESTOCK/TREATMENT), at, title, detailJson (type-specific: feed {food, amount}, water change {gallons, fraction, mix values}, dose {paramId, product, grams, expectedDelta}), journalId? | idx(tankId, at), idx(type) |
| `LivestockEntity` | id PK, tankId, speciesId, nickname, variant?, role? (FEMALE/MALE), lifeStage?, sizeCm, acquiredAt?, priceCents?, source?, status (ALIVE/REHOMED/DECEASED), statusChangedAt?, placement? (anchor name), verified (Boolean), notes?, photoPath?, createdAt | idx(tankId, status) |
| `HealthLogEntity` | id PK, livestockId FK(cascade), at, symptom, treatment?, dose?, outcome?, note? | idx(livestockId, at) |
| `EquipmentEntity` | id PK, tankId, type, name, brand?, model?, installedAt?, serviceIntervalDays?, lastServicedAt?, notes?, taskId? | |
| `JournalEntity` | id PK, tankId, at, text, tagsJson (livestock ids, event types) | idx(tankId, at) |
| `PhotoEntity` | id PK, journalId? FK, livestockId? FK, path (app files dir), takenAt, width, height | idx(journalId), idx(livestockId) |
| `LightScheduleEntity` | id PK, tankId, name, pointsJson (list of {minuteOfDay, channels[8] 0..1}), active | |
| `SaltBatchEntity` (v1.1) | id, mixedAt, sg, alk, ca, mg, note | |

Converters: `List<String>`↔JSON. DAOs expose `Flow` for reads. `MeasurementDao.history(paramId, fromMs)`, `latestPerParam(tankId)` (SQL `GROUP BY paramId HAVING MAX(takenAt)`), `inWindow(tankId, from, to)`.

Deletion policy: deleting a session deletes its measurements; deleting livestock keeps events (livestock id becomes dangling text in detailJson).

### 6.1 DataStore keys
`onboarded: Boolean`, `tempUnit`, `salUnit`, `alkUnit`, `theme (SYSTEM|DARK|LIGHT)`, `reminderHour`, `reduceMotion`, `haptics`, `liveQuality (LOW|MEDIUM|HIGH)`, `lastKitBrand`, `tileOrderJson`, `glassBlur`.

---

## 7. Status/alerts/reminders runtime

- `AlertEvaluator` (app) wraps `StatusEngine`: pulls last 30 d per parameter, computes evaluations, upserts `AlertEntity` (ignores duplicates via unique index), posts notifications for new rows. Called synchronously after `saveSession` (IO dispatcher) and by `AlertWorker` (PeriodicWorkRequest 24 h, flex 2 h, initial delay to 08:00).
- `ReminderScheduler.scheduleNext(task)` enqueues a `OneTimeWorkRequest` tagged `task:<id>` with delay to the next due datetime; re-run on completion, edit, and boot (`BOOT_COMPLETED` receiver re-schedules all).
- `InsightEngine` (core): produces one sentence per parameter from history: consumption (alk/ca/mg), drift (sal), ratio (no3:po4), stale. Pure functions with unit tests.

---

## 8. Live Tank renderer — decision and spec

### 8.1 Decision: custom OpenGL ES 3.0 renderer in Kotlin

Why not Filament: Filament ships on Maven Central (fine), but every custom look here (body-wave skinning along a spline, caustic projection, fog by depth, subsurface soft corals, polyp animation, iridescent fish) needs custom materials, which require `matc` at build time; that tool is not in the cloud container and adding it to CI is fragile. Filament's stock materials cannot express the fish deformation at all. A custom GLES 3.0 renderer is ~3–4k lines of Kotlin + GLSL, compiles shaders at runtime, and the asset pipeline is ours (Blender → GLB with only the features our loader reads). GLES 3.0 is universal on minSdk 26 devices.

### 8.2 Classes (`com.reeflive.app.render`)

| Class | Responsibility |
|---|---|
| `TankGlView : GLSurfaceView` | EGL config (RGBA8, depth 24, no stencil, MSAA 0 at Low, 2x at Medium, 4x at High), `setEGLContextClientVersion(3)`, `RENDERMODE_CONTINUOUSLY` while resumed, `TouchController` attached |
| `TankRenderer : GLSurfaceView.Renderer` | owns `Scene`, `FrameClock`, calls `Simulation.step` on the sim thread's snapshot; draw order §8.6 |
| `GlbLoader` | parses GLB (JSON chunk via `org.json`, BIN chunk), supports: meshes with POSITION/NORMAL/TEXCOORD_0/COLOR_0, indexed uint16/uint32, materials (baseColorFactor, baseColorTexture, metallic/roughness factors, emissiveFactor, `extras` dictionary), node hierarchy with TRS, images embedded as PNG/WebP in bufferViews (decoded with `BitmapFactory`), node/mesh `extras` (we use `extras.part` = "body"/"fin"/"eye"/"polyp" and `extras.anchor`). No skins, no animations, no KHR extensions required (KHR_materials_emissive_strength optional). |
| `Mesh` | VAO + VBO (interleaved pos3 nrm3 uv2 col4 + extra attribute `aSpine` float (0..1 along body, baked into COLOR_0.a by the Blender exporter) ), IBO, draw(range) |
| `Texture2D` | upload, mipmaps, trilinear, anisotropy 4 if supported; sRGB decode done in shader (textures stored sRGB, `pow(2.2)` in fragment) |
| `ShaderProgram` | compile/link at runtime from `assets/shaders/*.vert/.frag`; `#define` injection for quality; uniform cache |
| `Material` | program + uniforms + textures; variants: `RockPbr`, `SandPbr`, `SoftCoral`, `PolypInstanced`, `FishSkin`, `FinSheet`, `Glass`, `WaterSurface`, `Particles`, `Caustic` (shared texture), `Unlit` |
| `Scene` | static nodes (tank, rock, sand, equipment, corals) + dynamic `OrganismNode`s; lights (`TankLight` x2) |
| `Camera` | orbit camera: target, yaw, pitch, distance, fov 50°; follow mode with smoothing |
| `TouchController` | 1-finger orbit, 2-finger pan/pinch, tap (select via ray vs organism bounding capsules), double-tap (tap glass), long-press (drop food) |
| `FishRig` | per organism: spline control points (16), swim phase, amplitude, fin states → uniforms |
| `CoralRig` | per coral: extension 0..1, sway phase, bleaching 0..1, browning 0..1 → uniforms |
| `Effects` | caustic projector (texture scroll), god-ray quads, particulate (GPU points), bubbles from HOB return |
| `LightingState` | current 8-channel intensities → key light color/intensity, ambient, fog color |
| `Picker` | ray cast from touch against organism capsules (CPU) |
| `DebugHud` | fps, draw calls, tri count (dev builds) |

### 8.3 Coordinate system and scale
World units = centimeters. Origin at the tank's inside bottom-front-left corner; +X right (length 121.9), +Y up (61.0), +Z toward the viewer (depth 33.0; back glass at z=0, front glass at z=33). Sand top at y=3. Water surface at y=56 (tank filled to ~2" from the top). All assets exported in this frame.

### 8.4 Shaders (GLSL ES 3.00; files in `assets/shaders/`)

Common uniforms (UBO `Frame`): `uView, uProj, uViewProj, uCamPos, uTime, uLightDir[2], uLightColor[2] (linear RGB × intensity), uLightPos[2], uAmbient (vec3), uFogColor, uFogDensity, uWaterLevel, uCausticStrength, uCausticScale, uQuality`.

1. **`rock.frag` / `sand.frag` (PBR-lite)**: Lambert + GGX specular (roughness from texture; rock 0.85, sand 0.9), normal map (rock only; sand uses a tiled 256² detail normal), 2 directional-ish spot lights (AI Primes modeled as point lights with `cosine falloff` cone 120°), hemispherical ambient (`uAmbient` top `#1A3550`, bottom `#06101C`, both scaled by light intensity), **caustics**: `tex(uCaustic, worldPos.xz × uCausticScale + uTime×v1) × tex(uCaustic, worldPos.xz × uCausticScale×1.7 − uTime×v2)` multiplied by `max(0, normal.y)` and by light intensity, added as `albedo × caustic × uCausticStrength (0.6)`. Depth fog: `fogFactor = 1 − exp(−uFogDensity × dist)`, `uFogDensity = 0.004/cm` tinted `uFogColor` (#0E3B5A scaled by light), applied in all opaque shaders. Coralline/algae/hair masks are baked into the albedo; `uAlgaeBloom (0..1)` lerps toward green `#3B5B2E` at rock surfaces facing up (SIMULATION §2.6). Per-surface algae cover (`SimSnapshot.algae`, `AlgaeCover`) is packed into four more `vec4`s of the `Frame` UBO and drawn as glass, sand, rock-top and shade masks (SIMULATION §11).
2. **`softcoral.vert/frag`** (Kenya, mushroom, aiptasia column): vertex sway: `pos += swayDir × sin(uTime×ω + pos.y×0.15 + uPhase) × amp × (pos.y − basey)/height`; `amp` = 0.08×height × flow magnitude. Extension: `pos = base + (pos − base) × mix(0.55, 1.0, uExtension)`. Fragment: diffuse wrap lighting (`NdotL×0.5+0.5`), **subsurface approximation**: back-light term `pow(saturate(dot(V, −L)), 3) × uSssColor × thickness` where thickness = vertex color R baked in Blender (AO-inverse); rim: `pow(1−NdotV, 2.5) × 0.35 × lightColor`; bleaching: `albedo = mix(albedo, vec3(0.95), uBleach)`; browning: `mix(albedo, #7A5A3A, uBrown×0.4)`; polyp sparkle: tiny emissive from the polyp mask texture channel (G) scaled by `uExtension`.
3. **`polyp.vert/frag` (instanced)**: GSP, zoa, clove, Kenya polyp tufts. Per-instance attribute buffer: position (3), tangent frame (quaternion 4), scale (1), phase (1), colorVariant (1). Vertex: tentacle open/close by `uExtension × (0.6 + 0.4 sin(uTime×0.3 + phase))`, each tentacle vertex rotated about the polyp axis by `(1−open)×1.2 rad`; sway as above. Fragment: unlit-ish wrap with emissive green for GSP (`#7DFF4F` × 0.6 under blue light: emissive scaled by `uBlueFraction`, giving the fluorescent pop).
4. **`fish.vert`**: spline deformation (SIMULATION §4.8): `aSpine` (0 head → 1 tail) → sample `uSpine[16]` (vec4 pos + packed frame) with Catmull-Rom; vertex local y/z offsets applied in the sampled frame; plus lateral wave `z += A(u) × sin(2π(u×uWaveLen − uPhase))` with `A(u) = uAmp × (0.1 + 0.9u²)`; fins: `extras.part=="fin"` vertices get an additional flutter `± 0.02×BL × sin(uTime×12 + u×6)` and a pectoral fan angle uniform `uPectoral` (0 folded, 1 spread). Normals recomputed by deforming a tangent-offset point (cheap finite difference) at High quality; at Low/Medium, rotate the normal by the spline frame only.
5. **`fish.frag` (iridescent/glossy skin)**: albedo texture (sRGB), GGX spec with roughness 0.35, **iridescence**: `thinFilm = tex(uIridLut, vec2(NdotV, 0))` (a 64×1 ramp blue→cyan→violet) × `uIridStrength` (species: tang 0.35, clown 0.12, wrasse 0.5, cardinal 0.15, hawkfish 0.1, blenny 0.2, eel 0.05) added to specular; rim light; **symptoms**: `uDesat` (0..1) → `mix(color, luminance, uDesat)`; `uIchSpots` (0..1): procedural white dots: `step(0.92 − uIchSpots×0.3, hash(floor(uv×140)))` with size 1–2 texels, applied to body and fins; `uPallor` for cleaner wrasse thinness is geometric (uThin scales body y/z by `1 − 0.25×uThin`); dead: `uDesat=0.8`, eye uniform cloud `uEyeCloud`. Gill flare: `uGill` scales the opercular region (mask in COLOR_0.g) outward `+0.03 BL × uGill × sin(uTime × gillRate)`.
6. **`fin.frag`**: translucent fin sheet: alpha from texture (ray lines baked), two-sided, `alpha = tex.a × (0.75 + 0.25 NdotV)`, lit as wrap diffuse; dorsal/anal clamping is geometric (`uClamp` rotates fin vertices toward the body: fins are modeled extended; vertex rotation about the fin base line by `uClamp × 60°` using baked per-vertex hinge data in COLOR_0.b (distance from base)).
7. **`glass.frag`**: front/side panes rendered last, additive Fresnel: `F = 0.02 + 0.98(1−NdotV)^5`, reflection color = a 6-color static gradient "room" cubemap approximation (`assets/textures/room_env.png` 256×128 equirect), alpha `F×0.6`; slight green tint `#E0F8F0` × 0.05 on transmission (we don't render transmission; the scene behind is already drawn). Back glass: opaque black `#05070A` with 0.1 roughness reflection of the key lights (two soft blobs) — this matches the photos' dark back wall.
8. **`watersurface.vert/frag`**: a plane at y=56 seen from below: scrolling 2 normal maps (`water_n1.png`, `water_n2.png` 512², speeds (1.5, 0.8) and (−1.0, 1.2) cm/s), Fresnel from below with total internal reflection: if `angle(V, up) > 48.6°` → reflect the scene color approximation (fog color × 1.6 + light blobs) else show the "room above" color (`#0A0F1A` + light fixture glow discs at the two AI Prime positions). Light shafts: 6 vertical quads per light, billboarded about Y, additive, alpha `0.08 × lightIntensity × (1−y/56)`, scrolling noise; disabled at Low.
9. **`particles.vert/frag`**: GL_POINTS, 300 (Low 120) suspended particles with positions advected on the CPU by the flow field (SIMULATION §5.6), size 2–4 px, alpha 0.15, slightly bluish-white; food particles use the same pipeline with size 5–7 px and color by food type (flake `#C8A060`, mysis `#E8D8C0`, nori `#2E4A1A`).
10. **`caustic` shared texture**: `assets/textures/caustic_512.png` (tileable, generated by `tools/textures/caustics.py`: Voronoi edge intensity, 3 octaves, gamma 2.2).

Color management: all lighting in linear space; final `pow(1/2.2)` + ACES-ish tonemap `x/(x+0.155)×1.019` at the end of each opaque shader (no post-process pass at Low/Medium; High renders to an FBO and applies a 1-pass bloom (threshold 0.8, 9-tap blur at half res) for the fluorescent corals and iridescence).

### 8.5 Lighting from the AI Prime schedule
`LightingState.from(channels[8])` (0..1 each). Per-channel linear RGB (from 03 §F2): white (0.85,0.93,1.0), blue (0.23,0.63,1.0), royal (0.18,0.36,1.0), red (1.0,0.3,0.3), green (0.3,1.0,0.54), violet (0.42,0.24,1.0), UV (0.35,0.2,0.9)×0.5, moon (0.17,0.23,0.43)×0.15. `keyColor = Σ chan_i × rgb_i × weight_i` with weights (4,4,4,1,1,1,1,1)/16 (diode counts), `intensity = clamp(Σ chan_i × weight_i, 0, 1)`. Each AI Prime is a point light at the lens centres (32, 90, 16) and (91, 90, 16) cm (equipment.glb `lightPos`; above the water, ~29 cm over the rim) with cone 120° downward. Ambient = keyColor × 0.18. Fog color = keyColor × `#0E3B5A`/`#FFFFFF` × intensity + `#02060C`. `uBlueFraction = (blue+royal+violet)/(white+blue+royal+violet+ε)` drives coral fluorescence emissive. Night (intensity < 0.03): moon channel only; particles alpha halves; fish shaders get `uNight` to reduce specular.

### 8.6 Frame and draw order
1. Sim snapshot read (double-buffered `SimSnapshot` from the sim thread, no locks held during draw).
2. Update rigs (spline uniforms, coral states) — CPU, < 1 ms.
3. Opaque: back glass → sand → rock (1 draw) → equipment (≤ 6 draws) → corals (soft bodies ≤ 8 draws) → polyp instanced batches (4 draws: GSP×2, zoa, clove, Kenya tufts) → fish bodies (9 draws) → shrimp (1).
4. Alpha-tested fins (9 draws).
5. Transparent, back-to-front: light shafts (High/Medium), particles, water surface, front/side glass.
6. High only: bloom composite.
Target: ≤ 45 draw calls, ≤ 140k triangles on screen.

### 8.7 Performance budget (mid-range phone, 1080p, Medium)
- Fish LOD0 ≤ 5k tris each (eel 6k), LOD1 (camera > 60 cm) 1.5k. Rock 40k, sand 2k, corals 25k total + polyps instanced (GSP 1200 instances × 24 tris = 29k; zoa 250 × 36; clove 300 × 30; Kenya tufts 600 × 12). Total ≈ 130k tris.
- Textures: rock 2048² albedo + 1024² normal/roughness (ETC2/ASTC not required; PNG decoded to RGBA8, mipmapped), fish 1024² each (9 → ~36 MB VRAM uncompressed; acceptable; Low quality downsamples to 512²).
- Frame target: 60 fps High-end, 30–45 fps mid-range at Medium; Low disables shafts, bloom, normal maps, halves particles, MSAA 0.
- Sim thread runs independently at 20 Hz (SIMULATION §1.2); renderer interpolates organism positions between the last two snapshots.

### 8.8 Sim ↔ render interface (core, pure data)
```kotlin
data class OrganismPose(val id: String, val speciesId: String, val spine: FloatArray /*16×3*/, val frames: FloatArray /*16×4 quats*/,
    val swimPhase: Float, val amp: Float, val waveLen: Float, val pectoral: Float, val finClamp: Float, val gill: Float, val gillRate: Float,
    val desat: Float, val ich: Float, val thin: Float, val eyeCloud: Float, val alive: Boolean, val scale: Float)
data class CoralPose(val id: String, val extension: Float, val bleach: Float, val brown: Float, val algae: Float, val swayPhase: Float)
data class SimSnapshot(val simTimeSec: Double, val lights: FloatArray /*8*/, val water: WaterState, val organisms: List<OrganismPose>,
    val corals: List<CoralPose>, val food: FloatArray /*xyz×N*/, val foodKinds: ByteArray, val algaeBloom: Float, val algae: AlgaeCover = AlgaeCover.EMPTY, val events: List<SimEvent>)
```
Renderer never calls into the simulation except `Simulation.enqueue(Command)`; the simulation never references GL.

---

## 9. Threads
- Main: Compose.
- `SimThread` (single `HandlerThread`/coroutine dispatcher `newSingleThreadContext("sim")`): fixed-step loop; publishes snapshots via `AtomicReference`.
- GL thread: render only.
- IO dispatcher: Room, files, export.
- Background sim is paused when `LiveTankScreen` leaves RESUMED; on return it fast-forwards reality by the elapsed wall time (capped at 24 h of sim).

---

## 10. Testing
- `:core` JUnit: unit conversions (round trips), StatusEngine (table-driven cases), Ammonia (worked example 25 °C/pH 8.2 → f=0.0825±0.001), Dosing (60 gal +1 dKH baking soda → 6.8 g ±0.1), WaterChange, WaterSim invariants (mass balance of N over 30 simulated days; salinity rises with evaporation; nitrification converts 1 ppm NH3 → ~0 within 3 days at mature bacteria), HealthModel (tang at 30 °C for 48 h → ich onset), AI determinism (seeded RNG → same trajectories), SDF queries vs analytic shapes, Spline sampling continuity.
- `:app` unit: repositories with Room in-memory (Robolectric optional — skip to keep CI simple; test DAOs via instrumented tests only when an emulator is added later), CSV codec round trip, `LightingState` math.
- Manual QA checklist in PLAN.md per milestone.

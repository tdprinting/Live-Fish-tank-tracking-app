package com.reeflive.app.live

import android.content.res.AssetManager
import com.reeflive.app.data.repo.Clock
import com.reeflive.app.data.repo.CustomTankStore
import com.reeflive.app.data.repo.LightScheduleRepository
import com.reeflive.app.data.repo.LivestockRepository
import com.reeflive.app.data.repo.MeasurementRepository
import com.reeflive.core.custom.CustomTank
import com.reeflive.core.custom.CustomTankCodec
import com.reeflive.core.custom.CustomTankSave
import com.reeflive.core.custom.TankBuild
import com.reeflive.core.custom.TankBuilds
import com.reeflive.core.custom.economy.BuyResult
import com.reeflive.core.custom.economy.DisplayTank
import com.reeflive.core.custom.AwayCatchUp
import com.reeflive.core.custom.AwaySummary
import com.reeflive.core.custom.economy.Economy
import com.reeflive.core.custom.economy.FishStore
import com.reeflive.core.custom.economy.Offers
import com.reeflive.core.custom.economy.SellOffer
import com.reeflive.core.custom.economy.ShelfCard
import com.reeflive.core.custom.economy.StoreTrades
import com.reeflive.core.custom.economy.StoreVisit
import com.reeflive.core.custom.economy.TradeOffer
import com.reeflive.core.custom.economy.EconomyView
import com.reeflive.core.custom.economy.Gain
import com.reeflive.core.live.FastForward
import com.reeflive.core.live.LivePeek
import com.reeflive.core.live.RealityInit
import com.reeflive.core.live.Scenario
import com.reeflive.core.live.ScenarioQueue
import com.reeflive.core.model.LightSchedule as DbLightSchedule
import com.reeflive.core.model.Livestock
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.LivestockSeed
import com.reeflive.core.sim.Reality
import com.reeflive.core.sim.RockData
import com.reeflive.core.sim.SimSnapshot
import com.reeflive.core.sim.SimSpecies
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.SpeciesCatalog
import com.reeflive.core.sim.StockSize
import com.reeflive.core.sim.TankConfig
import com.reeflive.core.sim.WaterState
import com.reeflive.core.sim.timelapse.CancelToken
import com.reeflive.core.sim.timelapse.TimelapseFork
import com.reeflive.core.sim.timelapse.TimelapseRun
import com.reeflive.core.sim.timelapse.TimelapseSpec
import com.reeflive.core.sim.timelapse.TimelapseState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.Executors

enum class SimMode { REALITY, SANDBOX, CUSTOM }

/**
 * A walk around a fish store (docs/ECONOMY.md §5): its display tanks (the 3D view shows [at]), each with its cards, the
 * keeper's sand dollars, and which species each animal in the shown tank is (for taps in the tank).
 */
data class StoreVisitUi(
    val store: FishStore,
    val tanks: List<Pair<DisplayTank, List<ShelfCard>>>,
    val at: Int,
    val coins: Int,
    val speciesOf: Map<String, String>,
    /** The store's offer for each of the keeper's animals and corals (the Sell sheet). */
    val offers: List<SellOffer> = emptyList(),
)

/** The street of stores for the Add life card: each store and whether it is open to this keeper. */
data class StoreFrontUi(val store: FishStore, val open: Boolean)

/** My tank's room shop card (core room/Decor.kt): its tiles, the money, and what is being tried on. */
data class RoomShopUi(val tiles: List<com.reeflive.core.custom.room.Decor.Tile>, val coins: Int, val trying: String?)

/** "My tank" as the screen sees it: whether one is set up yet, its name and build. */
/** The welcome-back card: how far the catch-up is ([daysDone] of [days]) and, once done, what happened. */
data class AwayUi(val progress: Double, val daysDone: Double, val days: Double, val summary: AwaySummary?)

data class CustomTankInfo(val ready: Boolean, val exists: Boolean, val name: String = "", val build: TankBuild = TankBuilds.DEFAULT)

/**
 * Owns the [Simulation] on a dedicated "sim" thread (ARCHITECTURE §9) and glues it to the app data.
 *
 * - REALITY: initialised from the latest logged values, alive livestock and the active light schedule; runs 1x;
 *   a newly logged measurement is applied as a `SetParam` as soon as the repository flow emits (well under 2 s).
 * - SANDBOX: a forked copy of reality's current state with a speed multiplier; [resetSandbox] restores the fork.
 * - CUSTOM ("My tank"): the keeper's own tank, started empty from a [TankBuild] and saved to [customStore]
 *   (core CustomTank) every [CustomTank.AUTOSAVE_MS] on screen, when it leaves the screen and when the mode
 *   changes. Reopening skips it forward by the time away (1x, capped at 24 h, as reality).
 * - [onForeground]/[onBackground]: the loop only runs while the Live tab is RESUMED. Returning fast-forwards the
 *   reality sim by the elapsed wall time, capped at 24 h of sim ([FastForward]).
 *
 * All simulation state is confined to the sim thread; the public API is safe to call from any thread.
 */
class SimulationHolder(
    private val measurements: MeasurementRepository,
    private val livestock: LivestockRepository,
    private val lights: LightScheduleRepository,
    private val assets: AssetManager?,
    private val clock: Clock = Clock.SYSTEM,
    /** "Rocks can shift" (Settings): the dynamic rockscape for the tank and its sandbox forks. */
    private val dynamicRocks: Flow<Boolean> = flowOf(true),
    /** The tank's rock layout between launches (rocks that settled or toppled stay where they fell). */
    private val rockStore: RockStore? = null,
    private val customStore: CustomTankStore? = null,
    /** Gets the My tank care plan when the app goes to the background and a cancel when it is back (CareAlerts). */
    private val careAlerts: CareAlertSink? = null,
) {
    /** Where My tank's "needs you" notifications go (the app schedules them; nothing here knows how). */
    interface CareAlertSink {
        fun schedule(plan: List<com.reeflive.core.custom.PlannedAlert>)
        fun cancel()
    }

    /** Where the reality tank's moved rocks are kept between launches ([Simulation.rockState]). */
    interface RockStore {
        fun load(): String
        fun save(state: String)
    }

    /** A small text file (app files dir). */
    class FileRockStore(private val file: java.io.File) : RockStore {
        override fun load(): String = runCatching { if (file.isFile) file.readText() else "" }.getOrDefault("")
        override fun save(state: String) { runCatching { file.writeText(state) } }
    }

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "sim").apply { isDaemon = true } }
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())

    private val _snapshots = MutableStateFlow(SimSnapshot.EMPTY)
    /** Latest snapshot at ~20 Hz while running (renderer / HUD). */
    val snapshots: StateFlow<SimSnapshot> = _snapshots.asStateFlow()

    private val _mode = MutableStateFlow(SimMode.REALITY)
    val mode: StateFlow<SimMode> = _mode.asStateFlow()

    private val _speed = MutableStateFlow(1.0)
    /** Sandbox speed multiplier (1..1000). Reality always runs at 1x. */
    val speed: StateFlow<Double> = _speed.asStateFlow()

    private val _lifePace = MutableStateFlow(com.reeflive.core.sim.LifePace.DEFAULT)
    /** My tank's growing speed for slow biology (Simulation.lifePace); motion stays at [speed]. */
    val lifePace: StateFlow<com.reeflive.core.sim.LifePace> = _lifePace.asStateFlow()

    private val _peek = MutableStateFlow(LivePeek())
    /** Dashboard peek summary; follows the reality sim (kept up to date even while the tab is away). */
    val peek: StateFlow<LivePeek> = _peek.asStateFlow()

    private val _timelapse = MutableStateFlow<TimelapseState>(TimelapseState.Idle)
    /** Time-lapse of the active sim (idle / running with progress / done with recording and report). */
    val timelapse: StateFlow<TimelapseState> = _timelapse.asStateFlow()
    private val timelapseScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timelapseCancel: CancelToken? = null

    private val _custom = MutableStateFlow(CustomTankInfo(ready = false, exists = false))
    /** "My tank": not looked at yet, none set up (show the build picker), or set up. */
    val custom: StateFlow<CustomTankInfo> = _custom.asStateFlow()

    /** What happened to My tank's corals while the app was away (core CoralHealth.changes); the screen shows and clears it. */
    private val _coralNews = MutableStateFlow<List<String>>(emptyList())
    val coralNews: StateFlow<List<String>> = _coralNews.asStateFlow()
    fun clearCoralNews() { _coralNews.value = emptyList() }

    /** [advance] on [sim], noting what changed for its corals when it is My tank. */
    private inline fun withCoralNews(sim: Simulation, advance: () -> Unit) {
        if (sim !== customSim) { advance(); return }
        val before = com.reeflive.core.sim.CoralHealth.marks(sim.corals)
        advance()
        val lines = com.reeflive.core.sim.CoralHealth.changes(before, sim.corals)
        if (lines.isNotEmpty()) _coralNews.value = lines
    }

    private val _economy = MutableStateFlow<EconomyView?>(null)
    /** My tank's sand dollars, care list and goals (docs/ECONOMY.md); null while there is no My tank. */
    val economy: StateFlow<EconomyView?> = _economy.asStateFlow()
    private val _gains = MutableSharedFlow<Gain>(extraBufferCapacity = 16)
    /** Money that just came in (the coin cheer). */
    val gains: SharedFlow<Gain> = _gains.asSharedFlow()

    private val _visit = MutableStateFlow<StoreVisitUi?>(null)
    /** The store being visited, or null at home. */
    val visitState: StateFlow<StoreVisitUi?> = _visit.asStateFlow()
    /** A store regular's swap, once they have walked up and asked (the trade card is up), else null. */
    private val _trade = MutableStateFlow<TradeOffer?>(null)
    val tradeState: StateFlow<TradeOffer?> = _trade.asStateFlow()
    /** Today's swap at the store being visited (core StoreTrades), if a regular has one. */
    private var tradeOffer: TradeOffer? = null

    private val _paid = MutableSharedFlow<Pair<Int, Int>>(extraBufferCapacity = 4)
    /** A checkout went through: (sand dollars paid, animals bought); the screen plays the pay animation. */
    val paid: SharedFlow<Pair<Int, Int>> = _paid.asSharedFlow()

    private val _away = MutableStateFlow<AwayUi?>(null)
    /** My tank's "while you were away" catch-up and its summary (docs/ECONOMY.md §9); null when nothing to show. */
    val awayState: StateFlow<AwayUi?> = _away.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    // ---- confined to the sim thread ----
    private var species: SpeciesCatalog? = null
    private var rock: RockData? = null
    private var reality: Simulation? = null
    private var sandbox: Simulation? = null
    private var fork: Fork? = null
    private var customSim: Simulation? = null
    private var customBase: CustomTankSave? = null
    private var customLoaded = false
    private var lastCustomSaveMs = 0L
    /** My tank's economy; kept across "Start over" so the keeper's money and level stay theirs. */
    private var economyLive: Economy? = null
    /** The stand and room bought for My tank (core room/Decor.kt); kept across "Start over" like the money. */
    private var decorLive = com.reeflive.core.custom.room.SavedDecor()
    /** The room shop is trying something on (shown, not bought). */
    private var decorPreview: String? = null
    /** A long time-away catch-up in progress ([beginAway]). */
    private var away: AwayCatchUp? = null
    /** The corals' health marks when a long catch-up began, for the coral news when it ends. */
    private var awayCorals: List<com.reeflive.core.sim.CoralHealth.Mark>? = null
    private var lastEconomyMs = 0L
    /** The display tank on screen while visiting a store (My tank waits meanwhile, then catches up). */
    private var display: Simulation? = null
    private var visiting: FishStore? = null
    private var visitTanks: List<DisplayTank> = emptyList()
    private var lastWallMs = HashMap<Simulation, Long>()
    private val scenarios = ScenarioQueue()
    private var tickJob: Job? = null
    private var foreground = false
    private var lastEvent: String? = null

    private var latestValues: Map<String, Double> = emptyMap()
    private var alive: List<Livestock> = emptyList()
    private var schedule: DbLightSchedule? = null
    private var appliedValues: Map<String, Double> = emptyMap()
    private var structureKey: Pair<List<Livestock>, DbLightSchedule?>? = null

    private class Fork(val water: WaterState, val seeds: List<LivestockSeed>)

    private val active: Simulation? get() = when (_mode.value) {
        SimMode.SANDBOX -> sandbox ?: reality
        SimMode.CUSTOM -> display ?: customSim
        SimMode.REALITY -> reality
    }

    private var rocksDynamic = true
    private var savedRocks: String? = null

    init {
        scope.launch {
            dynamicRocks.collect { on ->
                rocksDynamic = on
                reality?.enqueue(Command.DynamicRocks(on))
                sandbox?.enqueue(Command.DynamicRocks(on))
                customSim?.enqueue(Command.DynamicRocks(on))
            }
        }
        scope.launch {
            combine(measurements.latestValues(), livestock.alive(), lights.active()) { v, a, l -> Triple(v, a, l) }
                .collect { (v, a, l) -> onInputs(v, a, l) }
        }
    }

    // ============================================================== public API (any thread)
    fun enqueue(cmd: Command) { scope.launch { active?.enqueue(cmd) } }
    /** A loaded species sheet, or null (read from the UI thread: sheets are only added while the sims are built). */
    fun simSpecies(id: String): SimSpecies? = species?.takeIf { it.has(id) }?.get(id)

    fun setMode(m: SimMode) { scope.launch { switchMode(m) } }

    fun setSpeed(speed: Double) {
        val s = speed.coerceIn(1.0, 1000.0)
        _speed.value = s
        scope.launch { sandbox?.speed = s; if (_mode.value == SimMode.CUSTOM) customSim?.speed = s }
    }

    fun setLifePace(p: com.reeflive.core.sim.LifePace) {
        _lifePace.value = p
        scope.launch { customSim?.lifePace = p.pace }
    }

    /** Sets up a new, empty "My tank" in [build] (replacing none: call [startOverCustomTank] first) and shows it. */
    fun startCustomTank(build: TankBuild, name: String = "My tank") {
        scope.launch {
            ensureAssets()
            val now = nowMs()
            val seed = now xor 0x5EEDL
            val sim = CustomTank.fresh(build, species!!, rock!!, localSecondsOfDay(), seed)
            sim.speed = 1.0
            sim.lifePace = _lifePace.value.pace
            customSim?.let { lastWallMs.remove(it) }
            customSim = sim
            val eco = economyLive ?: Economy()
            economyLive = eco
            customBase = CustomTankSave(name = name, buildId = build.id, createdAtMs = now, seed = seed, economy = eco.state, decor = decorLive)
            customLoaded = true
            lastWallMs[sim] = now
            saveCustom()
            _custom.value = CustomTankInfo(ready = true, exists = true, name = name, build = build)
            updateEconomy(sim, force = true)
            if (_mode.value == SimMode.CUSTOM) { _speed.value = 1.0; publish(sim) }
        }
    }

    // ---- the room: stands (core room/Decor.kt) ----
    /** The stand and room round the tank (render HomeRoom.kt): My tank's bought ones, Tee's in Watch. */
    val roomControl = com.reeflive.app.render.RoomControl()
    private val _room = MutableStateFlow<RoomShopUi?>(null)
    /** The room shop card's tiles and the money; null while there is no My tank. */
    val roomShop: StateFlow<RoomShopUi?> = _room.asStateFlow()

    private fun showDecor(m: SimMode = _mode.value) {
        val k = decorPreview
        roomControl.look = if (m != SimMode.CUSTOM) com.reeflive.app.render.RoomLook.DEFAULT
            else com.reeflive.app.render.RoomLook.of(if (k != null) com.reeflive.core.custom.room.Decor.preview(decorLive, k) else decorLive)
        val e = economyLive
        _room.value = if (e == null) null else RoomShopUi(com.reeflive.core.custom.room.Decor.tiles(decorLive, e.level), e.state.coins, k)
    }

    /** Buys room item [key] (it goes straight into use); [onResult] gets the result on the sim thread. */
    fun decorBuy(key: String, onResult: (com.reeflive.core.custom.room.DecorResult) -> Unit = {}) {
        scope.launch {
            val e = economyLive ?: return@launch
            val (d, r) = com.reeflive.core.custom.room.Decor.buy(decorLive, key, e, today())
            if (r == com.reeflive.core.custom.room.DecorResult.OK) {
                decorLive = d; decorPreview = null
                customSim?.let { updateEconomy(it, force = true) }
                saveCustom()
            }
            showDecor()
            onResult(r)
        }
    }

    /** Puts an owned stand, colour or extra to use (an extra toggles). */
    fun decorUse(key: String) {
        scope.launch {
            decorPreview = null
            val d = com.reeflive.core.custom.room.Decor.use(decorLive, key)
            if (d != decorLive) { decorLive = d; saveCustom() }
            showDecor()
        }
    }

    /** Tries [key] on in the 3D view without buying it (null ends the try-on). */
    fun decorTry(key: String?) { scope.launch { decorPreview = key; showDecor() } }

    /** The four storefronts and which are open to this keeper (the Add life card in My tank). */
    fun storeFronts(): List<StoreFrontUi> = economyLive.let { e -> FishStore.entries.map { StoreFrontUi(it, e != null && e.canSee(it)) } }

    /** Walks into [store]: the 3D view shows its first display tank; My tank waits and catches up on the way out. */
    fun enterStore(store: FishStore) {
        scope.launch {
            val e = economyLive ?: return@launch
            if (customSim == null || away != null || !e.canSee(store) || _mode.value != SimMode.CUSTOM) return@launch
            if (visiting == null) saveCustom()
            visiting = store
            visitTanks = StoreVisit.tanks(store, today())
            display = null
            tradeOffer = customSim?.let { StoreTrades.offer(store, it, e, today()) }
            showTankNow(0)
        }
    }

    /** Shows display tank [i] (one runs at a time). */
    fun showStoreTank(i: Int) { scope.launch { showTankNow(i) } }

    /** The 3D shop around the display tank (render StoreRoom.kt): which store, its tanks and the one shown. */
    val storeRoom = com.reeflive.app.render.StoreRoomControl()

    private fun showTankNow(i: Int) {
        val n = visitTanks.size
        if (n == 0 || visiting == null) return
        val k = ((i % n) + n) % n
        ensureAssets()
        val t = visitTanks[k]
        val d = StoreVisit.build(t, species!!, rock!!, (today().hashCode() * 31L + k))
        display?.let { lastWallMs.remove(it) }
        display = d
        lastWallMs[d] = nowMs()
        val store = visiting!!
        if (storeRoom.storeId != store.id) storeRoom.enter(store.id, n, k, visitTanks.map { it.setup }, tradeOffer?.line) else storeRoom.show(k)
        publishVisit(k)
        publish(d)
    }

    private fun publishVisit(at: Int = _visit.value?.at ?: 0) {
        val store = visiting ?: run { _visit.value = null; return }
        val e = economyLive ?: return
        val mine = customSim ?: return
        val day = today()
        val d = display
        val speciesOf = d?.organisms?.associate { it.id to it.species.id }.orEmpty()
        _visit.value = StoreVisitUi(
            store, visitTanks.map { it to StoreVisit.cards(e, store, it, mine, day) }, at, e.state.coins, speciesOf, Offers.offers(store, mine, day),
        )
    }

    /** Sells the keeper's animal or coral [id] to the store being visited (docs/ECONOMY.md §6). */
    fun sell(id: String) {
        scope.launch {
            val store = visiting ?: return@launch
            val e = economyLive ?: return@launch
            val mine = customSim ?: return@launch
            val o = Offers.offers(store, mine, today()).firstOrNull { it.id == id } ?: return@launch
            if (!e.sell(store, o, today())) return@launch
            mine.enqueue(if (o.coral) Command.RemoveCoral(id) else Command.RemoveOrganism(id))
            // out of the tank now, though the tank itself waits until the keeper is home
            mine.stepWall(0.0)
            for (g in e.takeGains()) _gains.tryEmit(g)
            _economy.value = e.view(mine)
            saveCustom()
            publishVisit()
        }
    }

    /** The store's regular walked up with their swap (StoreRoomControl.onTrade): the trade card shows. */
    fun showTrade() { scope.launch { if (visiting != null) _trade.value = tradeOffer } }

    /**
     * The keeper's answer to the swap: yes cuts the frag (or nets the spare) and floats the new animal in a bag in My
     * tank, and the regular thanks them; no, and they shrug it off. Either way, no more swaps at this store today on yes.
     */
    fun answerTrade(yes: Boolean) {
        scope.launch {
            val o = _trade.value ?: return@launch
            _trade.value = null
            tradeOffer = null
            storeRoom.tradeReply(yes)
            if (!yes) return@launch
            val e = economyLive ?: return@launch
            val mine = customSim ?: return@launch
            StoreTrades.apply(mine, o)
            e.traded(o, today())
            mine.stepWall(0.0)
            _economy.value = e.view(mine)
            saveCustom()
            publishVisit()
        }
    }

    /** Walks out (the bag goes back on the shelves); My tank catches up on the minutes spent shopping. */
    fun leaveStore() { scope.launch { leaveStoreNow() } }

    private fun leaveStoreNow() {
        if (visiting == null) return
        display?.let { lastWallMs.remove(it) }
        display = null; visiting = null; visitTanks = emptyList()
        tradeOffer = null; _trade.value = null
        storeRoom.leave()
        _visit.value = null
        customSim?.let { catchUp(it) }
    }

    /**
     * Pays for the shop bag ([bag]: one species id per animal). On success the keeper is home and the bags float at
     * the top of My tank; [onResult] gets the result on the sim thread.
     */
    fun checkout(bag: List<String>, onResult: (BuyResult) -> Unit = {}) {
        scope.launch {
            val store = visiting ?: return@launch
            val e = economyLive ?: return@launch
            val c = e.checkout(store, bag, today())
            if (c.result != BuyResult.OK) { publishVisit(); onResult(c.result); return@launch }
            leaveStoreNow()
            val mine = customSim
            if (mine != null) {
                val groups = bag.groupingBy { it }.eachCount().entries.toList()
                groups.forEachIndexed { k, (id, n) ->
                    // the bags in a row along the front glass, as if carried home and set down one by one
                    mine.enqueue(Command.FloatBag(id, n, 20.0 + (k + 0.5) * 80.0 / groups.size, size = StockSize.JUVENILE))
                }
                updateEconomy(mine, force = true)
            }
            saveCustom()
            _paid.tryEmit(c.total to bag.size)
            onResult(BuyResult.OK)
        }
    }

    /** The keeper opened the water test (Tank Health or the shelf kit): today's "Test the water" chore. */
    fun testedWater() {
        scope.launch { val sim = customSim ?: return@launch; economyLive?.testedWater(today()); updateEconomy(sim, force = true) }
    }

    /**
     * Tank Health's coral care button in My tank: pays [price] sand dollars for the supplies and puts them in.
     * False (nothing happens) when the keeper cannot afford it.
     */
    fun reefCare(price: Int): Boolean {
        if (price > (_economy.value?.coins ?: 0)) return false
        scope.launch {
            val sim = customSim ?: return@launch
            if (economyLive?.spend(price, "Coral care", today()) == false) return@launch
            sim.enqueue(Command.ReefCare)
            updateEconomy(sim, force = true)
        }
        return true
    }

    /**
     * The record shop (My tank): buys radio song [file] at its RadioStation price. False (nothing happens) when there
     * is no My tank, it is owned already or the keeper cannot afford it.
     */
    fun buyRecord(file: String): Boolean {
        val song = com.reeflive.core.radio.RadioStation.songs.firstOrNull { it.file == file } ?: return false
        val view = _economy.value ?: return false
        if (file in view.records || song.price > view.coins) return false
        scope.launch {
            val sim = customSim ?: return@launch
            if (economyLive?.buyRecord(file, today()) == true) updateEconomy(sim, force = true)
        }
        return true
    }

    /** Collects today's happy tank bonus. */
    fun collectHappyBonus() {
        scope.launch { val sim = customSim ?: return@launch; economyLive?.collectHappy(sim, today()); updateEconomy(sim, force = true) }
    }

    /** Deletes "My tank" (the screen asks first); the build picker shows again. */
    fun startOverCustomTank() {
        scope.launch {
            leaveStoreNow()
            customSim?.let { lastWallMs.remove(it) }
            customSim = null; customBase = null; customLoaded = true
            customStore?.clear()
            _economy.value = null
            away = null; _away.value = null
            _custom.value = CustomTankInfo(ready = true, exists = false)
            if (_mode.value == SimMode.CUSTOM) _snapshots.value = SimSnapshot.EMPTY
        }
    }

    /** Restores the sandbox to the state it was forked from. */
    fun resetSandbox() {
        scope.launch {
            val f = fork ?: return@launch
            scenarios.clear()
            sandbox?.enqueue(Command.ResetToReality(f.water.copy(), f.seeds))
        }
    }

    fun runScenario(s: Scenario) {
        scope.launch {
            val sim = active ?: return@launch
            if (_mode.value != SimMode.SANDBOX) return@launch
            scenarios.schedule(sim.simTimeSec, s.steps())
        }
    }

    /**
     * Forks the active sim (on the sim thread, once) and runs [spec] on a background dispatcher so the live sim
     * keeps ticking. Replaces a run already in flight.
     */
    fun startTimelapse(spec: TimelapseSpec) {
        timelapseCancel?.cancel()
        val token = CancelToken()
        timelapseCancel = token
        _timelapse.value = TimelapseState.Running(spec, com.reeflive.core.sim.timelapse.TimelapseProgress(0f, 0.0, spec.durationHours, 0))
        scope.launch {
            val fork = try {
                val src = active ?: error("The tank is not ready yet")
                TimelapseFork.of(src)
            } catch (e: Exception) {
                if (timelapseCancel === token) _timelapse.value = TimelapseState.Failed(e.message ?: "Could not start")
                return@launch
            }
            timelapseScope.launch {
                try {
                    val res = TimelapseRun(fork, spec).run(token) { p ->
                        if (timelapseCancel === token) _timelapse.value = TimelapseState.Running(spec, p)
                    }
                    if (timelapseCancel === token) _timelapse.value = TimelapseState.Done(spec, res.recording, res.report, res.cancelled)
                } catch (e: Exception) {
                    if (timelapseCancel === token) _timelapse.value = TimelapseState.Failed(e.message ?: "Time-lapse failed")
                }
            }
        }
    }

    /** Stops a running time-lapse; the partial recording is kept as a result. */
    fun cancelTimelapse() { timelapseCancel?.cancel() }

    /** Back to idle (closes the results). Cancels a run in flight and drops its result. */
    fun clearTimelapse() {
        timelapseCancel?.cancel(); timelapseCancel = null
        _timelapse.value = TimelapseState.Idle
    }

    /** The Live tab reached RESUMED. */
    fun onForeground() {
        scope.launch {
            foreground = true
            careAlerts?.cancel()
            startLoop()
        }
    }

    /** The Live tab left RESUMED. Stops ticking; the reality sim catches up on the next [onForeground]. */
    fun onBackground() {
        scope.launch {
            foreground = false
            saveCustom()
            planCareAlerts()
            tickJob?.cancel(); tickJob = null
            _running.value = false
        }
    }

    // ============================================================== sim thread
    private fun nowMs() = clock.now()

    private fun onInputs(v: Map<String, Double>, a: List<Livestock>, l: DbLightSchedule?) {
        latestValues = v; alive = a; schedule = l
        val key = a to l
        val sim = reality
        if (sim == null || key != structureKey) {
            structureKey = key
            buildReality(carryWater = sim?.water?.s, carryTime = sim?.simTimeSec)
            return
        }
        // a newly logged measurement: apply the changed values to the reality sim right away
        RealityInit.changedCommands(appliedValues, v).forEach { sim.enqueue(it) }
        appliedValues = v
        if (!_running.value) { sim.stepWall(0.0); publish(sim) }   // flush the commands so a paused tab shows them too
    }

    private fun ensureAssets() {
        if (species == null) {
            // every Add life species too: My tank takes any of them
            val stock = com.reeflive.core.sim.Stocking.CATALOG.map { it.speciesId }
            species = SpeciesCatalog.fromClasspath(alive.map { it.speciesId }.plus(com.reeflive.core.sim.DefaultStock.all.map { it.speciesId }).plus(stock).distinct())
        } else {
            val ids = alive.map { it.speciesId }.filterNot { species!!.has(it) }.distinct()
            if (ids.isNotEmpty()) SpeciesCatalog.fromClasspath(ids).all().forEach { species!!.put(it) }
        }
        if (rock == null) {
            rock = runCatching {
                val am = assets ?: error("no assets")
                RockData.fromFiles(am.open("sim/rock_sdf.bin").use { it.readBytes() }, am.open("sim/anchors.json").bufferedReader().use { it.readText() })
            }.getOrElse { RockData.defaultTank() }
        }
    }

    private fun localSecondsOfDay(): Double {
        val t = Instant.ofEpochMilli(nowMs()).atZone(ZoneId.systemDefault()).toLocalTime()
        return t.toSecondOfDay() + t.nano / 1e9
    }

    private fun buildReality(carryWater: WaterState?, carryTime: Double?) {
        ensureAssets()
        val water = carryWater?.copy() ?: Reality.waterFrom(latestValues)
        val sim = Simulation(
            config = TankConfig(),
            species = species!!,
            rock = rock!!,
            startTimeSec = carryTime ?: localSecondsOfDay(),
            initialWater = water,
            livestock = Reality.seedsFrom(alive),
            lights = Reality.lightsFrom(schedule),
        )
        sim.speed = 1.0
        // rocks where they came to rest: carried over a rebuild, else as saved at the last launch
        val prev = reality
        if (prev != null) sim.restoreRocks(prev.rockState())
        else sim.restoreRocks(rockStore?.load().orEmpty().also { savedRocks = it })
        sim.setDynamicRocks(rocksDynamic)
        appliedValues = latestValues
        reality?.let { lastWallMs.remove(it) }
        reality = sim
        lastWallMs[sim] = nowMs()
        if (carryWater != null) RealityInit.changedCommands(emptyMap(), latestValues).forEach { sim.enqueue(it) }
        sim.stepWall(0.0)
        publish(sim)
        if (foreground && tickJob == null) startLoop()
    }

    private fun switchMode(m: SimMode) {
        if (_mode.value == m && (m != SimMode.SANDBOX || sandbox != null)) return
        decorPreview = null
        if (m == SimMode.CUSTOM) { loadCustom(); showDecor(m) } else showDecor(m)
        val real = reality
        // My tank keeps its own speed; the other two start at real time
        if (_mode.value == SimMode.CUSTOM) { leaveStoreNow(); saveCustom(); _speed.value = 1.0 }
        if (m == SimMode.CUSTOM) {
            leaveSandbox()
            loadCustom()
            _mode.value = m
            val sim = customSim
            if (sim != null) { _speed.value = sim.speed; catchUp(sim) } else _snapshots.value = SimSnapshot.EMPTY
            return
        }
        if (m == SimMode.SANDBOX) {
            if (real == null) { _mode.value = m; return }
            val sb = forkSandbox(real)
            sandbox = sb
            _mode.value = m
            publish(sb)
        } else {
            _mode.value = m
            leaveSandbox()
            _speed.value = 1.0
            real?.let { catchUp(it); publish(it) }
        }
    }

    private fun leaveSandbox() {
        sandbox?.let { lastWallMs.remove(it) }
        sandbox = null; fork = null; scenarios.clear()
    }

    /** Reads "My tank" once (on first use); a damaged file falls back to the previous save. */
    private fun loadCustom() {
        if (customLoaded) return
        customLoaded = true
        ensureAssets()
        val store = customStore
        val save = store?.read()?.let { CustomTankCodec.decode(it) } ?: store?.readBackup()?.let { CustomTankCodec.decode(it) }
        if (save == null) { _custom.value = CustomTankInfo(ready = true, exists = false); return }
        val sim = runCatching { CustomTank.restore(save, species!!, rock!!) }.getOrNull()
        if (sim == null) { _custom.value = CustomTankInfo(ready = true, exists = false); return }
        customSim = sim
        _lifePace.value = com.reeflive.core.sim.LifePace.of(sim.lifePace)
        customBase = CustomTank.baseAfterRestore(save, species!!)
        economyLive = Economy(save.economy)
        decorLive = save.decor
        // the time away, at 1x: the tank lived on while the app was closed (all of it, up to 30 days)
        beginAway(sim, (nowMs() - save.savedAtMs).coerceAtLeast(0L) / 1000.0)
        sim.stepWall(0.0)
        lastWallMs[sim] = nowMs()
        _custom.value = CustomTankInfo(ready = true, exists = true, name = save.name, build = TankBuilds.byId(save.buildId))
        updateEconomy(sim, force = true)
    }

    /**
     * My tank lived on while the keeper was away: a short gap runs at once; a long one runs a chunk per tick
     * ([stepAway]) while the welcome-back card shows the days racing by, then says what happened.
     */
    private fun beginAway(sim: Simulation, seconds: Double) {
        if (seconds < FastForward.MIN_SIM_SEC) return
        val run = AwayCatchUp(sim, seconds)
        if (seconds < AwayCatchUp.REPORT_SEC) { withCoralNews(sim) { run.runAll() }; return }
        awayCorals = com.reeflive.core.sim.CoralHealth.marks(sim.corals)
        away = run
        _away.value = AwayUi(run.progress, run.doneSec / AwayCatchUp.DAY, run.totalSec / AwayCatchUp.DAY, null)
    }

    /** One chunk of a long away catch-up; true while it is still running (the tick loop skips the normal step). */
    private fun stepAway(sim: Simulation): Boolean {
        val run = away ?: return false
        run.step()
        if (run.finished) {
            away = null
            awayCorals?.let { before ->
                val lines = com.reeflive.core.sim.CoralHealth.changes(before, sim.corals)
                if (lines.isNotEmpty()) _coralNews.value = lines
            }
            awayCorals = null
            val s = run.summary()
            _away.value = if (s.worthShowing) AwayUi(1.0, run.doneSec / AwayCatchUp.DAY, run.totalSec / AwayCatchUp.DAY, s) else null
            lastWallMs[sim] = nowMs()
            return false
        }
        _away.value = AwayUi(run.progress, run.doneSec / AwayCatchUp.DAY, run.totalSec / AwayCatchUp.DAY, null)
        publish(sim)
        return true
    }

    /** The welcome-back card was closed. */
    fun dismissAway() { _away.value = _away.value?.takeIf { it.summary == null } }

    /** Today as the economy counts days: the keeper's local calendar day. */
    private fun today(): String = Instant.ofEpochMilli(nowMs()).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    /** Ticks off chores done since the last look (about once a second) and publishes the money; sim thread. */
    private fun updateEconomy(sim: Simulation, force: Boolean = false) {
        val eco = economyLive ?: return
        val now = nowMs()
        if (!force && now - lastEconomyMs < ECONOMY_MS) return
        lastEconomyMs = now
        val changed = eco.update(sim, today())
        for (g in eco.takeGains()) _gains.tryEmit(g)
        if (changed || force || _economy.value == null) { _economy.value = eco.view(sim); showDecor() }
        else _economy.value?.let { v -> Economy.happyBonus(sim).let { h -> if (h != v.happyBonus) _economy.value = v.copy(happyBonus = h) } }
    }

    /** The app is leaving the screen: tell the sink when My tank will next need the keeper. */
    private fun planCareAlerts() {
        val sink = careAlerts ?: return
        val sim = customSim ?: return sink.cancel()
        val now = nowMs()
        val left = economyLive?.view(sim)?.tasks?.count { !it.done } ?: 0
        val offsetMin = java.util.TimeZone.getDefault().getOffset(now) / 60_000
        sink.schedule(com.reeflive.core.custom.CareAlerts.plan(sim, now, offsetMin, economyLive != null, left))
    }

    private fun saveCustom() {
        val sim = customSim ?: return
        val base = customBase ?: return
        val now = nowMs()
        val save = CustomTank.capture(sim, (economyLive?.let { base.copy(economy = it.state) } ?: base).copy(decor = decorLive), now)
        customBase = save
        lastCustomSaveMs = now
        customStore?.write(CustomTankCodec.encode(save))
    }

    private fun forkSandbox(real: Simulation): Simulation {
        val seeds = real.organisms.filter { it.alive }.map {
            LivestockSeed(it.species.id, it.nickname, it.lengthCm, it.role, it.placement, it.id, it.variant)
        } + real.corals.map { LivestockSeed(it.species.id, it.nickname, 0.0, null, it.anchor.ifBlank { null }, it.id) }
        val water = real.water.s.copy()
        fork = Fork(water.copy(), seeds)
        val sb = Simulation(
            config = real.config.copy(predationEnabled = true),
            species = species!!,
            rock = rock!!,
            seed = real.config.seed + 17,
            startTimeSec = real.simTimeSec,
            initialWater = water,
            livestock = seeds,
            lights = com.reeflive.core.sim.LightSchedule(real.lights.points),
        )
        sb.speed = _speed.value
        sb.copyRocksFrom(real)
        lastWallMs[sb] = nowMs()
        return sb
    }

    /** Skip [sim] forward by the wall time since it last ran (reality and My tank; capped at 24 h of sim). */
    private fun catchUp(sim: Simulation) {
        val now = nowMs()
        val last = lastWallMs[sim] ?: now
        lastWallMs[sim] = now
        if (sim === customSim) { beginAway(sim, (now - last).coerceAtLeast(0L) / 1000.0); publish(sim); return }
        val secs = FastForward.simSeconds(now - last, speed = 1.0)
        if (secs >= FastForward.MIN_SIM_SEC) withCoralNews(sim) { sim.advanceAbstract(secs) }
        else sim.stepWall(0.0)
        publish(sim)
    }

    private fun startLoop() {
        if (tickJob != null) return
        active?.let { if (it === reality || it === customSim) catchUp(it) }
        if (_mode.value == SimMode.CUSTOM && !customLoaded) { loadCustom(); customSim?.let { catchUp(it) } }
        active?.let { publish(it) }
        _running.value = true
        tickJob = scope.launch {
            var last = System.nanoTime()
            while (isActive) {
                val sim = active
                val t0 = System.nanoTime()
                val dt = ((t0 - last) / 1e9).coerceIn(0.0, 0.1)
                last = t0
                if (sim != null && sim === customSim && stepAway(sim)) {
                    // catching up on the time away: the card shows the days going by
                } else if (sim != null) {
                    scenarios.due(sim.simTimeSec).forEach { sim.enqueue(it) }
                    sim.stepWall(dt)
                    lastWallMs[sim] = nowMs()
                    publish(sim)
                    if (sim === customSim) {
                        updateEconomy(sim)
                        if (nowMs() - lastCustomSaveMs >= CustomTank.AUTOSAVE_MS) saveCustom()
                    }
                }
                val spent = (System.nanoTime() - t0) / 1_000_000
                delay((TICK_MS - spent).coerceAtLeast(1))
            }
        }
    }

    private fun publish(sim: Simulation) {
        if (sim === active) _snapshots.value = sim.latest
        if (sim === reality) { publishPeek(sim); saveRocks(sim) }
    }

    /** A rock in the tank settled or toppled: keep its new spot for the next launch. */
    private fun saveRocks(sim: Simulation) {
        if (rockStore == null || sim.latest.events.none { it is com.reeflive.core.sim.SimEvent.RockShifted || it is com.reeflive.core.sim.SimEvent.RockscapeChanged }) return
        val s = sim.rockState()
        if (s != savedRocks) { savedRocks = s; rockStore.save(s) }
    }

    private fun publishPeek(sim: Simulation) {
        if (sim !== reality) return
        val snap = sim.latest
        LivePeek.lastDescribed(snap.events)?.let { lastEvent = it }
        _peek.value = LivePeek.from(snap, lastEvent)
    }

    companion object {
        const val TICK_MS = 50L   // 20 Hz (SIMULATION §1.2)
        /** How often My tank's chores are checked off (wall ms). */
        const val ECONOMY_MS = 1000L
    }
}

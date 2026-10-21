@file:OptIn(ExperimentalJsExport::class)

package com.reeflive.web

import com.reeflive.core.custom.AwayCatchUp
import com.reeflive.core.custom.AwaySummary
import com.reeflive.core.custom.CustomTank
import com.reeflive.core.custom.CustomTankCodec
import com.reeflive.core.custom.CustomTankSave
import com.reeflive.core.custom.TankBuilds
import com.reeflive.core.custom.economy.BuyResult
import com.reeflive.core.custom.economy.DisplayTank
import com.reeflive.core.custom.economy.Economy
import com.reeflive.core.custom.economy.FishStore
import com.reeflive.core.custom.economy.StoreVisit
import com.reeflive.core.sim.StockSize
import com.reeflive.core.live.FastForward
import com.reeflive.core.sim.Command
import com.reeflive.core.sim.LifePace
import com.reeflive.core.sim.Simulation
import kotlinx.browser.localStorage
import kotlin.math.roundToInt

/**
 * "My tank" in the browser, as the Android SimulationHolder does it: a tank started empty from a build, saved in this
 * browser's storage (core CustomTankCodec, versioned so a site update never loses it) every 30 s by the page, when the
 * page is hidden and when the mode changes, and skipped ahead by the time away when it opens again (1×, capped at a
 * day). The Android app and each browser keep their own tank.
 */
@JsExport
object ReefMyTank {
    private const val KEY = "reeflive.mytank"
    private const val BACKUP = "reeflive.mytank.bak"

    internal var sim: Simulation? = null
    private var base: CustomTankSave? = null
    private var loaded = false
    /** Wall ms when the tank last stopped running (left the mode or the page was hidden). */
    private var pausedAt = -1.0
    /** Sand dollars, the care list and goals (docs/ECONOMY.md); kept across "Start over" like the Android app. */
    private var economy: Economy? = null

    private fun now(): Double = js("Date.now()") as Double
    private fun read(key: String): String? = runCatching { localStorage.getItem(key) }.getOrNull()?.takeIf { it.isNotBlank() }
    private fun write(key: String, v: String) { runCatching { localStorage.setItem(key, v) } }

    /** `{exists, name, buildId, build, gallons}` for the page. */
    fun info(): String {
        load()
        val b = base
        return JSON.stringify(obj { o ->
            o.exists = sim != null && b != null
            if (b != null) {
                val build = TankBuilds.byId(b.buildId)
                o.name = b.name; o.buildId = build.id; o.build = build.label; o.gallons = build.gallons.roundToInt()
            }
        })
    }

    /** The tanks to pick from: `[{id, label, blurb, gallons, size}]`. */
    fun builds(): String = JSON.stringify(TankBuilds.all.map { b ->
        obj { it.id = b.id; it.label = b.label; it.blurb = b.blurb; it.gallons = b.gallons.roundToInt(); it.size = b.sizeLabel }
    }.toTypedArray())

    /** Sets up a new, empty tank in build [buildId] and shows it when the page is in My tank. */
    fun start(buildId: String): Boolean {
        val species = ReefLive.species ?: return false
        val rock = ReefLive.rock ?: return false
        val build = TankBuilds.byId(buildId)
        val t = now()
        val secOfDay = js("(function(){var d=new Date();return d.getHours()*3600+d.getMinutes()*60+d.getSeconds();})()") as Int
        val seed = t.toLong() xor 0x5EEDL
        val s = CustomTank.fresh(build, species, rock, secOfDay.toDouble(), seed)
        s.speed = 1.0
        s.lifePace = LifePace.DEFAULT.pace
        s.stepWall(0.0)
        sim = s
        val eco = economy ?: Economy()
        economy = eco
        base = CustomTankSave(name = "My tank", buildId = build.id, createdAtMs = t.toLong(), seed = seed, economy = eco.state)
        loaded = true
        save()
        if (ReefSandbox.isMine()) ReefLive.sim = s
        return true
    }

    /** Deletes the tank (the page asks first); the last save stays as a backup until the next tank is saved. */
    fun startOver() {
        read(KEY)?.let { write(BACKUP, it) }
        runCatching { localStorage.removeItem(KEY) }
        sim = null; base = null; loaded = true; awayRun = null; awaySummary = null
        if (ReefSandbox.isMine()) ReefLive.sim = null
    }

    /** Names an animal; it is kept in the next save. */
    /** Growing speed choices for slow biology (LifePace): `[{id, label, blurb, on}]`. */
    fun paces(): String {
        val cur = sim?.let { LifePace.of(it.lifePace) }
        return JSON.stringify(LifePace.entries.map { p -> obj { it.id = p.name; it.label = p.label; it.blurb = p.blurb; it.on = p == cur } }.toTypedArray())
    }

    fun setPace(id: String) {
        val p = LifePace.entries.firstOrNull { it.name == id } ?: return
        sim?.lifePace = p.pace
    }

    fun rename(id: String, name: String) { sim?.enqueue(Command.Rename(id, name)) }

    /** Today as the economy counts days: the keeper's local calendar day. */
    private fun today(): String = js("(function(){var d=new Date();return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0');})()") as String

    /**
     * Ticks off the chores done since the last call and returns the Care card's data (the page calls it about once a
     * second in My tank): `{coins, level, levelStars, levelNeed, tasks:[{id,label,hint,pay,done}], allDoneBonus,
     * allDonePaid, streak, multiplier, happyBonus, happyPaid, attention, goals:[{id,label,reward,done}],
     * gains:[{amount,label,levelUp,opens}]}`, or "null" with no tank.
     */
    fun economy(): String {
        val s = sim ?: return "null"
        val e = economy ?: return "null"
        e.update(s, today())
        val v = e.view(s)
        val gains = e.takeGains()
        return JSON.stringify(obj { o ->
            o.coins = v.coins; o.level = v.level; o.levelStars = v.levelStars; o.levelNeed = v.levelNeed
            o.tasks = v.tasks.map { t -> obj { it.id = t.task.id; it.label = t.task.label; it.hint = t.task.hint; it.pay = t.pay; it.done = t.done } }.toTypedArray()
            o.allDoneBonus = v.allDoneBonus; o.allDonePaid = v.allDonePaid; o.streak = v.streak; o.multiplier = v.multiplier
            o.happyBonus = v.happyBonus; o.happyPaid = v.happyPaid; o.attention = v.attention
            o.goals = v.goals.map { g -> obj { it.id = g.goal.id; it.label = g.goal.label; it.reward = g.goal.reward; it.done = g.done } }.toTypedArray()
            o.gains = gains.map { g ->
                obj { it.amount = g.amount; it.label = g.label; it.levelUp = g.levelUp; it.opens = com.reeflive.core.custom.economy.Levels.opens(g.levelUp) }
            }.toTypedArray()
        })
    }

    /** The keeper opened Tank Health (the water test): today's "Test the water" chore. */
    fun testedWater() { if (sim != null) economy?.testedWater(today()) }

    /** Pays [price] sand dollars for Tank Health's coral care supplies; false when the keeper cannot afford them. */
    fun spendCare(price: Int): Boolean = sim != null && economy?.spend(price, "Coral care", today()) != false

    /** Collects today's happy tank bonus; returns what it paid. */
    fun collectHappy(): Int { val s = sim ?: return 0; return economy?.collectHappy(s, today()) ?: 0 }

    // ------------------------------------------------------------------ fish stores (docs/ECONOMY.md §5)

    private var visiting: FishStore? = null
    private var tanks: List<DisplayTank> = emptyList()
    private var tankAt = -1
    private var display: Simulation? = null

    /** The street: `{coins, level, stores:[{id, label, greeting, level, open}]}`. */
    fun stores(): String {
        val e = economy ?: return "null"
        return JSON.stringify(obj { o ->
            o.coins = e.state.coins; o.level = e.level
            o.stores = FishStore.entries.map { f ->
                obj { it.id = f.id; it.label = f.label; it.greeting = f.greeting; it.level = f.level; it.open = e.canSee(f) }
            }.toTypedArray()
        })
    }

    /**
     * Walks into [storeId]: My tank pauses (it catches up on the way out) and the 3D view shows the first display tank.
     * Returns the store's tanks `{store, label, greeting, tanks:[{title, animals, items:[...]}]}` ([visitItems]), or "null".
     */
    fun visit(storeId: String): String {
        val store = FishStore.byId(storeId) ?: return "null"
        val e = economy ?: return "null"
        val mine = sim ?: return "null"
        if (!e.canSee(store)) return "null"
        if (visiting == null) { save(); pausedAt = now() }
        visiting = store
        tanks = StoreVisit.tanks(store, today())
        tankAt = -1
        showTank(0)
        return visitJson(mine)
    }

    /** The store's tanks with each animal's card: `{id, name, group, price, left, count, deal, wanted, level, locked, warning}`. */
    fun visitItems(): String = sim?.let { visitJson(it) } ?: "null"

    private fun visitJson(mine: Simulation): String {
        val store = visiting ?: return "null"
        val e = economy ?: return "null"
        val day = today()
        return JSON.stringify(obj { o ->
            o.store = store.id; o.label = store.label; o.greeting = store.greeting; o.coins = e.state.coins; o.at = tankAt
            o.tanks = tanks.map { t ->
                obj { tk ->
                    tk.title = t.title; tk.animals = t.animals
                    tk.items = StoreVisit.cards(e, store, t, mine, day).map { c ->
                        obj {
                            it.id = c.speciesId; it.name = c.name; it.group = c.group.name; it.price = c.price; it.left = c.left
                            it.deal = c.deal; it.wanted = c.wanted; it.level = c.level; it.locked = c.locked; it.warning = c.warning
                        }
                    }.toTypedArray()
                }
            }.toTypedArray()
        })
    }

    /** Shows display tank [i] in the 3D view (one runs at a time). */
    fun showTank(i: Int): Boolean {
        val t = tanks.getOrNull(i) ?: return false
        if (i == tankAt && display != null) return true
        val species = ReefLive.species ?: return false
        val rock = ReefLive.rock ?: return false
        val d = StoreVisit.build(t, species, rock, (today().hashCode() * 31 + i).toLong())
        display = d; tankAt = i
        if (ReefSandbox.isMine()) ReefLive.sim = d
        return true
    }

    /** The species id of an animal tapped in the display tank (its card), or "". */
    fun displaySpecies(id: String): String =
        display?.let { d -> d.organisms.firstOrNull { it.id == id }?.species?.id ?: d.corals.firstOrNull { it.id == id }?.species?.id } ?: ""

    /**
     * Pays for the shop bag ([bag]: a JSON array of species ids, one per animal). On "OK" the keeper is home and the
     * bags float in My tank. Returns `{result, total, coins}`.
     */
    fun checkout(bag: String): String {
        val store = visiting ?: return "null"
        val e = economy ?: return "null"
        val ids = (JSON.parse<Array<String>>(bag)).toList()
        val c = e.checkout(store, ids, today())
        if (c.result == BuyResult.OK && ids.isNotEmpty()) {
            leaveStore()
            val mine = sim
            if (mine != null) {
                val groups = ids.groupingBy { it }.eachCount().entries.toList()
                groups.forEachIndexed { k, (id, n) ->
                    // the bags in a row along the front glass, as if carried home and set down one by one
                    val x = 20.0 + (k + 0.5) * 80.0 / groups.size
                    mine.enqueue(Command.FloatBag(id, n, x, size = StockSize.JUVENILE))
                }
            }
            save()
        }
        return JSON.stringify(obj { it.result = c.result.name; it.total = c.total; it.coins = e.state.coins })
    }

    /** Walks out (the bag empties back onto the shelves): My tank catches up on the minutes spent shopping. */
    fun leaveStore() {
        if (visiting == null) return
        visiting = null; tanks = emptyList(); tankAt = -1; display = null
        if (ReefSandbox.isMine()) { ReefLive.sim = sim; catchUp() }
    }

    fun inStore(): Boolean = visiting != null

    /** Writes the tank to this browser's storage (the page calls it every 30 s and when it is hidden). */
    fun save() {
        val s = sim ?: return
        val b = base ?: return
        val next = CustomTank.capture(s, economy?.let { b.copy(economy = it.state) } ?: b, now().toLong())
        base = next
        val text = CustomTankCodec.encode(next)
        read(KEY)?.let { write(BACKUP, it) }
        write(KEY, text)
    }

    /** The page was hidden: save and note the time (the tank does not run while hidden). */
    fun hidden() { if (sim != null && ReefSandbox.isMine() && visiting == null) { save(); pausedAt = now() } }

    /** The page is back: the tank lived on meanwhile. */
    fun visible() { if (ReefSandbox.isMine() && visiting == null) catchUp() }

    internal fun enter(): Simulation? {
        load()
        catchUp()
        return sim
    }

    internal fun leave() {
        // walking out of My tank from a store: the pause already started at the store's door
        if (visiting != null) { visiting = null; tanks = emptyList(); tankAt = -1; display = null; return }
        save(); pausedAt = now()
    }

    private fun catchUp() {
        val s = sim ?: return
        if (pausedAt < 0) return
        val secs = (now() - pausedAt).coerceAtLeast(0.0) / 1000.0
        pausedAt = -1.0
        if (secs < FastForward.MIN_SIM_SEC) return
        // the whole time away, up to 30 days: a short gap at once, a long one a chunk per call of [away]
        val run = AwayCatchUp(s, secs)
        if (secs < AwayCatchUp.REPORT_SEC) { run.runAll(); s.stepWall(0.0) } else { awayRun = run; awaySummary = null }
    }

    private var awayRun: AwayCatchUp? = null
    private var awaySummary: AwaySummary? = null

    /**
     * "While you were away" for the page (docs/ECONOMY.md §9), called every frame while it shows: runs one chunk of a
     * long catch-up and returns `{running, progress, daysDone, days, headline, lines, deaths:[{name,cause}]}`, or
     * "null" when there is nothing to show.
     */
    fun away(): String {
        val run = awayRun
        if (run != null) {
            run.step()
            if (run.finished) { awayRun = null; awaySummary = run.summary().takeIf { it.worthShowing } }
            if (!run.finished) return JSON.stringify(obj { o ->
                o.running = true; o.progress = run.progress; o.daysDone = run.doneSec / AwayCatchUp.DAY; o.days = run.totalSec / AwayCatchUp.DAY
            })
        }
        val sm = awaySummary ?: return "null"
        return JSON.stringify(obj { o ->
            o.running = false; o.progress = 1.0; o.daysDone = sm.awaySec / AwayCatchUp.DAY; o.days = sm.awaySec / AwayCatchUp.DAY
            o.headline = sm.headline; o.lines = sm.lines.toTypedArray()
            o.deaths = sm.deaths.map { (n, c) -> obj { it.name = n; it.cause = c } }.toTypedArray()
        })
    }

    /** The welcome-back card was closed. */
    fun dismissAway() { awaySummary = null }

    private fun load() {
        if (loaded) return
        val species = ReefLive.species ?: return
        val rock = ReefLive.rock ?: return
        loaded = true
        // a damaged save falls back to the one before it; no save at all (never set up, or started over) is no tank
        val text = read(KEY) ?: return
        val save = CustomTankCodec.decode(text) ?: read(BACKUP)?.let { CustomTankCodec.decode(it) } ?: return
        val s = runCatching { CustomTank.restore(save, species, rock) }.getOrNull() ?: return
        sim = s
        base = CustomTank.baseAfterRestore(save, species)
        economy = Economy(save.economy)
        // closed since the last save: the time away runs on the next [catchUp]
        pausedAt = save.savedAtMs.toDouble()
        s.stepWall(0.0)
    }
}

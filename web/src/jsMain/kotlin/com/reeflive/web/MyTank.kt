@file:OptIn(ExperimentalJsExport::class)

package com.reeflive.web

import com.reeflive.core.custom.AwayCatchUp
import com.reeflive.core.custom.AwaySummary
import com.reeflive.core.custom.CustomTank
import com.reeflive.core.custom.CustomTankCodec
import com.reeflive.core.custom.CustomTankSave
import com.reeflive.core.sim.Stocking
import com.reeflive.core.custom.TankBuilds
import com.reeflive.core.custom.economy.BuyResult
import com.reeflive.core.custom.economy.DisplayTank
import com.reeflive.core.custom.economy.Economy
import com.reeflive.core.custom.economy.FishStore
import com.reeflive.core.custom.economy.Offers
import com.reeflive.core.custom.economy.StoreTrades
import com.reeflive.core.custom.economy.StoreVisit
import com.reeflive.core.custom.economy.TradeOffer
import com.reeflive.core.custom.room.Decor
import com.reeflive.core.custom.room.DecorResult
import com.reeflive.core.custom.room.SavedDecor
import com.reeflive.app.render.RoomLook
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
    /** The stand and room bought for My tank (core room/Decor.kt); kept across "Start over" like the money. */
    private var decor: SavedDecor = SavedDecor()
    /** The shop card is trying something on (shown, not bought). */
    private var previewKey: String? = null

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

    /** The tanks to pick from: `[{id, label, blurb, gallons, size, l, h, d (cm), rimless, filter}]` (the picker draws each to scale). */
    fun builds(): String = JSON.stringify(TankBuilds.all.map { b ->
        obj {
            it.id = b.id; it.label = b.label; it.blurb = b.blurb; it.gallons = b.gallons.roundToInt(); it.size = b.sizeLabel
            it.l = b.shape.lengthCm; it.h = b.shape.heightCm; it.d = b.shape.depthCm; it.rimless = b.rimless; it.filter = b.filter.name
        }
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
        base = CustomTankSave(name = "My tank", buildId = build.id, createdAtMs = t.toLong(), seed = seed, economy = eco.state, decor = decor)
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
     * journal:{moments:[{id,title,hint,fact,reward,found}], kept:[name]}, gains:[{amount,label,levelUp,opens,story?:{title,
     * headline,fact}}]}`, or "null" with no tank.
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
            o.journal = obj { j ->
                j.moments = v.journal.moments.map { m ->
                    obj { it.id = m.moment.id; it.title = m.moment.title; it.hint = m.moment.hint; it.fact = m.moment.fact; it.reward = m.moment.reward; it.found = m.found }
                }.toTypedArray()
                j.kept = v.journal.kept.toTypedArray()
            }
            o.gains = gains.map { g ->
                obj {
                    it.amount = g.amount; it.label = g.label; it.levelUp = g.levelUp; it.opens = com.reeflive.core.custom.economy.Levels.opens(g.levelUp)
                    g.story?.let { st -> it.story = obj { o2 -> o2.title = st.title; o2.headline = st.headline; o2.fact = st.fact } }
                }
            }.toTypedArray()
        })
    }

    /** The keeper opened Tank Health (the water test): today's "Test the water" chore. */
    fun testedWater() { if (sim != null) economy?.testedWater(today()) }

    /** Pays [price] sand dollars for Tank Health's coral care supplies; false when the keeper cannot afford them. */
    fun spendCare(price: Int): Boolean = sim != null && economy?.spend(price, "Coral care", today()) != false

    /** The record shop: buys radio song [file] at its RadioStation price; false when it cannot (owned, too dear, no tank). */
    fun buyRecord(file: String): Boolean = sim != null && economy?.buyRecord(file, today()) == true

    /** Radio songs bought in this tank, as a JSON array of files (`[]` without a tank). */
    fun records(): String = JSON.stringify(economy?.state?.records.orEmpty().toTypedArray())

    /** Collects today's happy tank bonus; returns what it paid. */
    fun collectHappy(): Int { val s = sim ?: return 0; return economy?.collectHappy(s, today()) ?: 0 }

    // ------------------------------------------------------------------ the room: stands (core room/Decor.kt)

    /** Puts My tank's stand and room (or the one being tried on) round the tank. */
    private fun showRoom() {
        val k = previewKey
        ReefLive.homeRoom.look = RoomLook.of(if (k != null) Decor.preview(decor, k) else decor)
    }

    /**
     * The room shop card: `{coins, level, recolor, tiles:[{key, kind, label, price, level, owned, inUse, locked, paint, trim}]}`
     * (kind: stand, color, extra; paint/trim are #rrggbb swatches).
     */
    fun decorShop(): String {
        val e = economy ?: return "null"
        return JSON.stringify(obj { o ->
            o.coins = e.state.coins; o.level = e.level; o.recolor = Decor.RECOLOR; o.preview = previewKey
            o.tiles = Decor.tiles(decor, e.level).map { t ->
                obj {
                    it.key = t.key; it.kind = t.key.substringBefore(':'); it.label = t.label; it.price = t.price; it.level = t.level
                    it.owned = t.owned; it.inUse = t.inUse; it.locked = t.locked
                    it.paint = t.swatch?.let { c -> hex(c) }; it.trim = t.swatch2?.let { c -> hex(c) }
                }
            }.toTypedArray()
        })
    }

    private fun hex(c: Int) = "#" + c.toString(16).padStart(6, '0')

    /** Buys [key] (it goes straight into use). Returns `{result, coins}`; result OK, OWNED, LEVEL_LOCKED, TOO_POOR, UNKNOWN. */
    fun decorBuy(key: String): String {
        val e = economy ?: return "null"
        val (d, r) = Decor.buy(decor, key, e, today())
        if (r == DecorResult.OK) { decor = d; previewKey = null; showRoom(); save() }
        return JSON.stringify(obj { it.result = r.name; it.coins = e.state.coins })
    }

    /** Puts an owned stand, colour or extra to use (an extra toggles). */
    fun decorUse(key: String) {
        val d = Decor.use(decor, key)
        previewKey = null
        if (d != decor) { decor = d; save() }
        showRoom()
    }

    /** Tries [key] on in the 3D view without buying it ("" ends the preview). */
    fun decorPreview(key: String) {
        previewKey = key.ifBlank { null }
        showRoom()
    }

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
        swap = StoreTrades.offer(store, mine, e, today())
        // walk in from the street, unless the page asks for less motion
        ReefLive.room.walkIn = !kotlinx.browser.window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ReefLive.room.enter(store.id, tanks.size, 0, tanks.map { it.setup }, swap?.line)
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
                            it.id = c.speciesId; it.species = Stocking.speciesOf(c.speciesId); it.name = c.name; it.group = c.group.name; it.price = c.price; it.left = c.left
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
        ReefLive.room.show(i)
        return true
    }

    /** Walks to the checkout counter in 3D ([on]) or back to the tank. */
    fun storeCounter(on: Boolean) { if (visiting != null) ReefLive.room.counter(on) }

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

    // ---- the store's people (render StorePeople): their speech bubbles and a regular's swap
    private var swap: TradeOffer? = null
    private val bText = arrayOfNulls<String>(6)
    private val bPos = FloatArray(18)
    private val bAlpha = FloatArray(6)
    private val bPt = FloatArray(3)

    /**
     * What the store's people are saying now, as JSON [{text, x, y, a}] with x, y in canvas pixels just over the
     * talker's head (as Android's StoreBubbleLayer). The page calls it every animation frame in a store.
     */
    fun storeBubbles(): String {
        if (visiting == null) return "[]"
        val r = ReefLive.renderer ?: return "[]"
        val proj = r.projection ?: com.reeflive.app.render.TankProjection().also { r.projection = it }
        val n = ReefLive.room.bubbles(bText, bPos, bAlpha)
        val out = ArrayList<dynamic>()
        for (i in 0 until n) {
            val t = bText[i] ?: continue
            if (!proj.project(bPos[i * 3], bPos[i * 3 + 1], bPos[i * 3 + 2], bPt)) continue
            out.add(obj { o -> o.text = t; o.x = bPt[0]; o.y = bPt[1]; o.a = bAlpha[i] })
        }
        return JSON.stringify(out.toTypedArray())
    }

    /** The swap the store's regular just asked about: {trader, line, give, frag, get} or "null". */
    fun trade(): String {
        val o = swap ?: return "null"
        return JSON.stringify(obj { it.trader = o.trader; it.line = o.line; it.give = o.giveName; it.frag = o.giveFrag; it.get = o.getName })
    }

    /** The keeper's answer: yes cuts the frag (or nets the spare) and bags the new animal for My tank. */
    fun answerTrade(yes: Boolean) {
        val o = swap ?: return
        swap = null
        ReefLive.room.tradeReply(yes)
        if (!yes) return
        val mine = sim ?: return
        val e = economy ?: return
        StoreTrades.apply(mine, o)
        e.traded(o, today())
        mine.stepWall(0.0)
        save()
    }

    /** Walks out (the bag empties back onto the shelves): My tank catches up on the minutes spent shopping. */
    fun leaveStore() {
        if (visiting == null) return
        visiting = null; tanks = emptyList(); tankAt = -1; display = null; swap = null
        ReefLive.room.leave()
        if (ReefSandbox.isMine()) { ReefLive.sim = sim; catchUp() }
    }

    fun inStore(): Boolean = visiting != null

    /**
     * The store's offer for each of the keeper's animals and corals (docs/ECONOMY.md §6):
     * `[{id, name, speciesId, group, coral, pet, amount, wanted, cm, tooSmallCm}]`, wanted first.
     */
    fun offers(): String {
        val store = visiting ?: return "[]"
        val mine = sim ?: return "[]"
        return JSON.stringify(Offers.offers(store, mine, today()).map { o ->
            obj {
                it.id = o.id; it.name = o.name; it.speciesId = o.speciesId; it.group = o.group.name; it.coral = o.coral; it.pet = o.pet
                it.amount = o.amount; it.wanted = o.wanted; it.cm = o.lengthCm; it.tooSmallCm = o.tooSmallCm
            }
        }.toTypedArray())
    }

    /** Sells animal or coral [id] to the store being visited; returns what it paid (0 = not sold). */
    fun sell(id: String): Int {
        val store = visiting ?: return 0
        val mine = sim ?: return 0
        val e = economy ?: return 0
        val o = Offers.offers(store, mine, today()).firstOrNull { it.id == id } ?: return 0
        if (!e.sell(store, o, today())) return 0
        mine.enqueue(if (o.coral) Command.RemoveCoral(id) else Command.RemoveOrganism(id))
        // out of the tank now, though the tank itself waits until the keeper is home
        mine.stepWall(0.0)
        save()
        return o.amount
    }

    /** Writes the tank to this browser's storage (the page calls it every 30 s and when it is hidden). */
    fun save() {
        val s = sim ?: return
        val b = base ?: return
        val next = CustomTank.capture(s, (economy?.let { b.copy(economy = it.state) } ?: b).copy(decor = decor), now().toLong())
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
        showRoom()
        catchUp()
        return sim
    }

    internal fun leave() {
        previewKey = null
        ReefLive.homeRoom.look = RoomLook.DEFAULT
        // walking out of My tank from a store: the pause already started at the store's door
        if (visiting != null) { visiting = null; tanks = emptyList(); tankAt = -1; display = null; ReefLive.room.leave(); return }
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
        decor = save.decor
        // closed since the last save: the time away runs on the next [catchUp]
        pausedAt = save.savedAtMs.toDouble()
        s.stepWall(0.0)
    }
}

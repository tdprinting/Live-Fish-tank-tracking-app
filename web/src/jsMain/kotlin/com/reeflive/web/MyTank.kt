@file:OptIn(ExperimentalJsExport::class)

package com.reeflive.web

import com.reeflive.core.custom.CustomTank
import com.reeflive.core.custom.CustomTankCodec
import com.reeflive.core.custom.CustomTankSave
import com.reeflive.core.custom.TankBuilds
import com.reeflive.core.custom.economy.Economy
import com.reeflive.core.live.FastForward
import com.reeflive.core.sim.Command
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
        sim = null; base = null; loaded = true
        if (ReefSandbox.isMine()) ReefLive.sim = null
    }

    /** Names an animal; it is kept in the next save. */
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
    fun hidden() { if (sim != null && ReefSandbox.isMine()) { save(); pausedAt = now() } }

    /** The page is back: the tank lived on meanwhile. */
    fun visible() { if (ReefSandbox.isMine()) catchUp() }

    internal fun enter(): Simulation? {
        load()
        catchUp()
        return sim
    }

    internal fun leave() { save(); pausedAt = now() }

    /** What happened to the corals while the page was away ("" = nothing), read once by the page ([ReefSandbox.coralNews]). */
    var coralNews = ""

    private fun catchUp() {
        val s = sim ?: return
        if (pausedAt < 0) return
        val secs = FastForward.simSeconds((now() - pausedAt).toLong(), 1.0)
        pausedAt = -1.0
        if (secs >= FastForward.MIN_SIM_SEC) {
            val before = com.reeflive.core.sim.CoralHealth.marks(s.corals)
            s.advanceAbstract(secs); s.stepWall(0.0)
            com.reeflive.core.sim.CoralHealth.changes(before, s.corals).takeIf { it.isNotEmpty() }?.let { coralNews = it.joinToString(" ") }
        }
    }

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
        // closed since the last save: skip ahead by the time away
        pausedAt = save.savedAtMs.toDouble()
        s.stepWall(0.0)
    }
}

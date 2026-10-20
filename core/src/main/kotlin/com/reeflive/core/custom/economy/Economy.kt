package com.reeflive.core.custom.economy

import com.reeflive.core.sim.CareLog
import com.reeflive.core.sim.MaintenanceTask
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.TankDoctor
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * My tank's money (docs/ECONOMY.md): sand dollars and star points, the daily care list, the streak, the happy tank
 * bonus and the one-time goals. Saved in [com.reeflive.core.custom.CustomTankSave.economy]; every field has a
 * default, so a save from before the economy opens with [Economy.START_COINS] and level 1.
 *
 * Days are the keeper's local calendar days as `yyyy-mm-dd` ([CalendarDay]); the hosts pass today's key, so nothing
 * here reads a clock and speeding the sim up never pays faster.
 */
@Serializable
data class SavedEconomy(
    val coins: Int = Economy.START_COINS,
    /** Star points: every sand dollar earned from care and goals (not from sales); [Levels] reads them. */
    val stars: Int = 0,
    /** The day [tasks] were picked for, and which of them are done. */
    val day: String = "",
    val tasks: List<String> = emptyList(),
    val done: List<String> = emptyList(),
    val allDonePaid: Boolean = false,
    val happyPaid: Boolean = false,
    /** Days in a row the whole list was finished, ending on [lastFullDay]. */
    val streak: Int = 0,
    val lastFullDay: String = "",
    val lastWaterChangeDay: String = "",
    val goals: List<String> = emptyList(),
    /** What was bought at each store on [boughtDay], as "store/species" → count (sold out when it reaches the stock). */
    val boughtDay: String = "",
    val bought: Map<String, Int> = emptyMap(),
    /** Day Shelly's last gave the free starter snails to an empty, broke tank. */
    val giftDay: String = "",
    /** Newest first, at most [Economy.LEDGER_MAX]: the "where did my money go" list. */
    val ledger: List<LedgerEntry> = emptyList(),
)

@Serializable
data class LedgerEntry(val day: String, val label: String, val amount: Int)

/** The daily chores (docs/ECONOMY.md §4). [pay] is before the streak bonus. */
enum class CareTask(val id: String, val label: String, val pay: Int, val hint: String) {
    FEED_FISH("feed_fish", "Feed your fish", 10, "Any food from the shelf counts."),
    FEED_CORALS("feed_corals", "Feed your corals", 10, "Coral powder, cyclops or plankton."),
    TEST_WATER("test_water", "Test the water", 10, "Open Tank Health to test it."),
    TOP_OFF("top_off", "Top off the water", 10, "Add fresh water for what evaporated."),
    EMPTY_SKIMMER("empty_skimmer", "Empty the skimmer cup", 15, "The cup is getting full."),
    CHANGE_SOCK("change_sock", "Swap the filter sock", 15, "The sock is dirty."),
    CLEAN_GLASS("clean_glass", "Clean the glass", 15, "Slide the scraper over the front glass."),
    SIPHON_SAND("siphon_sand", "Siphon the sand", 20, "Suck the gunk out of the sand."),
    HARVEST_MACRO("harvest_macro", "Harvest macroalgae", 15, "Trim the refugium."),
    REPLACE_CARBON("replace_carbon", "Swap the carbon", 15, "The carbon is used up."),
    WATER_CHANGE("water_change", "Water change", 30, "Swap 10-25 % for fresh salt water.");

    companion object {
        fun byId(id: String): CareTask? = entries.firstOrNull { it.id == id }
    }
}

/** One-time rewards (docs/ECONOMY.md §4 Goals). */
enum class Goal(val id: String, val label: String, val reward: Int) {
    FIRST_FISH("first_fish", "Your first fish", 25),
    FIRST_CORAL("first_coral", "Your first coral", 25),
    FIVE_SPECIES("five_species", "5 different kinds of animals", 50),
    ANEMONE_SPLIT("anemone_split", "An anemone split in two", 50),
    BABY_FISH("baby_fish", "Raise baby fish in the nursery", 150),
    FISH_30_DAYS("fish_30_days", "Keep a fish for 30 days", 100),
    STREAK_7("streak_7", "Finish the list 7 days in a row", 100),
    LEVEL_5("level_5", "Reach level 5", 100);

    companion object {
        fun byId(id: String): Goal? = entries.firstOrNull { it.id == id }
    }
}

/** Levels from star points: level n needs 50 · n · (n − 1) (100, 300, 600, 1000 ...), up to [MAX]. */
object Levels {
    const val MAX = 10

    fun starsFor(level: Int): Int = 50 * level * (level - 1)

    fun level(stars: Int): Int {
        var n = 1
        while (n < MAX && stars >= starsFor(n + 1)) n++
        return n
    }

    /** What a level opens, said on the level-up cheer ("" when nothing new). */
    fun opens(level: Int): String = when (level) {
        2 -> "Frag Shack is open!"
        3 -> "New animals at the stores!"
        4 -> "Big Blue Aquatics is open!"
        5 -> "A 4th daily task, for more sand dollars!"
        6 -> "Deep Reef Imports is open!"
        7 -> "The rarest animals are in the stores!"
        else -> ""
    }
}

/** What the screens show (Care card, money chip): one immutable copy per change. */
data class EconomyView(
    val coins: Int,
    val level: Int,
    /** Star points into this level, and how many it takes to reach the next (0 at [Levels.MAX]). */
    val levelStars: Int,
    val levelNeed: Int,
    val tasks: List<TaskView>,
    val allDoneBonus: Int,
    val allDonePaid: Boolean,
    /** Days in a row so far (counting today once the list is done) and today's pay multiplier. */
    val streak: Int,
    val multiplier: Double,
    /** What "Collect" pays now, and whether today's is already collected. */
    val happyBonus: Int,
    val happyPaid: Boolean,
    val goals: List<GoalView>,
    val ledger: List<LedgerEntry>,
) {
    /** Something on the Care card waits for a tap or a chore (the dot on the money chip). */
    val attention: Boolean get() = !happyPaid && happyBonus > 0 || tasks.any { !it.done }
}

data class TaskView(val task: CareTask, val pay: Int, val done: Boolean)
data class GoalView(val goal: Goal, val done: Boolean)

/** Money that just came in, for the coins-flying cheer; [levelUp] is the new level when this one crossed it. */
data class Gain(val amount: Int, val label: String, val levelUp: Int = 0)

/**
 * The live economy of one My tank. Confined to the thread that runs the sim (Android's sim thread, the web page's
 * frame loop): [update] reads the sim's [CareLog] counters for chores done since the last call.
 */
class Economy(initial: SavedEconomy = SavedEconomy()) {
    var state: SavedEconomy = initial
        private set

    /** Money that came in since [takeGains] was last called (the hosts turn these into the coin cheer). */
    private val gains = ArrayList<Gain>()

    // last-seen CareLog counts; -1 until [update] first sees the sim
    private var seenFish = -1
    private var seenCoral = 0
    private val seenMaint = IntArray(MaintenanceTask.entries.size)
    private var seenChanges = 0
    private var seenScrapes = 0
    private var seenSplits = 0
    private var seenBabies = 0
    private var seenLog: CareLog? = null

    val level: Int get() = Levels.level(state.stars)

    fun takeGains(): List<Gain> = if (gains.isEmpty()) emptyList() else ArrayList(gains).also { gains.clear() }

    /**
     * Rolls a new day's list when [today] changed, ticks off the chores done since the last call and pays any goal
     * reached. Returns true when anything the screens show changed.
     */
    fun update(sim: Simulation, today: String): Boolean {
        val before = state
        if (state.day != today) newDay(sim, today)
        val log = sim.careLog
        if (seenLog !== log) { remember(log); seenLog = log }
        if (log.fishFeeds > seenFish) { seenFish = log.fishFeeds; done(CareTask.FEED_FISH, today) }
        if (log.coralFeeds > seenCoral) { seenCoral = log.coralFeeds; done(CareTask.FEED_CORALS, today) }
        for (t in MaintenanceTask.entries) {
            val n = log.maintained[t.ordinal]
            if (n > seenMaint[t.ordinal]) { seenMaint[t.ordinal] = n; taskFor(t)?.let { done(it, today) } }
        }
        if (log.waterChanges > seenChanges) {
            seenChanges = log.waterChanges
            state = state.copy(lastWaterChangeDay = today)
            done(CareTask.WATER_CHANGE, today)
        }
        if (log.scrapes > seenScrapes) { seenScrapes = log.scrapes; done(CareTask.CLEAN_GLASS, today) }
        if (log.anemoneSplits > seenSplits) { seenSplits = log.anemoneSplits; goal(Goal.ANEMONE_SPLIT) }
        if (log.babiesReleased > seenBabies) { seenBabies = log.babiesReleased; goal(Goal.BABY_FISH) }
        checkGoals(sim)
        return state != before
    }

    /** The keeper opened the water test (Tank Health). */
    fun testedWater(today: String) = done(CareTask.TEST_WATER, today)

    /** Pays today's happy tank bonus (once a day); returns what it paid. */
    fun collectHappy(sim: Simulation, today: String): Int {
        if (state.day != today) newDay(sim, today)
        if (state.happyPaid) return 0
        val n = happyBonus(sim)
        if (n <= 0) return 0
        state = state.copy(happyPaid = true)
        earn(n, "Happy tank bonus", today)
        return n
    }

    /** Spends [amount] if there is enough (stores, update 3); false and nothing changes when there is not. */
    fun spend(amount: Int, label: String, today: String): Boolean {
        if (amount < 0 || amount > state.coins) return false
        state = state.copy(coins = state.coins - amount, ledger = ledger(today, label, -amount))
        return true
    }

    /** How many of [speciesId] [store] has left today. */
    fun left(store: FishStore, item: StockItem, today: String): Int {
        val b = if (state.boughtDay == today) state.bought["${store.id}/${item.listing.speciesId}"] ?: 0 else 0
        return max(0, item.count - b)
    }

    /** Whether [store] is open to this keeper and [l] can be bought at this level (the lock on the card). */
    fun canSee(store: FishStore): Boolean = level >= store.level
    fun unlocked(l: Listing): Boolean = level >= l.level

    /**
     * Buys one [speciesId] at [store] today at the shelf price. On [BuyResult.OK] the money is gone and the caller
     * brings the animal in (the shop bag, as Add life does).
     */
    fun buy(store: FishStore, speciesId: String, today: String): BuyResult {
        if (!canSee(store)) return BuyResult.STORE_LOCKED
        val item = Stores.stock(store, today).firstOrNull { it.listing.speciesId == speciesId } ?: return BuyResult.NOT_IN_STOCK
        if (!unlocked(item.listing)) return BuyResult.LEVEL_LOCKED
        if (left(store, item, today) <= 0) return BuyResult.SOLD_OUT
        if (item.price > state.coins) return BuyResult.TOO_POOR
        val key = "${store.id}/$speciesId"
        val bought = if (state.boughtDay == today) state.bought else emptyMap()
        spend(item.price, "${store.label}: ${speciesLabel(speciesId)}", today)
        state = state.copy(boughtDay = today, bought = bought + (key to (bought[key] ?: 0) + 1))
        return BuyResult.OK
    }

    /**
     * Never stuck: a tank with no animals and not enough money for the cheapest one gets two free snails from
     * Shelly's, once a day. Returns true when the caller should bring them in.
     */
    fun starterGift(sim: Simulation, today: String): Boolean {
        if (state.giftDay == today || sim.organisms.any { it.alive }) return false
        val cheapest = PriceBook.all.filter { unlocked(it) }.minOfOrNull { it.price } ?: return false
        if (state.coins >= cheapest) return false
        state = state.copy(giftDay = today, ledger = ledger(today, "Gift from Shelly's: 2 snails", 0))
        return true
    }

    private fun speciesLabel(id: String) = com.reeflive.core.sim.Stocking.entry(id)?.common ?: id

    /** Money from a sale: sand dollars but no star points (so trading cannot farm levels). */
    fun sold(amount: Int, label: String, today: String) {
        if (amount <= 0) return
        state = state.copy(coins = state.coins + amount, ledger = ledger(today, label, amount))
        gains += Gain(amount, label)
    }

    fun view(sim: Simulation): EconomyView {
        val s = state
        val lv = level
        val mult = multiplier()
        return EconomyView(
            coins = s.coins,
            level = lv,
            levelStars = s.stars - Levels.starsFor(lv),
            levelNeed = if (lv >= Levels.MAX) 0 else Levels.starsFor(lv + 1) - Levels.starsFor(lv),
            tasks = s.tasks.mapNotNull { CareTask.byId(it) }.map { TaskView(it, (it.pay * mult).roundToInt(), it.id in s.done) },
            allDoneBonus = (ALL_DONE_BONUS * mult).roundToInt(),
            allDonePaid = s.allDonePaid,
            streak = s.streak,
            multiplier = mult,
            happyBonus = happyBonus(sim),
            happyPaid = s.happyPaid,
            goals = Goal.entries.map { GoalView(it, it.id in s.goals) },
            ledger = s.ledger,
        )
    }

    // ============================================================== inside

    private fun remember(log: CareLog) {
        seenFish = log.fishFeeds; seenCoral = log.coralFeeds; log.maintained.copyInto(seenMaint)
        seenChanges = log.waterChanges; seenScrapes = log.scrapes; seenSplits = log.anemoneSplits; seenBabies = log.babiesReleased
    }

    private fun taskFor(t: MaintenanceTask): CareTask? = when (t) {
        MaintenanceTask.EMPTY_SKIMMER -> CareTask.EMPTY_SKIMMER
        MaintenanceTask.CHANGE_SOCK -> CareTask.CHANGE_SOCK
        MaintenanceTask.SIPHON_SAND -> CareTask.SIPHON_SAND
        MaintenanceTask.HARVEST_MACRO -> CareTask.HARVEST_MACRO
        MaintenanceTask.REPLACE_CARBON -> CareTask.REPLACE_CARBON
        MaintenanceTask.TOP_OFF -> CareTask.TOP_OFF
        MaintenanceTask.RINSE_FILTER -> null
    }

    /** Streak bonus on today's pay: +10 % per day in a row before today, up to +50 %. */
    private fun multiplier(): Double {
        val s = state
        val running = when (s.lastFullDay) {
            "" -> 0
            s.day -> s.streak - 1
            else -> if (CalendarDay.daysBetween(s.lastFullDay, s.day) == 1) s.streak else 0
        }
        return 1.0 + STREAK_STEP * min(running, STREAK_DAYS)
    }

    private fun newDay(sim: Simulation, today: String) {
        // a tank new to the economy counts as freshly changed: the first water change is asked for a week later
        if (state.lastWaterChangeDay.isEmpty()) state = state.copy(lastWaterChangeDay = today)
        val s = state
        val keep = s.lastFullDay.isNotEmpty() && CalendarDay.daysBetween(s.lastFullDay, today) <= 1
        val count = if (level >= 5) 4 else 3
        state = s.copy(
            day = today, tasks = CareTasks.pick(sim, today, count, s.lastWaterChangeDay).map { it.id }, done = emptyList(),
            allDonePaid = false, happyPaid = false, streak = if (keep) s.streak else 0,
        )
    }

    private fun done(t: CareTask, today: String) {
        val s = state
        if (s.day != today || t.id !in s.tasks || t.id in s.done) return
        val mult = multiplier()
        state = s.copy(done = s.done + t.id)
        earn((t.pay * mult).roundToInt(), t.label, today)
        if (!state.allDonePaid && state.tasks.all { it in state.done }) {
            val running = state.lastFullDay.isNotEmpty() && CalendarDay.daysBetween(state.lastFullDay, today) == 1
            state = state.copy(allDonePaid = true, streak = if (running) state.streak + 1 else 1, lastFullDay = today)
            earn((ALL_DONE_BONUS * mult).roundToInt(), "All done today!", today)
            if (state.streak >= 7) goal(Goal.STREAK_7)
        }
    }

    private fun goal(g: Goal) {
        if (g.id in state.goals) return
        state = state.copy(goals = state.goals + g.id)
        earn(g.reward, "Goal: ${g.label}", state.day)
    }

    private fun checkGoals(sim: Simulation) {
        val have = state.goals
        if (have.size >= Goal.entries.size) return
        var fish = false
        var oldFish = false
        for (o in sim.organisms) if (o.alive && o.isFish) {
            fish = true
            if (sim.simTimeSec - o.arrivalTime >= 30 * 86400.0 && o.arrivalTime > 0) oldFish = true
        }
        if (fish && Goal.FIRST_FISH.id !in have) goal(Goal.FIRST_FISH)
        if (oldFish && Goal.FISH_30_DAYS.id !in have) goal(Goal.FISH_30_DAYS)
        if (Goal.FIRST_CORAL.id !in have && sim.corals.any { it.tissue > 0.01 }) goal(Goal.FIRST_CORAL)
        if (Goal.FIVE_SPECIES.id !in have) {
            val kinds = HashSet<String>()
            for (o in sim.organisms) if (o.alive) kinds += o.species.id
            for (c in sim.corals) if (c.tissue > 0.01) kinds += c.species.id
            if (kinds.size >= 5) goal(Goal.FIVE_SPECIES)
        }
        if (Goal.LEVEL_5.id !in have && level >= 5) goal(Goal.LEVEL_5)
    }

    private fun earn(amount: Int, label: String, today: String) {
        if (amount <= 0) return
        val was = level
        state = state.copy(coins = state.coins + amount, stars = state.stars + amount, ledger = ledger(today, label, amount))
        val now = level
        gains += Gain(amount, label, if (now > was) now else 0)
        if (now > was && now >= 5) goal(Goal.LEVEL_5)
    }

    private fun ledger(day: String, label: String, amount: Int): List<LedgerEntry> =
        (listOf(LedgerEntry(day, label, amount)) + state.ledger).take(LEDGER_MAX)

    companion object {
        const val START_COINS = 100
        const val ALL_DONE_BONUS = 25
        const val STREAK_STEP = 0.1
        const val STREAK_DAYS = 5
        const val HAPPY_PER_ANIMAL = 2
        const val HAPPY_PER_CORAL = 1
        const val HAPPY_CAP = 60
        const val THRIVING = 0.7
        const val LEDGER_MAX = 30

        /** Happy tank bonus now: 2 per thriving animal, 1 per healthy coral, capped. */
        fun happyBonus(sim: Simulation): Int {
            var n = 0
            for (o in sim.organisms) if (o.alive && o.health > THRIVING) n += HAPPY_PER_ANIMAL
            for (c in sim.corals) if (c.tissue > THRIVING && c.bleach < 0.3) n += HAPPY_PER_CORAL
            return min(n, HAPPY_CAP)
        }
    }
}

/**
 * Picks the day's care list: chores the tank needs today first (the same signals as the tank check, [TankDoctor]),
 * then everyday chores, shuffled by the date so the list is the same all day and different tomorrow.
 */
object CareTasks {
    /** Detritus (ppm) worth a siphon, glass film cover worth a scrape, macro cover worth a harvest. */
    const val SIPHON_AT = 0.3
    const val GLASS_AT = 0.12
    const val MACRO_AT = 0.5
    const val WATER_CHANGE_DAYS = 7

    fun pick(sim: Simulation, today: String, count: Int, lastWaterChangeDay: String): List<CareTask> {
        val w = sim.water
        val care = w.careState()
        val fish = sim.organisms.any { it.alive && it.isFish }
        val corals = sim.corals.any { it.tissue > 0.01 } || sim.organisms.any { it.alive && it.species.id in FILTER_FEEDERS }
        val sinceChange = if (lastWaterChangeDay.isEmpty()) 0 else CalendarDay.daysBetween(lastWaterChangeDay, today)
        val need = ArrayList<CareTask>()
        if (care.skimmerOn && care.skimmerCup >= 0.5f) need += CareTask.EMPTY_SKIMMER
        if (care.sockOn && care.sockAgeH >= TankDoctor.SOCK_SWAP_H) need += CareTask.CHANGE_SOCK
        if (care.carbonOn && care.carbonLeft < TankDoctor.CARBON_SWAP_LEFT) need += CareTask.REPLACE_CARBON
        if (care.waterFull < TankDoctor.TOP_OFF_BELOW) need += CareTask.TOP_OFF
        if (w.s.film >= GLASS_AT) need += CareTask.CLEAN_GLASS
        if (w.s.detritus >= SIPHON_AT) need += CareTask.SIPHON_SAND
        if (w.refugium && w.s.macro >= MACRO_AT) need += CareTask.HARVEST_MACRO
        if (sinceChange >= WATER_CHANGE_DAYS || w.s.no3 > 20.0) need += CareTask.WATER_CHANGE
        val chores = ArrayList<CareTask>()
        if (fish) chores += CareTask.FEED_FISH
        if (corals) chores += CareTask.FEED_CORALS
        chores += CareTask.TEST_WATER
        chores += CareTask.SIPHON_SAND
        chores += CareTask.TOP_OFF
        chores += CareTask.CLEAN_GLASS
        val rng = com.reeflive.core.geom.Rng(today.hashCode().toLong() * 31 + sim.config.seed)
        val out = ArrayList<CareTask>()
        // fish that are there get fed every day: that one is always on the list
        if (fish) out += CareTask.FEED_FISH
        for (t in shuffled(need, rng)) if (out.size < count && t !in out) out += t
        for (t in shuffled(chores, rng)) if (out.size < count && t !in out) out += t
        return out
    }

    private fun <T> shuffled(l: List<T>, rng: com.reeflive.core.geom.Rng): List<T> {
        val a = l.toMutableList()
        for (i in a.size - 1 downTo 1) { val j = rng.nextInt(i + 1); val t = a[i]; a[i] = a[j]; a[j] = t }
        return a
    }

    /** Animals that eat coral food (they make "Feed your corals" a chore in a tank without corals). */
    private val FILTER_FEEDERS = setOf("sabellastarte_sp", "entacmaea_quadricolor")
}

/** `yyyy-mm-dd` calendar days without java.time (core also compiles to JS). */
object CalendarDay {
    fun key(year: Int, month: Int, day: Int): String =
        "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"

    /** Days from [a] to [b] (b − a); a large number when either is not a date. */
    fun daysBetween(a: String, b: String): Int {
        val x = epochDay(a) ?: return 9999
        val y = epochDay(b) ?: return 9999
        return (y - x).toInt()
    }

    /** Days since 1970-01-01 (proleptic Gregorian; Hinnant's days_from_civil). */
    fun epochDay(k: String): Long? {
        val p = k.split('-')
        if (p.size != 3) return null
        var y = p[0].toIntOrNull()?.toLong() ?: return null
        val m = p[1].toIntOrNull() ?: return null
        val d = p[2].toIntOrNull() ?: return null
        if (m !in 1..12 || d !in 1..31) return null
        if (m <= 2) y -= 1
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (m + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }
}

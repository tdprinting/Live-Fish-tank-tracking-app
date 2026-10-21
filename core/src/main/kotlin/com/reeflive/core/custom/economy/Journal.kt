package com.reeflive.core.custom.economy

import com.reeflive.core.sim.CoralKind
import com.reeflive.core.sim.SimEvent
import com.reeflive.core.sim.Simulation
import kotlinx.serialization.Serializable

/**
 * The reef journal (docs/ECONOMY.md §4 "Reef moments"): real things the keeper sees happen in the tank for the first
 * time, every kind of animal they have kept, and each fish's birthdays. Saved in [SavedEconomy.journal]; every field
 * has a default, so an older save opens with an empty journal.
 */
@Serializable
data class SavedJournal(
    /** [Moment.id]s seen so far, oldest first. */
    val moments: List<String> = emptyList(),
    /** Species ids that have lived in this tank (fish, inverts, corals, hitchhikers), oldest first. */
    val kept: List<String> = emptyList(),
    /** Organism id → how many of [Journal.BIRTHDAY_DAYS] it has had; dropped when the animal leaves or dies. */
    val birthdays: Map<String, Int> = emptyMap(),
)

/**
 * The reef moments: each one is a real event from the sim ([SimEvent]), paid once ever, with a true reef fact to go
 * with it. [hint] is what the journal shows while it is still "???".
 */
enum class Moment(val id: String, val title: String, val reward: Int, val hint: String, val fact: String) {
    CLEANING(
        "cleaning", "Cleaning station", 8, "Watch a cleaner wrasse or cleaner shrimp at work.",
        "Cleaners pick parasites and old skin off other fish. On a wild reef one cleaner wrasse can have over 2,000 visitors a day!",
    ),
    POSING(
        "posing", "Please clean me!", 5, "A fish asks a cleaner for help.",
        "Fish ask to be cleaned by holding still, spreading their fins and opening their mouth and gills wide.",
    ),
    MOLT(
        "molt", "A brand new shell", 8, "Shrimp and crabs outgrow their shells.",
        "Shrimp and crabs grow by climbing out of their old shell. The empty shell looks like a dead animal, but it is only " +
            "old clothes! The new shell stays soft for a day or two, so they hide.",
    ),
    BOSS(
        "boss", "Who's the boss?", 5, "Watch two clownfish together.",
        "The smaller clownfish shakes its body to say \"you're the boss\". In every clown pair the biggest is the female " +
            "and the second biggest is the male.",
    ),
    EGGS(
        "eggs", "Eggs!", 10, "A happy, well fed pair might spawn.",
        "Your fish laid eggs! Clownfish stick theirs to a rock beside their host, and dad fans and guards them until they " +
            "hatch about a week later, just after the lights go out.",
    ),
    HATCH(
        "hatch", "Hatching night", 10, "Eggs hatch in the dark.",
        "The eggs hatched into larvae smaller than an eyelash. In the wild they drift with the plankton for days or weeks " +
            "before they settle on a reef.",
    ),
    LITTLE_FISH(
        "little_fish", "Tiny fish", 10, "Raise larvae in the nursery.",
        "The larvae turned into tiny fish with real fins. Baby clownfish grow their white stripes when they are about two " +
            "weeks old.",
    ),
    CORAL_BABY(
        "coral_baby", "A coral baby", 8, "Healthy corals grow new colonies.",
        "A coral made a new colony! Kenya trees drop little branches that roll away and stick to a new rock, and zoas, " +
            "star polyps and mushrooms creep over the rock and split.",
    ),
    HITCHHIKER(
        "hitchhiker", "Surprise visitor", 5, "Something sneaks in on rock or coral.",
        "Lots of tiny animals ride in on rock and coral: worms, snails, pods and baby sea stars. Most are harmless and " +
            "many are helpful clean-up crew, but keep an eye out for pests.",
    ),
    RECOVERED(
        "recovered", "Feeling better", 10, "Nurse a sick animal back to health.",
        "A sick animal got better! Clean water, good food and calm tankmates help a fish's body fight off sickness.",
    );

    companion object {
        fun byId(id: String): Moment? = entries.firstOrNull { it.id == id }
    }
}

/** A cheer with words: the reef moment or birthday card shown when its [Gain] comes in. */
data class Story(val title: String, val headline: String, val fact: String)

/** What the Care card's journal shows. */
data class JournalView(val moments: List<MomentView>, val kept: List<String>)
data class MomentView(val moment: Moment, val found: Boolean)

/**
 * Reads the sim for the journal on each [Economy.update]: new events since the last call, the animals in the tank and
 * the fish's time with the keeper. Pays through [pay] (Economy.earn), so everything here gives star points too.
 */
internal class Journal {
    private var seenLog: Any? = null
    private var lastEventT = Double.NEGATIVE_INFINITY

    /** Returns the journal after this update; [pay] is called once per new moment or birthday. */
    fun update(sim: Simulation, j: SavedJournal, pay: (amount: Int, label: String, story: Story) -> Unit): SavedJournal {
        var out = j
        // a new sim (load, start over) starts its own event log: read it from the top
        if (seenLog !== sim.eventLog) { seenLog = sim.eventLog; lastEventT = Double.NEGATIVE_INFINITY }
        var newest = lastEventT
        for (e in sim.eventLog) {
            if (e.simTimeSec <= lastEventT) continue
            if (e.simTimeSec > newest) newest = e.simTimeSec
            val m = momentFor(sim, e) ?: continue
            if (m.id in out.moments) continue
            out = out.copy(moments = out.moments + m.id)
            pay(m.reward, "Reef moment: ${m.title}", Story(m.title, headline(sim, e, m), m.fact))
        }
        lastEventT = newest
        out = keep(sim, out)
        return birthdays(sim, out, pay)
    }

    private fun keep(sim: Simulation, j: SavedJournal): SavedJournal {
        var add: LinkedHashSet<String>? = null
        for (o in sim.organisms) if (o.alive && o.species.id !in j.kept) {
            val a = add ?: LinkedHashSet<String>()
            a += o.species.id
            add = a
        }
        for (c in sim.corals) if (c.tissue > 0.01 && c.species.id !in j.kept) {
            val a = add ?: LinkedHashSet<String>()
            a += c.species.id
            add = a
        }
        val a = add ?: return j
        return j.copy(kept = j.kept + a)
    }

    private fun birthdays(sim: Simulation, j: SavedJournal, pay: (Int, String, Story) -> Unit): SavedJournal {
        var map = j.birthdays
        // animals that left (sold, died, netted out) take their birthdays with them
        if (map.isNotEmpty()) {
            val here = HashSet<String>()
            for (o in sim.organisms) if (o.alive) here += o.id
            if (map.keys.any { it !in here }) map = map.filterKeys { it in here }
        }
        for (o in sim.organisms) {
            if (!o.alive || !o.isFish || o.arrivalTime <= 0) continue
            val days = (sim.simTimeSec - o.arrivalTime) / 86400.0
            var reached = 0
            while (reached < BIRTHDAY_DAYS.size && days >= BIRTHDAY_DAYS[reached]) reached++
            val had = map[o.id] ?: 0
            if (reached <= had) continue
            map = map + (o.id to reached)
            // a long time away can pass several at once: one cheer, for the biggest
            val i = reached - 1
            val name = o.nickname.ifBlank { o.species.common }
            val d = BIRTHDAY_DAYS[i]
            val span = when (d) { 7 -> "1 week"; 30 -> "1 month"; 365 -> "1 whole year"; else -> "$d days" }
            pay(
                BIRTHDAY_PAY[i], "$name's $span with you",
                Story("Happy birthday, $name!", "$name has lived in your tank for $span.", BIRTHDAY_FACTS[i]),
            )
        }
        return if (map === j.birthdays) j else j.copy(birthdays = map)
    }

    private fun momentFor(sim: Simulation, e: SimEvent): Moment? = when (e) {
        is SimEvent.Cleaned -> Moment.CLEANING
        is SimEvent.Pose -> Moment.POSING
        is SimEvent.Molted -> Moment.MOLT
        is SimEvent.Quiver -> Moment.BOSS
        is SimEvent.EggsLaid -> Moment.EGGS
        is SimEvent.Hatched -> Moment.HATCH
        is SimEvent.Metamorphosed -> Moment.LITTLE_FISH
        is SimEvent.CoralSpawned ->
            if (e.parentId.isNotEmpty() && sim.corals.firstOrNull { it.id == e.id }?.kind.let { it != null && it != CoralKind.AIPTASIA }) Moment.CORAL_BABY else null
        is SimEvent.Hitchhiker -> Moment.HITCHHIKER
        is SimEvent.Recovered -> Moment.RECOVERED
        else -> null
    }

    /** The card's first line, with the animals' own names. */
    private fun headline(sim: Simulation, e: SimEvent, m: Moment): String {
        fun name(id: String) = sim.organisms.firstOrNull { it.id == id }?.let { it.nickname.ifBlank { it.species.common } }
        return when (e) {
            is SimEvent.Cleaned -> pair(name(e.cleanerId), name(e.clientId), "is cleaning", "A cleaner is cleaning a fish!")
            is SimEvent.Pose -> pair(name(e.clientId), name(e.cleanerId), "is posing for", "A fish is posing for a cleaner!")
            is SimEvent.Molted -> name(e.id)?.let { "$it just molted!" } ?: "Something just molted!"
            is SimEvent.Quiver -> pair(name(e.id), name(e.toId), "is shaking at", "A clownfish is shaking at its partner!")
            is SimEvent.EggsLaid -> if (e.spec == "tang") "Your tangs spawned! Their tiny eggs float away in the current." else "Your fish laid ${e.eggs} eggs!"
            is SimEvent.Hatched -> "${e.larvae} larvae hatched!"
            is SimEvent.Metamorphosed -> "${e.fry} larvae became little fish!"
            is SimEvent.CoralSpawned -> sim.corals.firstOrNull { it.id == e.id }?.let {
                if (it.kind == CoralKind.KENYA) "Your Kenya tree dropped a baby branch!" else "Your ${it.species.common} grew a new colony!"
            } ?: m.title
            is SimEvent.Hitchhiker -> "${e.label} rode into your tank!"
            is SimEvent.Recovered -> name(e.id)?.let { "$it is feeling better!" } ?: "A sick animal is feeling better!"
            else -> m.title
        }
    }

    private fun pair(a: String?, b: String?, verb: String, fallback: String) =
        if (a != null && b != null) "$a $verb $b!" else fallback

    companion object {
        /** Days in the tank that get a birthday, what each pays, and the fact on its card. */
        val BIRTHDAY_DAYS = intArrayOf(7, 30, 100, 365)
        val BIRTHDAY_PAY = intArrayOf(3, 6, 10, 25)
        val BIRTHDAY_FACTS = listOf(
            "The first weeks are the hardest for a new fish: a new home, new food, new tankmates. This one made it!",
            "After a month a fish knows every rock, its favourite hiding spot and when food comes.",
            "100 days! Steady water and good food every day is what keeps a fish this healthy.",
            "One whole year! With good care many reef fish live 5 to 10 years or more, and clownfish have lived past 20.",
        )
    }
}

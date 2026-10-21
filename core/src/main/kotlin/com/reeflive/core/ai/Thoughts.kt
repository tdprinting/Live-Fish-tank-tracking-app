package com.reeflive.core.ai

import com.reeflive.core.sim.BrainKind
import com.reeflive.core.sim.FoodKind
import com.reeflive.core.sim.HeatModel
import com.reeflive.core.sim.Organism
import com.reeflive.core.sim.Simulation
import com.reeflive.core.sim.Thought
import com.reeflive.core.sim.TouchReaction

/**
 * "What is it thinking?" (SIMULATION §4.10): a plain-English readout of every animal's current plan, its next plan and
 * how it feels, for the fish card, follow mode and the thought bubbles. Read-only: it looks at the brain's current
 * behaviour, the last utility scores, the focus / threat / wary animals and the water, and never draws from an rng or
 * writes to the sim, so a run with the readout on is identical to one with it off (ThoughtsTest).
 *
 * Every behaviour of every brain has its own sentence ([line]); nothing falls through to a raw behaviour name.
 */
object Thoughts {

    /** The main driver behind a plan; turned into the short "why" and a default mood. */
    internal enum class Driver { HUNGER, FEAR, CURIOSITY, TERRITORY, FAMILY, HOME, CLEANER_JOB, GET_CLEANED, REST, WATER, HEALTH, GROWING, ROUTINE }

    /** One plan in words: [now] after "Name is …", [next] as an order ("graze the nori clip"), a short first-person [bubble]. */
    internal class Line(val now: String, val next: String, val bubble: String, val driver: Driver, val interest: Int = 1)

    /** Builds the thought of [o]; [target] is the animal its behaviour is about (Caption's target), if any. */
    fun of(o: Organism, sim: Simulation, target: Organism?, prevNextIndex: Int = -1): Pair<Thought, Int> {
        val name = nameOf(o)
        if (!o.alive) {
            return Thought(
                now = "Sadly, $name has died.", next = "", feeling = "", mood = "gone", why = "",
                bubble = "", planKey = "dead", interest = 0,
            ) to -1
        }
        val brain = sim.agentOf(o)?.brain
        val ub = runCatching { o.brain }.getOrNull()
        val cur = ub?.current
        if (brain == null || ub == null || cur == null) {
            return Thought(now = "$name is settling in.", next = "", feeling = "Feeling: a little shy.", mood = "shy",
                why = "it is new here", bubble = "Hello?", planKey = "new", interest = 1) to -1
        }
        val env = Env(o, sim, brain, target)
        val line = line(env, cur) ?: fallback(env)
        var now = line.now
        // an idle animal with an empty tummy: say so (the brain's own feed behaviour takes over once food is in sight)
        if (o.isFish && o.hunger > HUNGRY && cur.name in IDLE) now = "is hungry and watching the water for food"
        val sentence = "${cap(name)} ${if (now.startsWith("has ") || now.startsWith("is ")) now else "is $now"}."

        // the runner-up behaviour by its last utility score is the "next" plan; a time-of-day plan when nothing else is close
        val (nextIdx, nextLine) = runnerUp(env, ub, cur, prevNextIndex)
        val nextText = nextLine?.next ?: timePlan(env, cur)
        val next = if (nextText.isNullOrBlank()) "" else "Next: $nextText."

        val (mood, reason) = feeling(env, cur, line.driver)
        val feelingText = if (reason.isBlank()) "Feeling: $mood." else "Feeling: $mood ($reason)."
        val interest = (line.interest + if (o.hunger > HUNGRY && line.driver == Driver.HUNGER) 1 else 0).coerceIn(0, 3)
        val bubble = if (o.isFish && o.hunger > HUNGRY && cur.name in IDLE) "I'm hungry!" else line.bubble
        return Thought(
            now = sentence, next = next, feeling = feelingText, mood = mood, why = whyOf(env, line.driver),
            // the plan's own target (focusId) only: Caption's nearest-animal guess changes too often to count as a new plan
            bubble = bubble, planKey = cur.name + "|" + (o.focusId ?: ""), interest = interest,
        ) to nextIdx
    }

    // ------------------------------------------------------------------ the sentences

    /** The bubble tip anemone's sentence follows what its body is doing (AnemoneBrain's label). */
    private fun anemoneLine(label: String): Line = when {
        label.startsWith("Passing") -> Line("rolling a piece of food into its mouth with its tentacles", "finish its meal", "Mmm, got one!", Driver.HUNGER, 3)
        label.startsWith("Pulled") -> Line("pulling its tentacles in after being bumped", "open back up", "Hey, careful!", Driver.FEAR, 2)
        label.startsWith("Deflated") -> Line("shrunk down small, flushing out old water and waste", "puff back up", "Just cleaning house.", Driver.HEALTH, 2)
        label.startsWith("Healing") -> Line("healing after splitting into two anemones", "heal and grow", "There are two of me now!", Driver.GROWING, 3)
        label.startsWith("Walking") -> Line("slowly walking on its sticky foot to a better spot", "find a crevice with good light", "Not quite right here…", Driver.HOME, 2)
        label.startsWith("Looking") -> Line("looking for a rock crevice to tuck its foot into", "settle down in a crevice", "Where should I live?", Driver.HOME, 2)
        label.startsWith("Partly") -> Line("partly closed up for the night", "open up when the lights come on", "Sleepy time.", Driver.REST)
        label.startsWith("Bubbled") -> Line("sitting happily with its tentacle tips puffed into bubbles", "soak up the light", "Feeling bubbly!", Driver.ROUTINE)
        else -> Line("waving its tentacles in the current and soaking up the light", "catch some food in the current", "Ahh, nice light.", Driver.ROUTINE)
    }

    /** The sentence for behaviour [b] of the animal in [e]; null only for a behaviour this table does not know. */
    internal fun line(e: Env, b: Behavior): Line? {
        val k = e.o.brainKind
        val label = b.label
        val n = b.name
        val tgt = e.target?.let { refer(e.o, it) }
        // cleaners only ever clean fish (Caption's nearest-animal guess can be a snail)
        val client = e.target?.takeIf { it.isFish }?.let { refer(e.o, it) }
        return when {
            // ---- shared layers (every brain)
            n == "rivalry" -> Line("chasing ${tgt ?: "a rival"} away", "chase off a rival", "Shoo! My spot!", Driver.TERRITORY, 2)
            n == "unwell" -> Line("resting quietly because it is not feeling well", "rest until it feels better", "Not feeling great…", Driver.HEALTH, 2)
            n == "heat" -> Line(heatNow(e, label), "find cooler water", "Too hot!", Driver.WATER, 3)
            n == "touch" -> touchLine(e)
            b is SubmarineReact -> subLine(b)
            n == "bout" -> boutLine(b, tgt)
            n == "yield" -> Line("backing off to let ${tgt ?: "the boss"} go first", "give way to the boss", "OK, OK, you first!", Driver.TERRITORY, 2)
            n == "pose" -> Line("holding still so ${tgt ?: "the cleaner"} can clean it", "get cleaned", "Clean me, please!", Driver.GET_CLEANED, 2)
            n == "keepclear" -> Line("keeping its distance from ${tgt ?: e.waryName ?: "a bigger fish"}", "stay out of the way", "I'll stay over here.", Driver.FEAR, 2)
            n == "molt" -> Line("hiding while it molts (it is growing a new shell!)", "find a safe spot to molt", "Growing a new shell!", Driver.GROWING, 3)
            n == "tailflip" -> Line("flipping its tail to zoom away${e.because()}", "zoom away from danger", "Whoosh!", Driver.FEAR, 3)
            n == "shellswap" -> Line("trying on a new shell", "look for a roomier shell", "Ooh, a new house?", Driver.GROWING, 3)
            n == "gasp" -> Line("gulping at the surface because the water is low on air", "get some air at the surface", "Need air!", Driver.WATER, 3)
            n == "flash" -> Line("rubbing on the rock to scratch an itch", "scratch an itch on the rock", "So itchy!", Driver.HEALTH, 2)
            n == "visit" -> Line(if (tgt != null) "waiting for $tgt to clean it" else "lining up at the cleaning station", "visit the cleaner", "My turn to get cleaned!", Driver.GET_CLEANED, 2)
            n == "glass" -> Line("looking at you through the glass", "come say hi at the glass", "Hi there!", Driver.CURIOSITY, 2)
            n == "anticipate" -> Line("waiting by the feeding spot because it is almost dinner time", "wait for feeding time", "Is it dinner yet?", Driver.HUNGER, 2)
            n == "beg" -> Line("swimming to the glass hoping you have food", "beg for food at the glass", "Food? Food?", Driver.HUNGER, 2)
            n == "sandrest" -> Line(if (tgt != null) "resting head-up on the sand, still watching $tgt" else "resting head-up on the sand for the night", "rest on the sand tonight", "Comfy down here.", Driver.REST)
            n == "lightshy" -> Line("pulling its arms in because the light changed", "tuck its arms in", "Too bright!", Driver.FEAR, 2)
            n == "cavedart" -> Line("patrolling between its caves", "check on its caves", "All clear here.", Driver.TERRITORY)
            n == "nori" -> Line("nibbling the nori clip", "nibble the nori clip", "Nori! Yum!", Driver.HUNGER, 2)
            // ---- butterflyfish (research 60)
            n == "rove" -> Line(if (e.o.mateId != null) "roving the reef with its partner" else "roving along the rock looking for things to peck",
                "rove the reef", "So much to look at!", Driver.ROUTINE)
            n == "rockpeck" -> Line("pecking tiny critters off the rock", "peck at the rock", "Peck, peck, peck.", Driver.HUNGER)
            n == "duster" -> Line("pecking at ${tgt ?: "the feather duster"}'s fan (butterflies eat fanworms!)", "peck a feather duster",
                "Ooh, a tasty fan worm!", Driver.HUNGER, 3)
            n == "nipcoral" -> Line("nibbling at a coral (naughty!)", "nibble a coral", "Just a little taste…", Driver.HUNGER, 2)
            n == "cleansite" -> Line("cleaning a rock by ${e.host} to lay eggs on", "get a nest spot ready", "Making it spotless!", Driver.FAMILY, 3)
            n == "layeggs" -> Line("laying eggs by ${e.host}", "lay some eggs", "Eggs!", Driver.FAMILY, 3)
            n == "tendeggs" -> Line("fanning the eggs to keep them fresh", "look after the eggs", "Guarding my eggs!", Driver.FAMILY, 3)
            n == "hole" && k == BrainKind.SIXLINE && e.o.sleeping -> Line("asleep in the rock, wrapped in a bubble of its own slime", "sleep in its slime cocoon", "Snug as a bug in a mucus rug.", Driver.REST, 2)
            n == "hole" || n.startsWith("hole_") -> holeLine(e, label)
            // ---- common behaviours, with the species' own wording where it has one
            n == "flee" -> fleeLine(e, label)
            n == "sleep" -> sleepLine(e, label)
            n == "feed" -> feedLine(e, label)
            n == "graze" && k == BrainKind.EMERALD_CRAB -> Line("scooping bubble algae off the rock with its spoon claws", "scoop some bubble algae", "Pop, crunch!", Driver.HUNGER)
            n == "nip" && k == BrainKind.EMERALD_CRAB -> Line("nibbling coral polyps because it is hungry (naughty!)", "nibble a coral", "So hungry…", Driver.HUNGER, 2)
            n == "contract" && k == BrainKind.SEA_CUCUMBER -> Line("balled up into a firm, fat sausage${e.because()}", "stay tucked up", "Not touching me!", Driver.FEAR, 2)
            n == "hide" && k == BrainKind.SEA_CUCUMBER -> Line(if (label.startsWith("Creeping")) "creeping slowly back to its spot under the rock" else "lying with its head under the rock and its tail out on the sand", "wait for dark", if (label.startsWith("Creeping")) "Slow and steady." else "Too bright. Snooze.", Driver.REST)
            n == "sift" && k == BrainKind.SEA_CUCUMBER -> Line("creeping over the sand, mopping it clean with the little tentacles round its mouth", "clean more sand", "Mop, mop, yum.", Driver.HUNGER)
            n == "retreat" && k == BrainKind.PORCELAIN_CRAB -> Line("scuttling backwards under its anemone${e.because()}", "hide under the tentacles", "Back under the tentacles!", Driver.FEAR, 2)
            n == "filter" && k == BrainKind.PORCELAIN_CRAB -> Line(if (label.startsWith("Resting")) "resting its feathery fans for a moment" else "sweeping the water with its feathery fans to catch tiny bits of food", "catch more plankton", if (label.startsWith("Resting")) "Phew, arm break." else "Swish, swish, snack!", Driver.HUNGER)
            n == "spar" && k == BrainKind.PORCELAIN_CRAB -> Line("shoving a rival crab with its big flat claws", "keep its anemone", "This anemone is taken!", Driver.TERRITORY, 2)
            n == "retreat" && k == BrainKind.EMERALD_CRAB -> Line("scuttling back into its rock crevice${e.because()}", "hide in its crevice", "Eek, back inside!", Driver.FEAR, 2)
            n == "graze" && k == BrainKind.SALLY_CRAB -> Line("tearing at hair algae with its claws", "pick at some hair algae", "Munch munch!", Driver.HUNGER)
            n == "hunt" && k == BrainKind.ARROW_CRAB -> Line("stalking a bristleworm on long, slow legs", "catch a bristleworm", "Gotcha, wormy!", Driver.HUNGER, 2)
            n == "wormsnap" -> Line("snapping a bristleworm off the rock", "grab a bristleworm", "Worm snack!", Driver.HUNGER, 2)
            n == "probe" -> Line("reaching into a crevice for hiding worms", "check the crevices for worms", "Any worms in there?", Driver.HUNGER)
            n == "perch" -> Line("standing still as a stick under a ledge", "rest under a ledge", "Just chilling.", Driver.HOME, 0)
            n == "harass" -> Line("poking at a neighbour with its claws", "poke a neighbour", "Hey, move over!", Driver.HUNGER, 2)
            n == "graze" -> Line("grazing algae off the rock", "graze some algae", "Munch munch algae.", Driver.HUNGER)
            n == "firehover" -> Line("hovering over its hole, flicking its dorsal fin", "hover over its hole", "Flick flick.", Driver.HOME)
            n == "hole" && k == BrainKind.FIREFISH && label.startsWith("Darting") ->
                Line("darting head first into its hole${e.because()}", "pop back out of its hole", "Zoom, hide!", Driver.FEAR, 3)
            n == "shoal" -> Line("schooling with the other chromis", "swim with the school", "Stick together!", Driver.FAMILY)
            n == "peck" -> Line("chasing ${tgt ?: "a lower-ranked chromis"} to show who's boss", "boss the school around", "I'm in charge here.", Driver.TERRITORY, 3)
            n == "hover" && k == BrainKind.BOBTAIL_SQUID -> Line("hovering on its little fins, looking for a snack", "hover over the sand", "Anything tasty?", Driver.HUNGER, 2)
            n == "hover" && k == BrainKind.LEOPARD_WRASSE -> Line("hovering among the corals", "hover in the coral", "Nice and cozy.", Driver.ROUTINE)
            n == "hover" -> Line("hanging out near its favourite spot", "hang out near home", "Home sweet home.", Driver.HOME)
            n == "wander" -> Line("exploring the tank", "go exploring", "What's over here?", Driver.CURIOSITY)
            n == "scavenge" -> scavengeLine(e, label)
            n == "crawl" -> crawlLine(e, label)
            n == "glassgraze" -> Line("cleaning algae off the glass", "clean some glass", "Scrub scrub.", Driver.HUNGER)
            // ---- cleanup crew pack (research 43)
            n == "glassgraze" && k == BrainKind.URCHIN -> Line("rasping algae off the rock with its five little teeth", "graze the rock", "Crunch crunch.", Driver.HUNGER)
            n == "withdraw" -> Line("pulled into its shell until the coast is clear", "hide in its shell", "Nobody home!", Driver.FEAR, 2)
            n == "flipped" -> Line(if (label.startsWith("Flipped over, trying")) "on its back, reaching out with its foot to flip over" else "stuck on its back", "flip back over", "Little help? Almost…", Driver.HEALTH, 3)
            n == "forage" && k == BrainKind.CONCH -> Line("hopping across the sand on its pointy foot, grazing as it goes", "hop around the sand", "Boing! Yum, sand snacks.", Driver.HUNGER)
            n == "bury" && k == BrainKind.CONCH -> Line("buried in the sand with just its eyes poking out", "dig into the sand", "Peekaboo eyes.", Driver.REST, 0)
            n == "escape" && k == BrainKind.CONCH -> Line("leaping away as fast as a conch can", "leap away from danger", "Boing boing boing!", Driver.FEAR, 3)
            n == "hold" && k == BrainKind.URCHIN -> Line("clamped down with its spines pointed at you", "hold on tight", "Spiky, don't touch!", Driver.FEAR, 2)
            // ---- clownfish
            n == "host" -> Line(if (label.startsWith("Wiggling")) "wiggling in ${e.host}" else "hanging around ${e.host}, its home", "go home to ${e.host}", if (label.startsWith("Wiggling")) "Wiggle wiggle!" else "Home sweet home.", Driver.HOME)
            n == "anemone" && k == BrainKind.CLOWN -> Line(if (label.startsWith("Stung")) "zipping away from the anemone's sting" else "touching the new anemone to get used to it", "get used to the anemone", if (label.startsWith("Stung")) "Ouch, tingly!" else "Hello, new home?", Driver.HOME)
            n == "anemone" && k == BrainKind.ANEMONE -> anemoneLine(label)
            n == "feedanemone" -> Line("bringing a bite of food over to its anemone", "feed the anemone", "A snack for you!", Driver.HOME)
            n == "sortie" -> Line("swimming up over ${e.host} to snap at plankton", "catch some plankton", "Snap! Got one.", Driver.HUNGER)
            n == "nestle" -> Line("snuggled into ${e.host}", "snuggle into ${e.host}", "Cozy!", Driver.HOME)
            n == "defend" -> Line("defending ${e.host} from ${tgt ?: "an intruder"}", "guard ${e.host}", "Stay away from my home!", Driver.TERRITORY, 3)
            // ---- tang
            n == "patrol" -> Line("swimming laps around the tank", "swim some laps", "Just keep swimming!", Driver.ROUTINE)
            n == "columnpick" -> Line("picking tiny plankton out of the water", "pick at plankton", "Tiny snacks!", Driver.HUNGER)
            n == "playdead" -> Line("lying on its side, playing dead (tangs do that!)", "play dead for a bit", "Shh, I'm asleep.", Driver.REST, 3)
            n == "tanghide" -> Line("tucked under the rock until it feels safe again", "hide under the rock", "Nobody can see me here.", Driver.FEAR, 2)
            n == "tangrub" -> Line("rubbing its side along the rock, just for fun", "give the rock a rub", "Wheee, scratchy!", Driver.CURIOSITY, 2)
            n == "tangzoom" -> Line("racing across the tank as fast as it can", "race across the tank", "Zoom zoom!", Driver.CURIOSITY, 2)
            n == "tangspawn" -> Line("chasing its mate in loops, then dashing up to spawn", "court its mate", "Dance with me!", Driver.CURIOSITY, 2)
            // ---- royal gramma and watchman goby (research 50)
            n == "gramhover" -> Line(if (e.o.up.y < -0.3) "hanging upside down under its ledge (grammas do that!)" else "hanging belly-to-the-rock by its cave",
                "hang by its cave", if (e.o.up.y < -0.3) "The world looks better this way." else "My cave, my rules.", Driver.HOME)
            n == "gape" -> Line("opening its mouth wide at ${tgt ?: "an intruder"} to scare it off", "guard its cave", "Back off, this is my cave!", Driver.TERRITORY, 3)
            n == "nest" -> Line("carrying bits of algae into its cave to build a nest", "build a nest", "Making it cozy for the eggs!", Driver.FAMILY, 3)
            n == "lookout" -> Line("propped up at its burrow, keeping watch", "keep watch at its burrow", "I'll keep an eye out.", Driver.HOME)
            n == "dive" -> Line("flicking its tail and diving into its burrow${e.because()}", "pop back out of its burrow", "Danger! Everyone in!", Driver.FEAR, 3)
            n == "spit" -> Line("spitting a mouthful of sand out of its burrow", "tidy its burrow", "Ptooey!", Driver.HOME, 2)
            // ---- mandarin, six line wrasse and clown goby (research 51)
            n == "podhop" -> Line("hopping along the rock, pecking up tiny pods", "hunt for pods", "Copepod! Another one! Yum.", Driver.HUNGER)
            n == "spawn" && k == BrainKind.MANDARIN -> Line(if (e.o.dorsalRaise > 0.5) "raising its tall fin and dancing for its mate" else "rising to the surface with its mate to spawn",
                "dance with its mate", "Up we go, together!", Driver.FAMILY, 3)
            n == "spawnf" -> Line("rising up the tank with its mate in a slow spiral", "rise with its mate", "Ooh, a dance!", Driver.FAMILY, 3)
            n == "dart" && k == BrainKind.SIXLINE -> Line("darting from crack to crack, hunting tiny critters", "hunt in the rock", "What's in here? And here?", Driver.HUNGER)
            n == "harass" -> Line("chasing ${tgt ?: "a newcomer"} off its patch", "boss the reef", "My reef! I was here first!", Driver.TERRITORY, 3)
            n == "cperch" -> Line(if (e.o.sleeping) "asleep tucked in its coral" else "perched in its coral, watching", "sit in its coral", "Best seat in the reef.", Driver.HOME)
            n == "squabble" -> Line("squabbling with ${tgt ?: "another clown goby"} over the coral", "sort out who owns the coral", "Move over! …Okay, maybe we can share.", Driver.TERRITORY, 2)
            n == "hovercover" && k == BrainKind.CARDINAL && e.o.mouthbrooding ->
                Line("carrying its eggs in its mouth (dads do that!)", "keep the eggs safe in its mouth", "Mmmf. Can't talk, eggs.", Driver.FAMILY, 3)
            // ---- cardinal
            n == "hovercover" -> Line("hovering in its favourite shady spot", "hover in the shade", "Nice and shady.", Driver.HOME)
            n == "dusk" -> Line("out hunting tiny critters in the dark", "hunt for tiny critters tonight", "Night snacks!", Driver.HUNGER, 2)
            // ---- cleaner wrasse
            n == "station" && k == BrainKind.WRASSE -> Line(if (label.startsWith("Dancing")) "dancing to invite a fish to get cleaned" else "waiting at its cleaning station for customers", "open the cleaning station", "Cleaning, anyone?", Driver.CLEANER_JOB)
            n == "work" -> Line(if (client != null) "cleaning ${possessive(client)} ${e.part()}" else "looking for a fish to clean", "clean a fish", "Cleaning time!", Driver.CLEANER_JOB, 3)
            n == "surf" -> Line("checking the rocks for customers", "look for fish to clean", "Who needs a clean?", Driver.CLEANER_JOB)
            n == "cocoon" -> Line("sleeping in a slimy bubble blanket", "make its slimy sleeping bag", "Zzz…", Driver.REST, 0)
            // ---- fairy wrasse
            n == "plankton" -> Line("hanging in the current, picking plankton out of the water", "pick plankton in the current", "Snap! Snap!", Driver.HUNGER)
            n == "harem" -> Line(if (tgt != null) "swimming back to stay near $tgt" else "staying close to its group", "stay with the group", "Wait for me!", Driver.HOME)
            n == "flashing" -> Line(if (tgt != null) "flashing his bright fins to show off for $tgt" else "flashing his bright fins to show off", "show off his fins", "Look at me!", Driver.FAMILY, 3)
            n == "rush" -> Line("dashing up with a partner in a spawning rush", "dance up with a partner", "Up we go!", Driver.FAMILY, 3)
            // ---- moray
            n == "headout" -> Line("poking its head out of its den", "peek out of the den", "Peekaboo.", Driver.HOME)
            n == "roam" -> Line("roaming over the rocks", "roam the rocks", "Out for a slither.", Driver.CURIOSITY, 2)
            n == "hunt" && k == BrainKind.BOBTAIL_SQUID -> Line(squidHunt(label, tgt), "hunt a tiny shrimp", "Sneaky sneaky…", Driver.HUNGER, 3)
            n == "hunt" -> Line(if (tgt != null) "sneaking up on $tgt" else "hunting for a snack", "go hunting", "Sneaky sneaky…", Driver.HUNGER, 3)
            // ---- hawkfish
            n == "perch" -> Line(if (tgt != null) "perched on a rock, watching $tgt" else "perched on a rock, watching everything", "hop to a new lookout", "I see you…", Driver.CURIOSITY)
            n == "pounce" -> Line("pouncing on ${tgt ?: "a snack"}", "pounce on a snack", "Gotcha!", Driver.HUNGER, 3)
            n == "chase" -> Line("chasing ${tgt ?: "an intruder"} off its perch", "chase intruders away", "My rock!", Driver.TERRITORY, 3)
            // ---- blennies and dottyback
            n == "rockgraze" -> Line("mowing algae off the rocks", "mow some algae", "Mmm, algae!", Driver.HUNGER)
            n == "rival" -> Line("chasing ${tgt ?: "a rival"} out of its patch", "chase off a rival", "Go away!", Driver.TERRITORY, 3)
            n == "dash" -> Line("darting out to grab plankton", "dart out for plankton", "Snack!", Driver.HUNGER, 2)
            n == "perchhole" -> Line(if (tgt != null) "sitting by its hole, watching $tgt" else "sitting by its hole, looking around", "sit by its hole", "Just watching.", Driver.HOME)
            n == "prospect" -> Line("exploring its new home", "check out its new home", "Ooh, new place!", Driver.CURIOSITY, 2)
            n == "midwater" -> Line(if (tgt != null) "swimming up in the open water beside $tgt, picking plankton" else "swimming up in the open water, picking plankton",
                "swim up for plankton", "So many tiny snacks!", Driver.HUNGER, 2)
            n == "holefight" -> Line("fighting for a hole to call home", "fight for a hole", "That hole should be mine!", Driver.TERRITORY, 3)
            n == "cavedart" -> Line("darting between little caves", "dart between the caves", "Zip zip!", Driver.ROUTINE)
            n == "territory" -> Line("guarding its territory from ${tgt ?: "intruders"}", "guard its territory", "My cave!", Driver.TERRITORY, 3)
            // ---- engineer goby
            n == "burrow" -> Line(if (e.night) "asleep in its burrow" else "tucked away safe in its burrow", "rest in its burrow", if (e.night) "Zzz…" else "Safe and snug.", if (e.night) Driver.REST else Driver.HOME, 0)
            n == "mouthhover" && label.startsWith("Picking") -> Line("swimming up over its burrow to snap at plankton", "catch some plankton", "Snap! Tiny snack.", Driver.HUNGER)
            n == "mouthhover" -> Line("hovering at its burrow door", "hang out by its burrow", "Home sweet home.", Driver.HOME)
            n == "dig" -> Line(when (k) { BrainKind.DIAMOND_GOBY -> "digging a cozy tunnel under a rock"; BrainKind.MANTIS -> "hauling sand out of its burrow in its arms"; else -> "carrying sand out of its burrow" }, "dig some more", "Dig dig dig!", Driver.HOME, 2)
            n == "nightpatrol" -> Line("patrolling around at night", "patrol in the dark", "Night walk!", Driver.CURIOSITY)
            // ---- coral banded shrimp
            n == "hang" -> Line(if (label.startsWith("Grooming")) "cleaning its long antennae" else "hanging upside down under its ledge", "hang under its ledge", if (label.startsWith("Grooming")) "Neat antennae!" else "Upside down is fun.", Driver.HOME)
            n == "clean" -> Line("picking ${client ?: "a fish"} clean", "clean a fish", "Hold still!", Driver.CLEANER_JOB, 3)
            // ---- cleaner shrimp
            n == "station" -> Line(if (label.startsWith("Grooming")) "cleaning its own antennae" else "waving its antennae to invite fish to get cleaned", "wave for customers", "Cleaning, anyone?", Driver.CLEANER_JOB)
            n == "climb" -> Line("climbing on ${client ?: "a fish"} to clean it", "climb on a fish to clean it", "Hold still!", Driver.CLEANER_JOB, 3)
            // ---- hermit crab
            n == "pick" && k == BrainKind.LEOPARD_WRASSE -> Line(if (label.contains("sand")) "picking tiny critters off the sand" else "picking tiny pods off the coral", "pick pods off the coral", "Pod snack!", Driver.HUNGER)
            n == "pick" -> Line("picking bits of food off the rock", "pick at the rock", "Pick pick.", Driver.HUNGER)
            n == "retreat" -> Line("hiding inside its shell${e.because()}", "pop back out of its shell", "Nobody home!", Driver.FEAR, 2)
            n == "backout" -> Line("backing out of a gap too tight for its shell", "back out of the tight spot", "Too squeezy!", Driver.ROUTINE)
            // ---- brittle star
            // ---- peppermint shrimp and flame scallop (research 42)
            n == "hide" && k == BrainKind.PEPPERMINT_SHRIMP -> Line(when {
                label.startsWith("Grooming") -> "cleaning its antennae in its hiding spot"
                label.contains("back") -> "heading back to its hiding spot in the rock"
                else -> "hiding in a dark gap in the rock until night"
            }, "come out after dark", "Too bright out there.", Driver.HOME, 0)
            n == "aiptasia" -> Line(if (label.startsWith("Eating")) "picking apart a pest aiptasia anemone" else "sneaking up on a pest aiptasia anemone",
                "hunt aiptasia", "Aiptasia, my favourite!", Driver.HUNGER, 2)
            n == "clam" -> clamLine(label)
            n == "giantclam" -> giantClamLine(label)
            n == "fishgrab" -> Line("wrapping an arm round a sleeping fish", "eat it", "Gotcha!", Driver.HUNGER, 3)
            // ---- shrimp pack (research 44)
            n == "dance" -> Line("swaying its tail on ${if (label.contains("anemone")) "its anemone" else if (label.contains("coral")) "its coral" else "the rock"}",
                "keep dancing", "Wiggle wiggle!", Driver.HOME, 1)
            n == "hostpick" -> Line("picking slime and tiny food off its ${if (label.contains("anemone")) "anemone" else "coral"}", "pick at its host", "Snack time.", Driver.HUNGER)
            n == "hide" && k == BrainKind.HARLEQUIN_SHRIMP -> Line("tucked under a rock, waving its big flat claws", "come out to hunt stars", "Too bright out there.", Driver.HOME, 0)
            n == "starhunt" -> Line("creeping up on ${tgt ?: "a starfish"}", "flip the starfish over", "A star! Dinner!", Driver.HUNGER, 3)
            n == "stareat" -> Line("eating a starfish it flipped onto its back", "finish its starfish", "Nom, starfish.", Driver.HUNGER, 3)
            n == "starshare" -> Line("sharing its mate's starfish", "share the starfish", "Save some for me!", Driver.FAMILY, 2)
            n == "bulldoze" -> Line("bulldozing sand out of its burrow with its claws", "dig some more", "Dig, dig, push!", Driver.HOME, 2)
            n == "duck" -> Line("diving down its hole${e.because()}", "peek out again", "Into the hole!", Driver.FEAR, 2)
            // ---- peacock mantis (research 62 §2)
            n == "watch" -> Line("watching from its burrow, its big eyes swivelling", "wait for something to walk by", "I see everything.", Driver.HOME, 1)
            n == "smash" -> Line("smashing ${tgt ?: "a shell"} with its clubs, fast as a bullet", "crack the shell open", "Bam! Bam! Bam!", Driver.HUNGER, 3)
            n == "eatprey" -> Line(if (label.startsWith("Eating")) "eating its catch at the burrow" else "carrying its catch home to its burrow", "finish its meal", "Mine, all mine.", Driver.HUNGER, 2)
            n == "clawsnap" -> Line("snapping its big claw with a loud crack", "guard its burrow", "Crack! Back off!", Driver.TERRITORY, 3)
            n == "hide" && k == BrainKind.EMERALD_CRAB -> Line("wedged into its rock crevice for the day", "hide in its crevice", "Too bright out there.", Driver.HOME, 0)
            n == "hide" && k == BrainKind.SALLY_CRAB -> Line("tucked into its rock crevice", "hide in its crevice", "Can't see me!", Driver.HOME, 0)
            n == "squeeze" -> Line("squeezing out of a gap too tight for its shell", "squeeze out of the gap", "Too tight in here!", Driver.HOME, 2)
            n == "hide" -> Line("hiding under a rock with just its arms peeking out", "hide under the rock", "Shh, hiding.", Driver.HOME, 0)
            n == "armfeed" -> Line("passing food to its mouth with an arm", "pass food to its mouth", "Yum, arm delivery!", Driver.HUNGER, 2)
            n == "retract" -> Line("pulling its arms in and creeping away${e.because()}", "pull its arms in", "Eek!", Driver.FEAR, 3)
            // ---- feather duster
            n == "filter" -> Line("catching tiny food with its feathery crown", "open its feathery crown", "Catching snacks!", Driver.HUNGER)
            n == "snap" -> Line("pulled into its tube${e.because()}", "pop its crown back out", "Hide!", Driver.FEAR, 2)
            n == "abandoned" -> Line("has left its tube to find a better home", "build a new tube", "New house, please.", Driver.WATER, 3)
            // ---- nassarius
            n == "buried" -> Line("buried in the sand with its snorkel up, sniffing for food", "sniff for food", "Sniff sniff…", Driver.HUNGER, 0)
            n == "erupt" -> Line("popping out of the sand to race to food", "race to food", "Food! Food!", Driver.HUNGER, 3)
            n == "feedbout" -> Line("slurping food with its long snout", "slurp up some food", "Slurp!", Driver.HUNGER, 2)
            // ---- snails and stars
            n == "flipped" -> Line("upside down and trying to flip back over", "flip back over", "Help, I'm upside down!", Driver.HEALTH, 3)
            n == "crw" -> Line("slowly grazing the film on the rock and glass", "graze the film", "Slow and steady.", Driver.HUNGER, 0)
            // ---- Sally Lightfoot crab
            n == "carcass" -> Line(if (tgt != null) "picking at $tgt" else "cleaning up a dead animal", "clean up leftovers", "Leftovers!", Driver.HUNGER, 2)
            n == "dart" -> Line("dashing for cover${e.because()}", "dash for cover", "Eek!", Driver.FEAR, 3)
            n == "grab" -> Line("grabbing at ${tgt ?: "a snack"}", "grab a snack", "Got you!", Driver.HUNGER, 3)
            // ---- diamond goby
            n == "sandrest" -> Line("resting on the sand between sifting", "have a little rest", "Phew, break time.", Driver.ROUTINE)
            n == "sift" -> Line(siftNow(label), "sift the sand for critters", "Sifting sand…", Driver.HUNGER)
            // ---- leopard wrasse
            n == "sand" -> Line(sandNow(e, label), "dive into the sand to sleep", if (e.night) "Zzz…" else "Hiding!", if (e.night) Driver.REST else Driver.FEAR, if (label.startsWith("Diving") || label.startsWith("Pushing")) 3 else 0)
            // ---- bobtail squid
            n == "bury" -> Line(buryNow(label), "bury itself in the sand", "Hiding in the sand.", Driver.REST, if (label.startsWith("Burying")) 3 else 0)
            n == "jet" -> Line(if (label.startsWith("Inking")) "squirting ink and jetting away${e.because()}" else "hiding quietly after a scare", "jet away from danger", "Ink cloud!", Driver.FEAR, 3)
            else -> null
        }
    }

    private fun touchLine(e: Env): Line = when (e.brain.touchKind) {
        TouchReaction.INVESTIGATE -> Line("swimming over to see what tapped the tank", "check out the tap", "What was that?", Driver.CURIOSITY, 3)
        TouchReaction.AVOID -> Line("turning around and swimming away from the tap", "keep away from the tap", "Nope!", Driver.FEAR, 3)
        TouchReaction.PEEK -> Line("peeking out to see who tapped", "peek at the tap", "Who's there?", Driver.CURIOSITY, 3)
        TouchReaction.FREEZE -> Line("freezing very still after the tap", "freeze until it is safe", "Shh…", Driver.FEAR, 3)
        TouchReaction.CHECK -> Line("checking out the little sand puff", "check the sand puff", "Something smells good?", Driver.CURIOSITY, 3)
        else -> Line("noticing the tap on the tank", "check out the tap", "Huh?", Driver.CURIOSITY, 2)
    }

    private fun subLine(b: SubmarineReact): Line = when (b.reaction) {
        com.reeflive.core.sim.SubReaction.INVESTIGATE -> Line("swimming over to look at the little submarine", "follow the submarine", "Ooh, what's that?", Driver.CURIOSITY, 3)
        com.reeflive.core.sim.SubReaction.POSTURE -> if (b.guarding) Line("guarding its home from the submarine", "chase the submarine off", "Go away, this is mine!", Driver.TERRITORY, 3)
            else Line("watching the submarine closely", "keep an eye on the submarine", "I see you…", Driver.CURIOSITY, 3)
        com.reeflive.core.sim.SubReaction.AVOID -> Line("swimming away from the submarine", "keep away from the submarine", "Too big! Too close!", Driver.FEAR, 3)
        com.reeflive.core.sim.SubReaction.FREEZE -> Line("freezing very still while the submarine goes by", "freeze until it is gone", "Shh…", Driver.FEAR, 3)
        else -> Line("noticing the little submarine", "watch the submarine", "Huh? A submarine?", Driver.CURIOSITY, 2)
    }

    private fun boutLine(b: Behavior, tgt: String?): Line {
        val who = tgt ?: "a neighbour"
        return when (b.targetVerb) {
            "asserting itself over" -> Line("showing $who who's the boss", "show the neighbours who's the boss", "I'm the boss!", Driver.TERRITORY, 3)
            "squaring up to" -> Line("having a staring contest with $who", "stare down a neighbour", "Oh yeah?", Driver.TERRITORY, 3)
            "driving off" -> Line("driving $who away from its spot", "drive a neighbour off", "My spot!", Driver.TERRITORY, 3)
            "eyeing" -> Line("giving $who a grumpy look", "give a neighbour a look", "Hmph.", Driver.TERRITORY, 2)
            else -> Line("chasing $who", "chase a neighbour", "Shoo!", Driver.TERRITORY, 3)
        }
    }

    private fun heatNow(e: Env, label: String): String = when {
        label.startsWith("Climbing") -> "climbing up high because the water is too warm"
        label.startsWith("Lost its grip") || label.startsWith("Fallen") -> "too hot to hold on to the rock"
        label.startsWith("Motionless") || label.startsWith("Lethargic") -> "lying still because the water is too warm"
        label.startsWith("Lying") || label.startsWith("Loss") -> "too hot to swim straight"
        label.startsWith("Breathing") -> "breathing hard because the water is too warm"
        else -> "restless because the water is too warm"
    }

    private fun holeLine(e: Env, label: String): Line = when {
        label.contains("cave") -> if (e.night) Line("sleeping in its cave", "rest in its cave", "Zzz…", Driver.REST, 0)
            else Line("tucked into its cave with its head at the door", "rest in its cave", "My cave.", Driver.HOME, 0)
        e.night -> Line("sleeping in its hole", "rest in its hole", "Zzz…", Driver.REST, 0)
        label.startsWith("Backing") -> Line("backing into its hole tail first", "settle into its hole", "Reverse in…", Driver.HOME, 1)
        e.brain.threat != null || e.sim.simTimeSec - e.brain.startleAt < 20 ->
            Line("backing into its hole${e.because()}", "pop back out of its hole", "Eek! Hide!", Driver.FEAR, 3)
        label.startsWith("Peeking") -> Line("peeking out of its hole", "peek out of its hole", "Peekaboo!", Driver.CURIOSITY)
        else -> Line("resting snugly in its hole", "rest in its hole", "Comfy hole.", Driver.HOME, 0)
    }

    private fun fleeLine(e: Env, label: String): Line {
        val why = e.because()
        val now = when {
            label == "Darting into the host" -> "diving into ${e.host}$why"
            label == "Darting to cover" -> "darting behind the rocks$why"
            label == "Retreating" -> "slipping back into the shadows$why"
            label == "Fleeing" -> "swimming away fast$why"
            label == "Retreating to den" -> "pulling back into its den$why"
            label == "Ducking behind rock" -> "ducking behind a big rock$why"
            label == "Darting home" -> "darting back into its hole$why"
            label == "Darting into its cave" -> "zipping into its cave$why"
            label == "Darting into its burrow" -> "backing into its burrow$why"
            label == "Diving into its burrow" -> "diving head-first into its burrow$why"
            label == "Darting away" -> "zooming away to safety$why"
            else -> "darting away to hide$why"
        }
        return Line(now, "hide somewhere safe", "Eek! Hide!", Driver.FEAR, 3)
    }

    private fun sleepLine(e: Env, label: String): Line {
        val o = e.o
        val where = when {
            label == "Resting at the cave" -> " by the cave"
            label == "Resting in its cave" -> " in its cave"
            o.brainKind == BrainKind.TANG -> " in a rocky nook"
            else -> ""
        }
        val nocturnal = o.species.activity.equals("NOCTURNAL", true)
        val now = when {
            !e.night && !nocturnal && o.brainKind == BrainKind.CLOWN -> "heading home to ${e.host} to rest"
            !e.night && !nocturnal -> "settling down for a rest$where"
            else -> "sleeping peacefully$where"
        }
        return Line(now, "find a cozy spot to sleep", "Zzz…", Driver.REST, 0)
    }

    private fun feedLine(e: Env, label: String): Line {
        val food = e.foodWord
        val now = when {
            label == "Picking plankton" -> "picking tiny plankton out of the water"
            label == "Picking food off the sand" -> "picking ${food ?: "bits of food"} off the sand"
            label == "Picking food from the water" -> "picking ${food ?: "bits of food"} out of the water"
            label == "Snatching food with its tentacles" -> "snatching ${food ?: "a snack"} with its tentacles"
            food != null -> "chasing down some $food"
            e.o.isFish -> "hungry and watching the surface for food"
            else -> "looking for food"
        }
        val bubble = if (food != null) "Yum, $food!" else "Food? Food!"
        return Line(now, "grab some food", bubble, Driver.HUNGER, if (food != null) 3 else 2)
    }

    private fun scavengeLine(e: Env, label: String): Line {
        val food = e.foodWord
        val now = when {
            label == "Racing to food" -> "racing to ${food ?: "food"} on its long arms"
            label == "Grabbing food" -> "grabbing ${food ?: "a bit of food"}"
            food != null -> "scavenging some $food"
            else -> "scavenging for leftovers"
        }
        return Line(now, "look for leftovers", if (food != null) "Yum, $food!" else "Leftovers?", Driver.HUNGER, if (food != null) 3 else 1)
    }

    private fun clamLine(label: String): Line = when {
        label.startsWith("Swimming") -> Line("clapping its shell to swim away", "find a quieter crevice", "Clap clap clap!", Driver.FEAR, 3)
        label.startsWith("Looking") -> Line("drifting down to look for a new crevice", "find a new crevice", "Somewhere darker…", Driver.HOME, 2)
        label.startsWith("Resting") -> Line("resting where it landed", "creep into a crevice", "Phew.", Driver.HOME)
        label.startsWith("Snapped") -> Line("snapped its shell shut", "open up again", "Eek!", Driver.FEAR, 2)
        label.startsWith("Flushing") -> Line("clapping its shell to flush out its gills", "keep filtering", "Ahem!", Driver.ROUTINE)
        label.startsWith("Starving") -> Line("filtering hard, but there is too little phytoplankton to eat", "eat phytoplankton (dose some!)", "So hungry…", Driver.HUNGER, 3)
        label.startsWith("Hungry") -> Line("filtering the water for phytoplankton", "eat more phytoplankton", "More phyto, please.", Driver.HUNGER, 2)
        else -> Line("wedged in its crevice, waving its red tentacles and filtering the water", "keep filtering", "Mmm, plankton.", Driver.HUNGER)
    }

    private fun giantClamLine(label: String): Line = when {
        label.startsWith("Snapped") -> Line("snapped its shell shut with a squirt of water", "open up again", "Who's there?!", Driver.FEAR, 2)
        label.startsWith("Opening") -> Line("slowly spreading its mantle out again", "soak up the light", "Is it safe now?", Driver.HOME)
        label.startsWith("Bleached") -> Line("pale and bleached: it has lost the algae that feed it", "get its colour back (steady light, cooler water)", "I feel so empty…", Driver.HUNGER, 3)
        label.startsWith("Paling") -> Line("losing colour as its algae leave", "keep its colour (check heat and light)", "Something's not right…", Driver.HUNGER, 2)
        label.contains("too little light") -> Line("spreading its mantle wide to catch what little light reaches it", "find more light (move it higher)", "More light, please!", Driver.HUNGER, 2)
        label.contains("night") -> Line("resting with its mantle half drawn in for the night", "wait for the lights", "Zzz…", Driver.ROUTINE)
        label.startsWith("Hungry") -> Line("soaking up the light to feed the algae in its mantle", "soak up more light", "Sun, please!", Driver.HUNGER)
        else -> Line("spreading its colourful mantle in the light, letting the algae in it make food", "keep soaking up the light", "Ahh, sunshine.", Driver.ROUTINE)
    }

    private fun crawlLine(e: Env, label: String): Line = when (label) {
        "Foraging" -> Line("out foraging in the dark", "go foraging tonight", "Night snacks!", Driver.HUNGER)
        "Roaming" -> Line("creeping around the rocks", "creep around", "Creep creep.", Driver.CURIOSITY)
        "Roaming the sand" -> Line("creeping across the sand", "creep across the sand", "Sniff sniff.", Driver.HUNGER)
        else -> Line("wandering around", "go for a wander", "La la la.", Driver.CURIOSITY)
    }

    private fun squidHunt(label: String, tgt: String?): String = when {
        label.startsWith("Arms") -> "raising its arms, about to strike"
        label.startsWith("Shooting") -> "zapping out its long tentacles"
        label.startsWith("Eating") -> "eating what it caught"
        label.startsWith("Missed") -> "sulking after a miss"
        tgt != null -> "stalking $tgt"
        else -> "stalking tiny prey"
    }

    private fun siftNow(label: String): String = when {
        label.startsWith("Moving") -> "swimming to a new patch of sand"
        label.startsWith("Taking") -> "taking a big mouthful of sand"
        label.startsWith("Sifting") -> "sifting sand through its gills for tiny critters"
        else -> "puffing sand out of its gills"
    }

    private fun sandNow(e: Env, label: String): String = when {
        label.startsWith("Heading") -> "heading for the sand to bury itself"
        label.startsWith("Diving") -> "diving head-first into the sand"
        label.startsWith("Asleep") -> "fast asleep under the sand"
        label.startsWith("Hiding") -> "lying low under the sand${e.because()}"
        else -> "pushing up out of the sand"
    }

    private fun buryNow(label: String): String = when {
        label.startsWith("Heading") -> "swimming down to the sand"
        label.startsWith("Burying") -> "blowing sand over itself to hide"
        else -> "buried in the sand with just its eyes out"
    }

    private fun fallback(e: Env): Line = Line("busy doing its own thing", "", "Hmm…", Driver.ROUTINE)

    // ------------------------------------------------------------------ next plan

    /** The highest-scoring other behaviour (sticky: the previous pick stays unless clearly beaten). */
    private fun runnerUp(e: Env, ub: UtilityBrain, cur: Behavior, prev: Int): Pair<Int, Line?> {
        var best = -1
        var bestScore = MIN_NEXT_SCORE
        val curLine = line(e, cur)?.next
        for ((i, b) in ub.behaviors.withIndex()) {
            if (b === cur || b.name in NO_NEXT) continue
            val s = ub.lastScore(i)
            if (s > bestScore) { bestScore = s; best = i }
        }
        if (prev in ub.behaviors.indices && prev != best && ub.behaviors[prev] !== cur && ub.lastScore(prev) >= max(MIN_NEXT_SCORE, bestScore - NEXT_STICKY)) best = prev
        if (best < 0) return -1 to null
        val l = line(e, ub.behaviors[best]) ?: return -1 to null
        if (l.next.isBlank() || l.next == curLine) return best to null
        return best to l
    }

    /** A time-of-day plan when no other behaviour is close: bedtime soon, or waking up. */
    private fun timePlan(e: Env, cur: Behavior): String? {
        val o = e.o
        if (!o.isFish && o.brainKind != BrainKind.BOBTAIL_SQUID) return null
        val nocturnal = o.species.activity.equals("NOCTURNAL", true)
        val resting = cur.name in RESTING || o.sleeping
        val untilOff = e.sim.minutesUntilLightsOff
        return when {
            nocturnal && !e.night && resting -> "come out when it gets dark"
            nocturnal -> null
            e.night && resting -> "wake up when the lights come on"
            !e.night && untilOff != null && untilOff < 45 -> "find a cozy spot before the lights go out"
            else -> null
        }
    }

    // ------------------------------------------------------------------ feeling and why

    private fun feeling(e: Env, cur: Behavior, d: Driver): Pair<String, String> {
        val o = e.o
        val water = waterReason(e)
        return when {
            o.heat.stage != HeatModel.Stage.NORMAL -> "too hot" to "the water is too warm"
            o.disease != null && o.diseaseStage > 0.15 || o.health < 0.5 -> "unwell" to (o.disease?.let { "it might be sick" } ?: "it needs a rest")
            d == Driver.FEAR -> "scared" to (e.threatName?.let { "$it is close" } ?: e.waryName?.let { "$it is nearby" } ?: "")
            o.stress > 0.5 -> "stressed" to (water ?: "")
            o.sleeping || d == Driver.REST && e.night -> "sleepy" to ""
            o.hunger > HUNGRY -> "hungry" to "its tummy is rumbling"
            d == Driver.TERRITORY -> "feisty" to ""
            d == Driver.FAMILY -> "proud" to "eggs on the way"
            d == Driver.CLEANER_JOB -> "busy" to ""
            d == Driver.CURIOSITY -> "curious" to ""
            o.stress > 0.3 && water != null -> "a bit stressed" to water
            o.stress > 0.3 -> "a bit uneasy" to ""
            o.hunger > 0.45 -> "a little peckish" to ""
            o.stress < 0.1 && o.hunger < 0.3 -> "happy" to ""
            else -> "relaxed" to ""
        }
    }

    /** What in the water bothers it most, in plain words (null = nothing much). */
    private fun waterReason(e: Env): String? {
        val o = e.o
        var key: String? = null
        var top = 0.15
        for ((k, v) in o.envStress) if (v > top) { top = v; key = k }
        val temp = e.sim.water.s.tempC
        return when (key ?: return null) {
            "temp" -> if (temp >= 26.0) "water is warm" else "water is cool"
            "shockTemp" -> "the temperature changed fast"
            "sg", "shockSg" -> "the salt level is off"
            "ph", "shockPh" -> "the water is not quite right"
            "nh3Free" -> "there is ammonia in the water"
            "no2" -> "there is nitrite in the water"
            "no3" -> "too much nitrate"
            "do" -> "not enough air in the water"
            "copper" -> "copper in the water"
            "light" -> "the lights are very bright"
            "flow" -> "the water flow changed"
            "social" -> "someone keeps bothering it"
            else -> "the water is not quite right"
        }
    }

    private fun whyOf(e: Env, d: Driver): String = when (d) {
        Driver.HUNGER -> if (e.o.hunger > HUNGRY) "it is hungry" else "snack time"
        Driver.FEAR -> e.threatName?.let { "$it is close" } ?: e.waryName?.let { "keeping away from $it" } ?: "something startled it"
        Driver.CURIOSITY -> "it is curious"
        Driver.TERRITORY -> "guarding its space"
        Driver.FAMILY -> "starting a family"
        Driver.HOME -> "home feels safe"
        Driver.CLEANER_JOB -> "cleaning is its job"
        Driver.GET_CLEANED -> "it wants to get cleaned"
        Driver.REST -> if (e.night) "it is bedtime" else "rest time"
        Driver.WATER -> waterReason(e) ?: "the water is not right"
        Driver.HEALTH -> "it does not feel well"
        Driver.GROWING -> "it is growing"
        Driver.ROUTINE -> "just being itself"
    }

    // ------------------------------------------------------------------ names

    /** The animal's name for a sentence: its nickname, else "the cleaner wrasse". */
    fun nameOf(o: Organism): String = o.nickname.ifBlank { "the " + kindWord(o) }

    /** How another animal is called: "Blue the tang", "Marlin", "Hermit 2", "the hawkfish". */
    fun refer(me: Organism, t: Organism): String {
        val kind = kindWord(t)
        val nick = t.nickname
        if (nick.isBlank()) return "the $kind"
        if (t.species.id == me.species.id) return nick
        // "Hermit 2", "Brittle", "Mr. Crabs": the nickname already says what it is; personal names ("Dori") get the kind added
        val n = nick.lowercase()
        val named = kind.lowercase().split(' ').any { w -> w.length >= 4 && (n.contains(w) || n.length >= 4 && w.contains(n)) }
        return if (named) nick else "$nick the $kind"
    }

    /** A kid-friendly short species word. */
    fun kindWord(o: Organism): String = when (o.brainKind) {
        BrainKind.CLOWN -> "clownfish"
        BrainKind.TANG -> "tang"
        BrainKind.CARDINAL -> if (o.species.id.startsWith("pterapogon")) "Banggai cardinal" else "cardinalfish"
        BrainKind.WRASSE -> "cleaner wrasse"
        BrainKind.EEL -> "moray eel"
        BrainKind.ENGINEER_GOBY -> "engineer goby"
        BrainKind.HAWK -> "hawkfish"
        BrainKind.LAWNMOWER_BLENNY -> "lawnmower blenny"
        BrainKind.PEEKER_BLENNY -> "blenny"
        BrainKind.DOTTYBACK -> "dottyback"
        BrainKind.BANDED_SHRIMP -> "banded shrimp"
        BrainKind.PEPPERMINT_SHRIMP -> "peppermint shrimp"
        BrainKind.FIRE_SHRIMP -> "fire shrimp"
        BrainKind.SEXY_SHRIMP -> "sexy shrimp"
        BrainKind.HARLEQUIN_SHRIMP -> "harlequin shrimp"
        BrainKind.PISTOL_SHRIMP -> "pistol shrimp"
        BrainKind.MANTIS -> "mantis shrimp"
        BrainKind.FILE_CLAM -> "flame scallop"
        BrainKind.GIANT_CLAM -> "maxima clam"
        BrainKind.SEA_CUCUMBER -> "sea cucumber"
        BrainKind.CLEANER_SHRIMP -> "cleaner shrimp"
        BrainKind.HERMIT -> "hermit crab"
        BrainKind.SALLY_CRAB -> "Sally Lightfoot crab"
        BrainKind.ARROW_CRAB -> "arrow crab"
        BrainKind.EMERALD_CRAB -> "emerald crab"
        BrainKind.PORCELAIN_CRAB -> "porcelain crab"
        BrainKind.BRITTLE_STAR -> if (o.species.brain == "serpent_star") "serpent star" else "brittle star"
        BrainKind.FEATHER_DUSTER -> "feather duster worm"
        BrainKind.NASSARIUS -> "nassarius snail"
        BrainKind.ASTREA -> "astrea snail"
        BrainKind.TROCHUS -> "trochus snail"
        BrainKind.CONCH -> "fighting conch"
        BrainKind.URCHIN -> "tuxedo urchin"
        BrainKind.ASTERINA -> "asterina star"
        BrainKind.DIAMOND_GOBY -> "diamond goby"
        BrainKind.LEOPARD_WRASSE -> "leopard wrasse"
        BrainKind.FAIRY_WRASSE -> "fairy wrasse"
        BrainKind.BOBTAIL_SQUID -> "bobtail squid"
        BrainKind.FIREFISH -> "firefish"
        BrainKind.CHROMIS -> "chromis"
        BrainKind.ANEMONE -> "bubble tip anemone"
        BrainKind.GRAMMA -> "royal gramma"
        BrainKind.WATCHMAN_GOBY -> "watchman goby"
        BrainKind.MANDARIN -> "mandarin"
        BrainKind.SIXLINE -> "six line wrasse"
        BrainKind.CLOWN_GOBY -> "clown goby"
        BrainKind.BUTTERFLY -> "butterflyfish"
        BrainKind.GENERIC_FISH -> o.species.common.ifBlank { "fish" }.lowercase()
        BrainKind.GENERIC_INVERT -> o.species.common.ifBlank { "critter" }.lowercase()
    }

    private fun possessive(s: String) = if (s.endsWith("s")) "$s'" else "$s's"

    /** Kid words for a food. */
    fun foodWord(k: FoodKind): String = when (k) {
        FoodKind.FLAKE -> "flakes"
        FoodKind.MYSIS -> "mysis shrimp"
        FoodKind.PELLET, FoodKind.FLOATING_PELLET -> "pellets"
        FoodKind.NORI -> "nori"
        FoodKind.PODS -> "copepods"
        FoodKind.PRAWN -> "prawn"
        FoodKind.PLANKTON -> "plankton"
        FoodKind.BRINE -> "brine shrimp"
        FoodKind.KRILL -> "krill"
        FoodKind.ALGAE_WAFER -> "algae wafer"
        FoodKind.CORAL_FOOD -> "coral food"
        FoodKind.CYCLOPS -> "cyclops"
    }

    private fun cap(s: String) = s.replaceFirstChar { it.uppercaseChar() }
    private fun max(a: Double, b: Double) = if (a > b) a else b

    /** What one sentence is built from: the animal, its brain and target, and lazily looked-up extras. */
    internal class Env(val o: Organism, val sim: Simulation, val brain: SpeciesBrain, val target: Organism?) {
        val night: Boolean get() = sim.perception.night
        val threatName: String? by lazy { brain.threat?.takeIf { it.alive }?.let { refer(o, it) } }
        val waryName: String? by lazy { o.wary?.takeIf { it.alive && o.waryLevel > 0.3 }?.let { refer(o, it) } }

        /** " because Falco the hawkfish is close", " after a scare", or "". */
        fun because(): String = when {
            threatName != null -> " because $threatName is close"
            waryName != null -> " because $waryName is nearby"
            brain.touchKind != TouchReaction.NONE && sim.simTimeSec - brain.touchStartAt < 30 -> " after the tap on the tank"
            sim.simTimeSec - brain.startleAt < 30 -> " after a scare"
            else -> ""
        }

        /** The clown's host coral ("the hairy mushrooms"), else "its host". */
        val host: String by lazy {
            if (sim.anemones.hostOf(o) != null) return@lazy "its bubble tip anemone"
            val anchor = o.hostAnchor?.let { sim.rock.anchors[it] }
            val at = anchor?.pos ?: o.homePos
            val coral = sim.corals.minByOrNull { it.pos.distanceTo(at) }?.takeIf { it.pos.distanceTo(at) < 15.0 }
            coral?.let { "the " + it.nickname.ifBlank { it.species.common }.lowercase() } ?: "its host"
        }

        /** The nearest food it would eat (within 80 cm), in kid words. */
        val foodWord: String? by lazy {
            sim.food.nearest(o.pos, 80.0) { f -> o.species.dietMatch(f.kind) > 0 }?.let { foodWord(it.kind) }
        }

        /** Which part of the client a cleaner is working on (cycles every few seconds of the bout). */
        fun part(): String {
            val cur = runCatching { o.brain.current }.getOrNull()
            val i = (((cur?.elapsed ?: 0.0) / 4.0).toInt()) % PARTS.size
            return PARTS[i]
        }
    }

    private val PARTS = listOf("gills", "fins", "sides", "mouth")
    /** Behaviours an empty-tummy fish idles in: the "is hungry and watching for food" sentence replaces them. */
    private val IDLE = setOf("hover", "host", "wander", "patrol", "hovercover", "perch", "perchhole", "mouthhover", "cavedart", "surf", "station", "firehover", "shoal", "gramhover", "lookout", "cperch", "rove")
    private val RESTING = setOf("sleep", "cocoon", "burrow", "sand", "bury", "nestle", "hovercover", "hide", "buried")
    /** Never offered as the next plan: reactions, not plans. */
    private val NO_NEXT = setOf("touch", "unwell", "heat", "flee", "tailflip", "retract", "dart", "jet", "snap", "molt", "gasp", "abandoned", "flipped", "yield")
    private const val HUNGRY = 0.65
    private const val MIN_NEXT_SCORE = 0.2
    private const val NEXT_STICKY = 0.08
}

/**
 * Per-simulation cache of thoughts (refreshed every [EVERY_TICKS] ticks, ~4 Hz at the 20 Hz wall tick) so the
 * 20 Hz snapshot does not rebuild the sentences each time. Holds the sticky "next" pick per animal. UI only.
 */
class ThoughtBook {
    private val cache = HashMap<String, Thought>()
    private val nextPick = HashMap<String, Int>()
    private var tick = Long.MIN_VALUE

    fun thought(o: Organism, sim: Simulation, target: Organism?): Thought {
        val fresh = sim.tickCount - tick
        val hit = cache[o.id]
        if (hit != null && fresh in 0 until EVERY_TICKS) return hit
        val (t, next) = Thoughts.of(o, sim, target, nextPick[o.id] ?: -1)
        cache[o.id] = t
        nextPick[o.id] = next
        return t
    }

    /** Called once per snapshot after every animal's thought: starts a new refresh window when the old one ran out. */
    fun endSnapshot(sim: Simulation, ids: Collection<String>) {
        val fresh = sim.tickCount - tick
        if (fresh !in 0 until EVERY_TICKS) tick = sim.tickCount
        if (cache.size > ids.size) { cache.keys.retainAll(ids.toSet()); nextPick.keys.retainAll(ids.toSet()) }
    }

    companion object { const val EVERY_TICKS = 5L }
}

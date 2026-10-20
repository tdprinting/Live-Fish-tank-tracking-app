package com.reeflive.core.live

/**
 * "Did you know?": a short teaching moment each time the app opens and there are no new update notes (web What's new
 * popup, Android live screen). Half are facts about the animals in a reef tank, half are reef-keeping tips; each
 * is one or two plain sentences a child can read. Sources: the research compendium (docs/research), Fautin & Allen
 * "Anemone fishes and their host sea anemones", Fenner "The Conscientious Marine Aquarist", Borneman "Aquarium
 * Corals", Delbeek & Sprung "The Reef Aquarium", Holmes-Farley's Reefkeeping chemistry articles, NOAA Ocean Service.
 *
 * [nth] walks the whole list in a scrambled order before anything repeats.
 */
object ReefFacts {
    data class Fact(val kind: Kind, val text: String)
    enum class Kind(val title: String) { CREATURE("Ocean fact of the day"), TIP("Reef tip of the day") }

    private fun c(t: String) = Fact(Kind.CREATURE, t)
    private fun t(t: String) = Fact(Kind.TIP, t)

    val all: List<Fact> = listOf(
        // ---- ocean creatures
        c("Every clownfish hatches as a boy. In a pair the bigger fish turns into the girl, and if she is lost the male changes into a female."),
        c("A clownfish is safe in its anemone because the mucus on its skin stops the stinging cells from firing."),
        c("Clownfish dads guard the eggs. They fan them with their fins and nibble away any bad ones until they hatch, about a week later."),
        c("Blue tangs (Dory!) have a sharp, scalpel-like spine by their tail. They flick it out to defend themselves, so net them gently."),
        c("Baby blue tangs are bright yellow and blue. They turn the famous royal blue as they grow up."),
        c("Cleaner wrasses run cleaning stations. Bigger fish line up and hold still while the wrasse picks parasites off their skin."),
        c("Cleaner shrimp wave their long white antennae to tell fish: come over, I will clean you!"),
        c("Hermit crabs do not grow their own shell. When they get bigger they hunt for an empty snail shell and move house."),
        c("A brittle star can drop an arm to escape a predator and grow a new one later."),
        c("Brittle stars do not have eyes, but their skin can sense light, so they know when to hide."),
        c("Coral is an animal, not a plant! Each coral is a colony of tiny polyps, each one like a little upside-down jellyfish."),
        c("Many corals have tiny algae living inside them. The algae make food from light and share it, which is why corals need bright lamps."),
        c("Green star polyps pull their polyps in at night or when something bothers them, and the purple mat underneath shows through."),
        c("Mushroom corals can split themselves in half to make a new mushroom."),
        c("Bubble tip anemones can split into two. A healthy one in a good tank may make copies of itself over time."),
        c("The tips of a bubble tip anemone's tentacles can swell into little balls. Nobody knows exactly why, but strong light and good food seem to help."),
        c("Hawkfish have no swim bladder to float with, so they perch on rocks and corals and pounce on food."),
        c("Engineer gobies (convict blennies) dig tunnels under the rocks. The babies even school around the parents' tunnel."),
        c("Sand sifting gobies take a mouthful of sand, spit it out through their gills and keep the little worms and crumbs they find."),
        c("Pajama cardinalfish dads carry the eggs in their mouth until they hatch, and do not eat the whole time."),
        c("Blennies are like little dogs of the reef: they sit in a hole with just their head poking out, watching everything."),
        c("Nassarius snails sleep buried in the sand and pop out with their long snorkel sniffing the water when food lands."),
        c("Sally lightfoot crabs are fast climbers. In the wild they run across wave-splashed rocks without getting washed away."),
        c("Coral banded shrimp wave their big white-and-red claws to scare off anything that comes close to their cave."),
        c("A copepod is smaller than a grain of rice, but billions of them feed fish, corals and baby fish all over the ocean."),
        c("Fish sleep with their eyes open, because they have no eyelids. Many hide in a crevice or change color at night."),
        c("Leopard wrasses bury themselves in the sand every night and come out again when the lights turn on."),
        c("Coral reefs cover less than one percent of the ocean floor but are home to about a quarter of all ocean creatures."),
        c("Parrotfish chew coral rock and poop out fine white sand. Some beaches are made mostly of parrotfish poop!"),
        c("Octopuses have three hearts and blue blood."),
        c("Sea turtles can hold their breath for hours while they sleep under water."),
        c("Some corals glow under blue light. The glowing colors may work like sunscreen for the coral."),
        c("The Great Barrier Reef is the biggest thing built by living animals. It can be seen from space."),
        c("Dottybacks are tiny but brave: they will chase fish twice their size away from their favourite hole."),
        c("Lawnmower blennies spend all day scraping algae off rocks with comb-like teeth."),
        // ---- reef keeping tips
        t("Test your water once a week. Writing the numbers down helps you spot a problem before the fish feel it."),
        t("Feed a little at a time. If food is still drifting after two minutes, that was too much."),
        t("Top off with fresh RO water, not salt water. Only the water evaporates, so the salt stays behind."),
        t("A 10 to 20 percent water change every week or two keeps nitrate and phosphate down and puts back what corals use."),
        t("Change temperature and salinity slowly. Fish handle a small change over a day far better than a sudden jump."),
        t("Float a new fish's bag for about 15 minutes so the water in the bag reaches tank temperature."),
        t("Never pour the store's bag water into your tank. Net the new fish out so pests and dirty water stay out."),
        t("Empty the skimmer cup when it fills with brown gunk. A full cup stops cleaning the water."),
        t("Swap the filter sock every few days. Old muck in it rots and puts waste back in the water."),
        t("Add new fish one or two at a time so the good bacteria have time to catch up with the extra waste."),
        t("Keep the lights on a timer. Corals and fish like the same day and night every day, about 8 to 10 hours of light."),
        t("Turn the pumps off while you feed so the food does not get blown straight into the filter."),
        t("Tangs need lots of greens. A sheet of nori on a clip gives them something to graze all day."),
        t("Put the most peaceful fish in first and the bossy ones last, so nobody claims the whole tank."),
        t("Clownfish do not need an anemone. If they do not find one, they often host a coral or the corner of the tank."),
        t("Wait until a tank has been running a few months before adding an anemone. They need stable water."),
        t("Feed corals like zoanthids and mushrooms a tiny bit of coral food once or twice a week."),
        t("Look at your fish every day. A fish that hides, stops eating or breathes fast is telling you something is wrong."),
        t("Stable alkalinity matters more than a perfect number. Pick a target and keep it steady."),
        t("Clean the glass with a magnet or a Flipper scraper a few times a week to keep the view clear."),
        t("Never use soap on anything that goes in the tank. Even a tiny bit can hurt fish and corals."),
        t("A cleanup crew of snails and hermit crabs eats leftover food and algae, but too many will run out of food."),
        t("Quarantine new fish in a separate tank for a few weeks to catch sickness before it reaches your display."),
        t("Keep a lid or net on the tank if you have jumpers like wrasses and gobies."),
        t("If the power goes out, keep the water moving with a battery air pump and cover the tank to hold the heat in."),
        t("Turn the heater off before a water change so it does not crack when the water level drops."),
        t("Pods and tiny critters on the rocks are a good sign. They mean your tank has a healthy food web."),
        t("Brown film on new sand is normal in a young tank. It usually fades as the tank settles."),
    )

    /** The [i]th fact shown (a counter kept per device): every fact once in a scrambled order, then the walk starts again. */
    fun nth(i: Long): Fact {
        val n = all.size
        val k = ((i % n + n) % n).toInt()
        // a stride with no common factor with n visits every index once per n days
        var stride = 37 % n
        while (gcd(stride, n) != 1) stride++
        return all[(k * stride) % n]
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
}

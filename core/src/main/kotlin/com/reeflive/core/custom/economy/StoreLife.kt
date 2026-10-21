package com.reeflive.core.custom.economy

/**
 * The people who make each fish store feel lived in (docs/research/fish_stores.md): its staff, its regulars and its
 * pet, and the little routines they loop through while the keeper shops. Pure data: the renderer's store room
 * (render/StoreRoom.kt) walks and poses them and shows what they say in speech bubbles; tools/blender/store_rooms.py
 * models each person as the nodes `P<slot>_Torso`, `_Head`, `_ArmL`, `_ArmR`, `_LegL`, `_LegR` (CASTS there lists the
 * same slots in the same order).
 *
 * A person loops through [Activity]s picked by weight; each is a few [Step]s: walk to a [Place], do an [Act] there for
 * a while, maybe say one of a few lines. The [Role.TRADER] regular also walks up to the keeper once a visit when
 * [StoreTrades] finds a trade (the host gives the renderer the line).
 */
enum class Role { CLERK, AQUARIST, STOCKER, GREETER, SHOPPER, KID, TRADER }

/** Where a step happens, resolved by the renderer against the store's bays. */
enum class Place {
    /** In front of a display tank: a random one, or [Step.near] the keeper's. */
    TANK,
    /** Beside the keeper, in front of the tank they stand at, turned toward them. */
    KEEPER,
    /** The customer side of the checkout counter. */
    COUNTER,
    /** Behind the counter, at the till. */
    TILL,
    /** Just inside the front door. */
    DOOR,
    /** Outside, past the door: the person is gone for the step's time (off home, or just arriving). */
    OUTSIDE,
    /** The entry bay left of the first tank (dry goods, the bulletin board, the water station). */
    ENTRY,
    /** The staff door in the back corner past the counter. */
    BACK,
    /** Wherever the person already is. */
    HERE,
}

/** What the body does at a step (the renderer's pose for it). */
enum class Act {
    IDLE, BROWSE, POINT, PHOTO, PRESS_GLASS, TAP_GLASS, JUMP, TALK, WAVE, FEED, NET, CLEAN, TEST, CARRY, SWEEP, TILL,
    SIP, READ, TRADE,
}

/** What the person holds in the right hand (store_rooms.py `Prop_<name>` nodes, grip at the origin). */
enum class Prop { NONE, NET, CUP, PHONE, BAG, JUG, BUCKET, BROOM, TABLET, FOOD, FRAG, BOX, VIAL }

/**
 * One beat of a routine: go to [place], then [act] for about [seconds], saying one of [say] (picked at random, none
 * when empty) as the act starts. [near] picks the tank next to the keeper's instead of a random one.
 */
data class Step(
    val place: Place,
    val act: Act,
    val seconds: Double,
    val say: List<String> = emptyList(),
    val prop: Prop = Prop.NONE,
    val near: Boolean = false,
)

data class Activity(val weight: Int, val steps: List<Step>)

data class StorePerson(
    /** The model's P<slot> nodes. */
    val slot: Int,
    val name: String,
    val role: Role,
    /** One line about them (for a tap: "Shelly owns the shop"). */
    val about: String,
    val routines: List<Activity>,
    /** Said when the keeper taps them. */
    val hello: List<String>,
    /** Where they start when the keeper walks in. */
    val start: Place = Place.TANK,
)

enum class PetKind { CAT, DOG }

/** The shop pet: where it naps and what it is called (it wanders the aisle between naps). */
data class StorePet(val kind: PetKind, val name: String, val nap: Place)

/** A store's people, and the lines staff use for things that happen to them (a tapped glass, a trade). */
data class StoreCast(
    val store: FishStore,
    val people: List<StorePerson>,
    val pet: StorePet?,
    /** A staff member's answer when a kid taps the glass. */
    val noTapping: List<String>,
    /** The trader's answers to the keeper's yes and no. */
    val tradeYes: List<String>,
    val tradeNo: List<String>,
) {
    val trader: StorePerson? get() = people.firstOrNull { it.role == Role.TRADER }
    val clerk: StorePerson get() = people.first { it.role == Role.CLERK }
}

object StoreLife {
    private fun s(place: Place, act: Act, seconds: Double, vararg say: String, prop: Prop = Prop.NONE, near: Boolean = false) =
        Step(place, act, seconds, say.toList(), prop, near)

    private fun a(weight: Int, vararg steps: Step) = Activity(weight, steps.toList())

    fun cast(store: FishStore): StoreCast = CASTS.getValue(store)

    fun cast(storeId: String): StoreCast? = FishStore.byId(storeId)?.let { CASTS[it] }

    // ============================================================== routines everyone shares

    private fun browse(vararg say: String) = a(5,
        s(Place.TANK, Act.BROWSE, 7.0, *say),
        s(Place.HERE, Act.POINT, 3.0),
        s(Place.HERE, Act.BROWSE, 5.0),
    )

    private val photo = a(2, s(Place.TANK, Act.PHOTO, 5.0, "Got it! Best picture ever.", "Smile, fishies!", prop = Prop.PHONE))

    private fun clerkDay(vararg chat: String) = listOf(
        a(6, s(Place.TILL, Act.TILL, 8.0), s(Place.HERE, Act.IDLE, 6.0)),
        a(3, s(Place.TILL, Act.SIP, 5.0, prop = Prop.CUP), s(Place.HERE, Act.IDLE, 5.0)),
        a(3, s(Place.TILL, Act.TALK, 5.0, *chat)),
        a(1, s(Place.TILL, Act.WAVE, 2.5, "Holler if you need a hand!", "Take your time!")),
    )

    private fun aquaristDay(vararg chat: String) = listOf(
        a(4, s(Place.TANK, Act.FEED, 6.0, "Dinner time!", "Who's hungry?", "Just a pinch. Never too much.", prop = Prop.FOOD)),
        a(3, s(Place.TANK, Act.NET, 6.0, "Gently does it...", "Got him!", "Easy, little buddy.", prop = Prop.NET), s(Place.COUNTER, Act.IDLE, 3.0, prop = Prop.BAG)),
        a(3, s(Place.TANK, Act.CLEAN, 7.0, "Squeaky clean glass!", "There. Now you can see them.")),
        a(3, s(Place.TANK, Act.TEST, 6.0, "Salinity 1.025. Perfect.", "Nitrate looks good today.", prop = Prop.VIAL)),
        a(2, s(Place.BACK, Act.IDLE, 8.0), s(Place.TANK, Act.CARRY, 4.0, prop = Prop.BUCKET)),
        a(2, s(Place.TANK, Act.TALK, 5.0, *chat)),
    )

    private val kidStuff = listOf(
        a(5, s(Place.TANK, Act.PRESS_GLASS, 6.0, "Whoa! Look at that one!", "It's looking at me!", "Hi, fishy!")),
        a(3, s(Place.TANK, Act.JUMP, 3.0, "Can we get one? Please please please?", "That one's my favorite!"), s(Place.HERE, Act.BROWSE, 4.0)),
        a(2, s(Place.TANK, Act.TAP_GLASS, 2.5, "Hello in there!")),
        a(2, s(Place.TANK, Act.POINT, 4.0, "Is that Nemo?", "That shrimp is cleaning a fish!", "Look, a starfish!")),
    )

    private val CASTS: Map<FishStore, StoreCast> = mapOf(
        // ---- Shelly's Pet Shop: the corner pet shop with a fish corner; the owner knows every kid's name
        FishStore.SHELLYS to StoreCast(
            FishStore.SHELLYS,
            listOf(
                StorePerson(0, "Shelly", Role.CLERK, "Shelly has run this pet shop for 30 years.",
                    clerkDay("Mittens, get off the salt bags!", "We get new snails every Tuesday.", "My first fish was a goldfish named Bubbles."),
                    listOf("Hi sweetie! Need help finding anything?", "The clownfish are my favorite too.", "Ask me anything about fish!"), Place.TILL),
                StorePerson(1, "Max", Role.AQUARIST, "Max works here after school.",
                    aquaristDay("I'm saving up for my own reef tank.", "Did you know snails eat algae all night?") +
                        a(2, s(Place.ENTRY, Act.SWEEP, 8.0, "Somebody spilled fish flakes again.", prop = Prop.BROOM)),
                    listOf("Hey! The cleanup crew tank just got new hermits.", "I'm Max. I feed everybody here!", "Want to see the shrimp?")),
                StorePerson(2, "Grandpa Joe", Role.TRADER, "Joe raises baby clownfish in his garage.",
                    listOf(browse("Reminds me of my first tank, back in '72.", "Those snails work harder than I ever did."),
                        a(2, s(Place.COUNTER, Act.TALK, 6.0, "Shelly, my clowns laid eggs again!", "Got any more of that frozen brine shrimp?")),
                        a(1, s(Place.ENTRY, Act.READ, 6.0, "Reef club picnic Saturday. Hm!"))),
                    listOf("Howdy, young reefer!", "Patience is the secret to a good tank.", "I've kept fish longer than your parents have been alive!")),
                StorePerson(3, "Ana", Role.SHOPPER, "Ana is setting up her first tank with her daughter Lily.",
                    listOf(browse("Lily, which fish do you like?", "This one says easy to keep. Good!"),
                        a(2, s(Place.COUNTER, Act.TALK, 5.0, "How many snails do we need for a little tank?", "Is the water test free?")),
                        a(1, s(Place.TANK, Act.READ, 5.0, "Hmm, it says this one gets big..."))),
                    listOf("Hi! It's our first saltwater tank.", "Lily picked the name before we even have the fish.")),
                StorePerson(4, "Lily", Role.KID, "Lily is six and loves clownfish.",
                    kidStuff, listOf("I'm gonna name my fish Sparkles!", "Do you have a tank too?", "Hi! I'm Lily!")),
            ),
            StorePet(PetKind.CAT, "Mittens", Place.COUNTER),
            noTapping = listOf("Gentle, sweetie! Tapping scares them.", "Oops! Let's not tap the glass, okay?"),
            tradeYes = listOf("Wonderful! You'll love them.", "Deal! Take good care of them now."),
            tradeNo = listOf("No problem at all, kiddo.", "Maybe next time!"),
        ),
        // ---- Frag Shack: a garage coral shop under blue lights; everyone talks in coral names
        FishStore.FRAG_SHACK to StoreCast(
            FishStore.FRAG_SHACK,
            listOf(
                StorePerson(0, "Kai", Role.CLERK, "Kai surfs at sunrise and sells corals all day.",
                    clerkDay("Dude, that zoa frag is glowing like crazy.", "Surf was perfect this morning.", "Reef, stop chewing the frag plugs!"),
                    listOf("Aloha! Welcome to the Shack.", "Every frag here grew in our own tanks.", "Hang loose! Ask me about any coral."), Place.TILL),
                StorePerson(1, "Jess", Role.AQUARIST, "Jess can name every coral in the shop.",
                    aquaristDay("Corals look best under the blue lights.", "This one's a Rasta zoa. Isn't it wild?") +
                        a(3, s(Place.TANK, Act.FEED, 6.0, "Coral food! They catch it with their tentacles.", prop = Prop.FRAG)),
                    listOf("Hi! Want a coral that's hard to kill? Mushrooms!", "I glue every frag to its plug by hand.", "Shh, the corals are opening up.")),
                StorePerson(2, "Mia", Role.TRADER, "Mia collects zoanthids. She has 200 kinds!",
                    listOf(browse("Ooh, I don't have that color yet.", "Look at those polyps open up!"),
                        a(2, s(Place.COUNTER, Act.TALK, 6.0, "Kai, you holding any new zoas for me?", "I brought frags for store credit.")),
                        a(1, s(Place.TANK, Act.PHOTO, 5.0, "For my coral collection page!", prop = Prop.PHONE))),
                    listOf("Hi! Want to see my zoa pictures?", "Trading frags is the best part of the hobby.", "My tank is basically all zoas.")),
                StorePerson(3, "Sam", Role.SHOPPER, "Sam is a reef hobbyist who comes in every Saturday.",
                    listOf(browse("Blue lights or white lights? Hmm.", "That leather coral is huge!"), photo,
                        a(2, s(Place.TANK, Act.TALK, 5.0, "Mine grew like a weed last month.", "My tank finally cycled!"))),
                    listOf("Hey! Do you dose anything?", "I'm here every Saturday.")),
                StorePerson(4, "Theo", Role.KID, "Theo wants a coral that glows.",
                    kidStuff + a(2, s(Place.TANK, Act.POINT, 4.0, "Dad, what's a frag?", "That coral is glowing! How?")),
                    listOf("Corals are animals. Did you know?", "I'm Theo! Is that coral alive?")),
                StorePerson(5, "Ben", Role.SHOPPER, "Ben is Theo's dad, learning about corals.",
                    listOf(browse("So a frag is a little piece of a coral?", "Theo, hands off the glass, bud."),
                        a(2, s(Place.COUNTER, Act.TALK, 5.0, "What's the easiest coral for a beginner?", "Do these need special lights?"))),
                    listOf("Hi! My son talked me into this.", "I'm learning a lot today!")),
            ),
            StorePet(PetKind.DOG, "Reef", Place.ENTRY),
            noTapping = listOf("Whoa, little dude, no tapping!", "Easy on the glass, friend!"),
            tradeYes = listOf("Sweet! Pleasure trading with you.", "Yes! This is going right in my tank."),
            tradeNo = listOf("All good! Another time.", "No worries. Happy reefing!"),
        ),
        // ---- Big Blue Aquatics: the big superstore with a fish room, a reef club board and a busy Saturday
        FishStore.BIG_BLUE to StoreCast(
            FishStore.BIG_BLUE,
            listOf(
                StorePerson(0, "Marcus", Role.CLERK, "Marcus manages Big Blue and runs the reef club.",
                    clerkDay("Free water testing at the counter!", "Reef club meets Thursday night.", "Priya, tank 12 needs a water change."),
                    listOf("Welcome to Big Blue! Bring a water sample anytime.", "We have the biggest fish room in town.", "Ask about our 14-day guarantee!"), Place.TILL),
                StorePerson(1, "Priya", Role.AQUARIST, "Priya runs the fish room and quarantines every new fish.",
                    aquaristDay("New fish rest in quarantine for two weeks first.", "This tang is eating great today.") +
                        a(2, s(Place.TANK, Act.NET, 6.0, "Bagging this one for a customer.", prop = Prop.NET), s(Place.COUNTER, Act.IDLE, 4.0, prop = Prop.BAG)),
                    listOf("Hi! Every fish here eats before we sell it.", "Want to know a fish's story? Ask me!", "Fish room's my happy place.")),
                StorePerson(2, "Leo", Role.STOCKER, "Leo stocks the shelves and fills the water jugs.",
                    listOf(a(4, s(Place.BACK, Act.IDLE, 5.0), s(Place.ENTRY, Act.CARRY, 8.0, "Coming through! Salt delivery!", prop = Prop.BOX)),
                        a(3, s(Place.ENTRY, Act.SWEEP, 8.0, prop = Prop.BROOM)),
                        a(2, s(Place.ENTRY, Act.CARRY, 6.0, "Fresh RO water, five gallons!", prop = Prop.JUG))),
                    listOf("Hey! Need salt? Aisle one.", "I carry like a hundred salt buckets a day.")),
                StorePerson(3, "Dave", Role.TRADER, "Dave is the reef club's frag swap king.",
                    listOf(browse("Not bad, not bad at all.", "I grew that coral from a frag this big."),
                        a(3, s(Place.COUNTER, Act.TEST, 6.0, "Marcus, test my water? Here's my sample.", prop = Prop.JUG)),
                        a(2, s(Place.ENTRY, Act.READ, 6.0, "Frag swap next Saturday. Be there!"))),
                    listOf("Reef club meets Thursday. You should come!", "I trade frags with half the town.", "Hiya, fellow reefer!")),
                StorePerson(4, "Rosa", Role.SHOPPER, "Rosa keeps a big tang tank at home.",
                    listOf(browse("My tang would love a friend.", "Such healthy fish here."),
                        a(2, s(Place.COUNTER, Act.TALK, 5.0, "Do you have more of that seaweed?", "My tank is ten years old today!"))),
                    listOf("Hello, dear! Ten years I've kept fish.", "Feed them a little seaweed every day.")),
                StorePerson(5, "Jamal", Role.SHOPPER, "Jamal posts reef videos online.",
                    listOf(photo, photo, browse("Wait till my followers see this.", "This is going in the video.")),
                    listOf("Yo! Like and subscribe!", "Want to be in my video?")),
                StorePerson(6, "Zoe", Role.KID, "Zoe is on a trip with her mom.",
                    kidStuff, listOf("Mom said I can pick one snail!", "Hi! Do you like sharks?")),
                StorePerson(7, "Mei", Role.SHOPPER, "Mei is Zoe's mom.",
                    listOf(browse("Zoe, gentle hands please.", "Look, honey, a cleaner shrimp."),
                        a(2, s(Place.COUNTER, Act.TALK, 5.0, "Which fish are safe with shrimp?"))),
                    listOf("Hi! We just come to look at the fish sometimes.", "It's better than the aquarium, and free!")),
            ),
            null,
            noTapping = listOf("Please don't tap the glass, buddy!", "Tapping scares them. Thanks, kiddo!"),
            tradeYes = listOf("Great trade! Bring a frag to the next club swap.", "Awesome, thanks! Happy reefing!"),
            tradeNo = listOf("No sweat! Offer stands at the club swap.", "Totally fine. See you around!"),
        ),
        // ---- Deep Reef Imports: a quiet gallery for rare animals; the staff speak softly and wear black
        FishStore.DEEP_REEF to StoreCast(
            FishStore.DEEP_REEF,
            listOf(
                StorePerson(0, "Celeste", Role.CLERK, "Celeste chooses every animal Deep Reef imports.",
                    clerkDay("Each of these fish was hand-picked.", "Our showpiece came all the way from Fiji.", "Victor, the espresso, please."),
                    listOf("Welcome to Deep Reef. Take your time.", "Every animal here is quarantined for a month.", "May I tell you about the showpiece?"), Place.TILL),
                StorePerson(1, "Victor", Role.GREETER, "Victor greets every guest at the door.",
                    listOf(a(4, s(Place.DOOR, Act.IDLE, 10.0)),
                        a(3, s(Place.TANK, Act.FEED, 6.0, "A little mysis. Only the finest.", prop = Prop.FOOD)),
                        a(2, s(Place.TANK, Act.CLEAN, 7.0, "Spotless. As it should be.")),
                        a(2, s(Place.COUNTER, Act.SIP, 5.0, prop = Prop.CUP))),
                    listOf("Good day. Welcome to Deep Reef Imports.", "May I offer you a sparkling water?", "Please, enjoy the gallery."), Place.DOOR),
                StorePerson(2, "Ms. Okafor", Role.TRADER, "Ms. Okafor collects the rarest corals in the city.",
                    listOf(browse("Exquisite.", "The color on that one... divine."),
                        a(2, s(Place.COUNTER, Act.TALK, 6.0, "Celeste, anything new from Fiji?", "I'll take the usual, darling."))),
                    listOf("A young collector! How delightful.", "Quality over quantity, always.")),
                StorePerson(3, "Julian", Role.SHOPPER, "Julian designs reef tanks for fancy houses.",
                    listOf(browse("This would look stunning in a lobby.", "Hmm. Too blue? No, perfect."),
                        a(2, s(Place.TANK, Act.READ, 6.0, "Taking notes for a client.", prop = Prop.TABLET))),
                    listOf("I design tanks for a living!", "A great tank is like a painting.")),
                StorePerson(4, "Nadia", Role.SHOPPER, "Nadia and Omar visit the gallery on date night.",
                    listOf(browse("Omar, look at this one!", "It's like a painting that moves."),
                        a(2, s(Place.TANK, Act.TALK, 5.0, "Should we get a bigger tank?"))),
                    listOf("Isn't this place beautiful?", "We come here instead of the movies!")),
                StorePerson(5, "Omar", Role.SHOPPER, "Omar keeps a little nano tank at home.",
                    listOf(browse("That's more than my car, Nadia.", "Our nano tank would fit inside this one."), photo),
                    listOf("Hi! Our tank is tiny compared to these.", "I could stare at these all day.")),
            ),
            null,
            noTapping = listOf("Kindly, no tapping on the glass.", "Gently, please. They startle easily."),
            tradeYes = listOf("Splendid. A pleasure doing business.", "Marvelous. Take good care of it."),
            tradeNo = listOf("Of course. Perhaps another time.", "Quite all right, darling."),
        ),
    )
}

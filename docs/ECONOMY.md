# My Tank economy: sand dollars, fish stores and unlocks

Design for turning **My tank** into a game: you earn **sand dollars** by looking after the tank every day, spend them
at **fish stores** on new fish, corals and inverts, and sell animals you raised (an anemone that split, a coral frag,
a fish that grew up) back to the stores. My tank is the app's main tab (the Sandbox tab is going away); the Watch tab,
the live copy of Tee's real tank, has no money in it.

The two rules above everything else: **it must be fun, and a kid must be able to follow it without reading
anything.** Every number below is a starting point to tune after Tee plays it.

## 1. The loop in one picture

```
 look after the tank  ──►  earn sand dollars + stars  ──►  level up, new stores open
        ▲                                                         │
        │                                                         ▼
 animals grow, split, frag  ◄──  put them in the tank  ◄──  buy animals at a store
        │
        └──►  sell extras to a store for more sand dollars
```

* **Daily care** pays the bills: a short list of real tank chores (feed, top off, empty the skimmer...).
* **Stores** turn sand dollars into new animals. Better stores open as you level up.
* **Growing and breeding** pays back: anything you raise can be sold, and some stores want it more than others.

## 2. Money: sand dollars

* Name: **sand dollars** (a real sea animal, and it sounds like money). Drawn as a little round sand dollar glyph,
  never an emoji, in the gold accent colour of the Abyss theme.
* Shown as a chip at the top of My tank: `[sand dollar] 245  ★ 3`. Tap it to open the **Care card** (section 4).
* Prices are **two sand dollars per US dollar** a real store charges (`PriceBook.SCALE`), so a chromis is 30, a
  clownfish 60 and a yellow tang 260. Relative values stay realistic, and a new fish takes a few days of care to
  save for (Tee, 2026-10-20: "make the user work for their points").
* Money never goes below zero, and nothing costs money every day (no rent, no food bill). A kid who forgets the
  tank for a week loses nothing but the daily pay they missed.
* **Starting money:** 60 sand dollars: one clownfish, or two chromis, or a clean-up crew on day one.
* **Never stuck:** if the tank has no animals and you cannot afford the cheapest one, Shelly's Pet Shop gives you
  a free starter (a pair of snails) once a day.

## 3. Stars and levels

Every sand dollar earned from care and goals also gives one **star point** (sales do not, so buying and selling
cannot be farmed for levels). Levels open stores and the rarer animals.

| Level | Star points | Opens | Rough day (doing the daily list) |
|---|---|---|---|
| 1 | 0 | Shelly's Pet Shop | day 1 |
| 2 | 60 | Frag Shack | day 2 |
| 3 | 180 | more animals at both | day 4 |
| 4 | 360 | Big Blue Aquatics | day 8 |
| 5 | 600 | a 4th daily task | day 13 |
| 6 | 900 | Deep Reef Imports | day 20 |
| 7 | 1260 | the rarest animals | day 28 |
| 8-10 | 1680 / 2160 / 2700 | a new tank badge each | 6-9 weeks |

Formula: level *n* needs 30 · *n* · (*n* − 1) star points. Levelling up plays a short celebration (Hermie the hermit
cheers, coins burst from the sand) and says what opened ("Big Blue Aquatics is open!").

## 4. Earning: the daily care list

Each real day (local midnight) the Care card picks **3 tasks** (4 from level 5). They are real sim actions the
keeper already has, so doing a task also keeps the tank healthy. The list is chosen from what the tank needs
(the same tank check behind "Fix my tank"), topped up with everyday chores so there are always 3.

| Task | Done when | Pays |
|---|---|---|
| Feed your fish | any feeding (twice a day counts as one task) | 5 |
| Feed your corals | coral food (powder, phyto) | 5 |
| Test the water | open the test kit / tank check | 5 |
| Top off the water | top-off with the tank below full | 5 |
| Empty the skimmer cup | cup more than half full | 8 |
| Swap the filter sock | sock older than 2 days | 8 |
| Clean the glass | scrape when there is algae on the glass | 8 |
| Siphon the sand | siphon with detritus on the sand | 10 |
| Harvest macroalgae | trim the refugium | 8 |
| Swap the carbon | carbon spent | 8 |
| Water change | 10-25 % change (at most one a week asks for it) | 15 |

* **All done bonus:** finishing the whole list pays **+15**.
* **Streak:** each day in a row the list is finished adds +10 % to that day's pay, up to **+50 % at 5 days**. Missing
  a day drops the streak back to 0 (no other penalty).
* **Happy tank bonus:** once a day, tap "Collect" on the Care card: **1 per thriving fish, invert or healthy
  coral** (health above 70 %), capped at 30. More animals pay more, but only if they are doing well, so
  overstocking stops paying.
* **Bad care costs pay, not money:** a sick or dying animal simply stops counting for the happy tank bonus.
* **Speeding up time does not pay faster.** Tasks and bonuses are per real day, so fast-forward is for watching, not
  for farming.

A normal day: 3 tasks (~20) + all done (15) + happy tank (5-20) ≈ **40-55 sand dollars**, ~60-80 with a streak.
So a chromis (30) is a day of care, a clownfish (60) a day or two, a yellow tang (260) about four days with a
streak and a purple tang (500) about a week.

### Goals (one-time rewards)

A **Goals** list on the Care card for the big moments: first fish (+25), first coral (+25), 5 different species
(+50), a clownfish pair hosting (+75), first sale (+25), first anemone split (+50), first coral frag sold (+50), a
fish kept 30 days (+100), finish the list 7 days in a row (+100), reach level 5 (+100), raise baby fish from the
nursery (+150). Goals give star points too, so they speed up levels.

### Reef moments and the reef journal

Real things happening in the tank pay once, the first time the keeper's tank does them, and each one pops a **Reef
moment** card with the animals' names, a true reef fact and a sparkle sound (`core/custom/economy/Journal.kt`). They
come straight from sim events, so they cannot be farmed and they teach how a reef works:

| Moment | Sim event | Pays |
|---|---|---|
| Cleaning station | a cleaner cleans a fish (`Cleaned`) | 8 |
| Please clean me! | a fish poses for a cleaner (`Pose`) | 5 |
| A brand new shell | a shrimp or crab molts (`Molted`) | 8 |
| Who's the boss? | the smaller clown quivers at the bigger one (`Quiver`) | 5 |
| Eggs! | a pair spawns (`EggsLaid`) | 10 |
| Hatching night | the eggs hatch (`Hatched`) | 10 |
| Tiny fish | nursery larvae become fry (`Metamorphosed`) | 10 |
| A coral baby | a Kenya branch drops or a coral spreads (`CoralSpawned`, not aiptasia) | 8 |
| Surprise visitor | a hitchhiker rides in (`Hitchhiker`) | 5 |
| Feeling better | a sick animal recovers (`Recovered`) | 10 |

All ten together pay 79 once. **Fish birthdays:** each fish (bought, not the starting stock) is cheered by name at
1 week, 1 month, 100 days and 1 year in the tank, paying 3 / 6 / 10 / 25; a long time away that passes several at once
pays only the biggest. Birthdays are kept per animal and dropped when it leaves (sold, netted out or died), so a fish
bought later starts fresh and nothing is paid twice. Every payout goes through `earn`, so it adds star points and a
ledger line.

The **Reef journal** at the bottom of the Care card lists the moments (found ones show their fact on tap; the rest are
"???" with a hint of what to look for) and every kind of animal the tank has kept.

## 5. Spending: the fish stores

Four stores in town, each with its own look, price level, stock and taste in what it buys back. Store prices change
a little every day so it pays to shop around.

| Store | Opens | Sells | Price level | Buys back at | Personality |
|---|---|---|---|---|---|
| **Shelly's Pet Shop** | level 1 | beginner fish, snails, hermits, easy corals | 100 % | 40 % | a friendly mall pet shop; small but always open |
| **Frag Shack** | level 2 | corals and coral frags only | 85 % | 65 % on corals | a reefer's garage under purple lights; cheapest corals |
| **Big Blue Aquatics** | level 4 | almost everything, deep stock | 110 % | 60 % fish and inverts, 50 % corals | the big store with a wall of tanks |
| **Deep Reef Imports** | level 6 | rare animals (3-4 a day) | 125 % | 75 % for animals in great shape | a fancy import shop; pays the most |

* **Stock:** each store has a short list for the day (Shelly's 9 items, Frag Shack 9 corals, Big Blue 14, Deep Reef
  5), with a few of each. It restocks at midnight. The list is drawn from the store's catalogue with a seed of
  (store, date), so it is the same on every visit that day.
* **Deal of the day:** one item per store is 30 % off.
* **Wanted today:** each store asks for one or two species and pays **+50 %** for them that day ("Wanted: bubble tip
  anemones!"). This is what makes selling fun: grow something, then find the store that wants it.
* **Buying:** see "Visiting a store" below: walk the store's display tanks, pick animals into a shop bag, check out.
  The bagged animals come home and float in exactly as Add life does today (float, acclimate, release).
* **Locked animals:** every species is visible in the store so kids know what to save up for. Locked cards are
  dimmed with a lock and one short reason: "Level 4" or "Need 35" (with a progress bar to the price).
* **Warnings, not blocks:** if an animal is a bad fit (a tang in a 20 gallon, a hawkfish with shrimp), the Buy button
  says why in one line and asks "Buy anyway?". Kids learn real stocking rules without hard walls.

### Visiting a store

A store is a place you walk around, not a list. Inside, the big 3D view **becomes the store** (Tee, 2026-10-20: "an
actual 3D modeled store with stands for each tank and a simple flat environment with checkout counter"): a tiled
floor, the store's walls, a row of display tanks on their own stands under hanging tank lights, and the checkout
counter at the end of the aisle (till, card reader, a jar of sand dollars, bagged fish waiting to go home). The tank
you stand at is a real little tank with the day's stock swimming in it, several of each species, run by the same sim
as your tank (fish school, shrimp clean, corals sway); the other tanks along the aisle are stocked models. Each store
is split into sections, each section is one tank:

| Store | Sections (one display tank each) |
|---|---|
| Shelly's | Starter fish · Clean-up crew · Easy corals |
| Frag Shack | Soft corals · Mushrooms & polyps · Leathers & rare frags |
| Big Blue | Community fish · Bigger fish · Shrimp & crabs · Corals |
| Deep Reef | The showpiece tank (its rarest animal alone) · Rare fish · Rare inverts & corals |

* **Browse:** you walk in looking down the aisle at the first tank on its stand. Tap another tank (or the arrows, or
  a section tab) to walk over to it; tap the live tank to step up close, pinch and drag to look round. Tabs on top
  name the sections ("Starter fish", "Clean-up crew", "Easy corals").
* **Pick:** tap an animal in the tank (the camera swims up to it, like the close-up follow cam) or its card in the strip
  below. The card shows the picture, name, price, size, "Easy / Medium / Expert", how many are left, ribbons (Deal,
  Wanted), and one warning line when it fits your tank badly. **Add to bag** drops it in the bag; tap again for one more.
  Locked animals still swim in the tank, so kids see what they are saving for; their card says "Level 4" or "Need 35".
* **The shop bag** (bottom right) shows how many animals and the total, and turns red when the total is more than
  you have.
* **Check out:** tap the counter in 3D (or the bag button) and you walk to it; the till lists the bag, the total, and your sand dollars before and after. **Pay** sends the
  coins flying out of the money chip into the till (the reward animation run backwards), then you are back home with
  the bags floating at the top of your tank. Not enough money: the Pay button says "Need 20 more" and points at the
  Care card.
* **Leave without buying** is always one tap: the **‹ Back** button at the top left (or the phone's back gesture);
  the bag empties back onto the shelves.
* Performance: only one display tank runs at a time (your own tank pauses while you shop, then catches up as if you
  were away for those minutes), and display tanks are small (a 40 gallon breeder with a few rocks).

### Prices and unlock levels

Base prices follow 2025-26 US store prices (LiveAquaria and local-store listings). The table and `PriceBook.LISTINGS`
are in **real US dollars**; the game charges `SCALE` (2) sand dollars per dollar, before each store's own price level.
Cheap schooling fish and clean-up crew cost a few dollars to ~15, most fish and frags 25-65, tangs, the leopard wrasse
and the bubble tip anemone 130-250. "Level" is when it can be bought at all. A day of care earns roughly 40-80 sand
dollars (20-40 real dollars' worth).

| Species | Group | Level | Real price ($) |
|---|---|---|---|
| Nassarius snail | invert | 1 | 3 |
| Astrea snail | invert | 1 | 4 |
| Blue-leg hermit crab | invert | 1 | 5 |
| Peppermint shrimp | invert | 1 | 13 |
| Blue-green chromis | fish | 1 | 15 |
| Scarlet hermit crab | invert | 1 | 15 |
| Green star polyps | coral | 1 | 20 |
| Kenya tree coral | coral | 1 | 25 |
| Ocellaris clownfish | fish | 1 | 30 |
| Pajama cardinalfish | fish | 1 | 30 |
| Lawnmower blenny | fish | 1 | 30 |
| Green mushroom | coral | 1 | 30 |
| Clove polyps | coral | 1 | 35 |
| Sally Lightfoot crab | invert | 2 | 15 |
| Black brittle star | invert | 2 | 25 |
| Zoanthids | coral | 2 | 25 |
| Finger leather | coral | 2 | 25 |
| Brown feather duster | invert | 2 | 30 |
| Coral banded shrimp | invert | 2 | 30 |
| Skunk cleaner shrimp | invert | 2 | 35 |
| Hairy green mushroom | coral | 2 | 35 |
| Firefish goby | fish | 2 | 40 |
| Bicolor blenny | fish | 2 | 65 |
| Arrow crab | invert | 3 | 18 |
| Tailspot blenny | fish | 3 | 30 |
| Engineer goby | fish | 3 | 30 |
| Diamond goby | fish | 3 | 35 |
| Pink lobed leather | coral | 3 | 35 |
| Bluestreak cleaner wrasse | fish | 3 | 40 |
| Toadstool leather | coral | 3 | 70 |
| Falco hawkfish | fish | 4 | 40 |
| Indigo dottyback | fish | 4 | 50 |
| Red fairy wrasse, female (the male: level 5, 80) | fish | 4 | 50 |
| Elephant ear mushroom | coral | 4 | 50 |
| Midas blenny | fish | 4 | 65 |
| Rainbow bubble tip anemone | invert | 4 | 150 |
| Flame scallop (warning: needs phytoplankton daily) | invert | 5 | 20 |
| Organ pipe coral | coral | 5 | 40 |
| Yellow tang | fish | 5 | 130 |
| Blue tang | fish | 5 | 150 |
| Blue star leopard wrasse | fish | 6 | 150 |
| Hummingbird bobtail squid | invert | 7 | 80 |
| Purple tang | fish | 7 | 250 |
| Gem tang (rare, research 60) | fish | 8 | 1000 |
| Wrought iron butterflyfish (rare; eats fanworms) | fish | 9 | 2500 |

Asterina stars and bristleworms are hitchhikers: never sold, they show up on rock by themselves. A species added to the sim later
gets a price from its size and care level (`PriceBook` default) until it is listed here.

Which store sells what:
* Shelly's: everything level 1-3 except corals above level 1.
* Frag Shack: every coral.
* Big Blue: everything up to level 5.
* Deep Reef: level 4 and up, plus one "rare colour" pick a day (same species, special name, 1.5× price).

## 6. Selling

Anything alive in the tank can be sold, from the **Sell** button inside a store. The store shows its offer for each of your
animals; tap **Sell**, the net catches it (the existing net-out animation), and the money arrives.

**Offer = base price × store buy-back rate × health × size bonus × wanted bonus**

* **Health:** a sick animal is worth less (health 0..1), a dead one nothing.
* **Size bonus:** fish and inverts sold bigger than they were bought are worth more: × (length now / juvenile length),
  between 1 and 1.8. Corals: × colony size, between 0.5 and 2.
* **Wanted bonus:** × 1.5 when the store wants that species today.
* **Too small to sell:** a baby that is still smaller than the store's juvenile size shows "Too little, grows in N days"
  instead of an offer, so babies are raised before they are sold.
* Because buy-back is always under 100 %, buying and instantly selling always loses money. Profit comes only from
  growing and breeding, which take real days.

### Things you raise to sell

| Thing | How you get it | Typical offer |
|---|---|---|
| **Anemone split** | a well-fed bubble tip anemone splits in two (already in the sim) | 40-55, ~80 when wanted |
| **Coral frag** | new **Frag** button on a big coral: cut a piece, the colony shrinks a little, 3-day rest per colony | 30-40 % of the coral's price, ×1.5 at Frag Shack wanted |
| **Kenya branch** | Kenya trees drop branches by themselves (already in the sim) | like a frag |
| **Baby fish you raised** | clownfish, cardinal and blue hippo tang babies from the nursery tank, once they are old enough to sell (they reach the store's juvenile size, the size stores sell that fish at) | the species' price × buy-back, so a shop-size baby clown ~10-15 and a baby hippo tang ~40-50; the whole batch can be sold at once |
| **Grown-up fish** | buy small, keep it healthy for weeks | up to 1.8× its juvenile value |

Selling your named pets asks "Say goodbye to Nemo?" first, so nobody sells a favourite by accident.

## 7. Screens

Kept as simple as the rest of the kid-friendly UI: big pictures, one number, one button per card.

1. **Money chip** (My tank only): sand dollar + amount + level star. A small dot when there is a task or a bonus to
   collect. Coins fly into it whenever money comes in.
2. **Care card** (tap the chip): today's tasks with a check each and what they pay, the all-done bonus, the streak
   flames, the happy tank Collect button, the level bar, and Goals below.
3. **Stores** (replaces "Add life" in My tank): a street of four storefront pictures
   rendered from the Blender models. Locked stores are dark with "Opens at level 4".
4. **Inside a store:** the storefront's greeting, then the display tanks in the 3D view (see "Visiting a store"):
   section tabs on top, the animal strip and the shop bag below, the door to leave. A **Sell** button on the counter
   lists your animals with the store's offer for each; the wanted ones float to the top.
5. **Rewards feel like a game:** every payment sends sand dollars bubbling up out of the tank, wobbling along a curve
   into the money chip, which gulps and counts up while a big "+15" floats away. A finished job's tick pops. A level-up
   dims the water, rushes bubbles and a little school of fish past, and pops a big glowing star with what just opened.
   All of it respects "reduce motion". (Android `RewardFx.kt`, web `care.js`.)
6. **Hermie** (the hermit crab helper) explains each new thing once: the first task, the first store, the first sale.

Android (Compose) and web (`web/site`) get the same screens and words.

## 8. Fish store models (Blender)

Each store is a small diorama built in `tools/blender/stores.py` (one script, four stores), exported to
`app/src/main/assets/models/store_*.glb` and rendered to the storefront pictures the screens show. Low poly, warm
lit, readable as tiny thumbnails:

* **Shelly's Pet Shop:** mall-style shop front with a striped awning, a big window with three glowing fish tanks, a
  hamster wheel in the corner, a shell-shaped sign.
* **Frag Shack:** a garage with the door rolled up, rows of frag tanks under purple-blue reef lights, a hand-painted
  sign, a surfboard by the door.
* **Big Blue Aquatics:** a big blue box store with a wall of tanks, a giant fish mural and a neon sign.
* **Deep Reef Imports:** a dark glass shop front, one large lit display tank with a rare fish, gold lettering.

**Interiors** (`tools/blender/store_rooms.py` → `store_room_<id>.glb`, drawn by `render/StoreRoom.kt` with
`store.frag`): the same modules in each store's colours and light: a 180 cm **Bay** (tiled floor, back wall with a
stripe and a framed fish poster, ceiling panel, a hanging tank light), the **Stand** under every tank, a stocked
**PropTank** for the tanks you are not standing at, the **Counter**, the store's **Sign** over the first tank and
**EndL / EndR** walls closing the aisle. The renderer repeats them along x around the live display tank (which stays
at the origin), so a store with any number of sections needs no new model. Shelly's is warm cream and teal, Frag
Shack a dark garage under blue-violet lights, Big Blue bright white and blue with a neon sign, Deep Reef black and gold.

## 9. While you are away

The tank keeps living when the app is closed, like a real one: fish grow, get hungry, make waste; algae grows;
nitrate climbs. Today the catch-up on reopening stops at 24 hours and an automatic feeder (Tee's real feeding times)
quietly feeds My tank, so nothing ever goes hungry. Both change:

* **No hidden feeder in My tank.** The keeper feeds (it is a daily job). A store "auto feeder" can come later.
* **The whole time away counts,** up to 30 days. A long catch-up runs as a short "while you were away" fast-forward
  on the welcome-back card (a day counter racing up, the tank dimming through the nights), a few seconds at most, in
  coarse steps with the animals' behaviour switched off, as the catch-up does now.
* **Hunger is research-paced.** Fish get hungry within a day, then lose condition at their species' starvation rate
  (`starvationDays` in the species sheets: most reef fish 3-6 weeks to death without food, small fast ones sooner).
  Hungry fish get stressed and their immunity drops, so a week without food often brings disease first. Grazers,
  snails, hermits and filter feeders get by on algae, film and detritus.
* **The water and the gear keep going too.** Nitrate and phosphate climb, water evaporates and salinity rises, the
  skimmer cup fills and overflows back in, the filter sock clogs and rots what it caught, carbon runs out, algae
  spreads. All of this is already in the chemistry; the long catch-up just lets it run. Bad water stresses animals,
  makes them sick and can kill them, the same as hunger.
* **Grace period.** For the first **2 days** away nobody can die from hunger or slowly worsening water alone (a
  sudden disaster, like a heater stuck on, is still dangerous, as in real life).
* **Welcome back.** On reopening after more than a few hours, a card says what happened while you were gone, in plain
  words: "You were away 3 days. Nemo and Marlin are very hungry! The skimmer cup overflowed and the filter sock is
  clogged. Nitrate went up to 25. Algae grew on the glass." with one big **Take care of it** button that opens the
  Care card (feed, clean). Deaths are listed gently, with what caused them (hunger, bad water, sickness), and the net
  takes them out.
* **No spiral.** Being away costs nothing but the daily pay you missed, the streak, and animals that were neglected
  too long. If the tank ends up empty, Shelly's free starter (section 2) gets you going again.

## 10. Saving

The wallet, stars, streak, today's tasks, goals and the stores' "sold out today" counts are saved in My tank's save
as one new field (`CustomTankSave.economy`). It has defaults, so existing tanks open with 60 sand dollars and
level 1, and the animals already in them are simply theirs (no codec version bump: an added defaulted field needs no
migration, as with `brood`). Daily things are keyed by the local date (`yyyy-mm-dd`), so a phone clock change can at
worst re-roll one day's list, never pay twice for a task.

## 11. Code layout

* `core/custom/economy/`: `PriceBook` (prices, levels), `Stores` (catalogues, daily stock, deals, wanted),
  `CareTasks` (daily list from the tank check + chores, done detection from commands and sim events), `Levels`,
  `Goals`, `Wallet` (all money in and out, with a short ledger for the "where did my money go" list), `Offers`
  (sell prices). Pure Kotlin, no clocks inside: the hosts pass today's date and the wall time.
* Hosts (Android `SimulationHolder` / `LiveTankViewModel`, web `MyTank.kt`) feed every My tank command and sim event
  to the economy and save it with the tank.
* Screens: Android `ui/live/StoreScreens.kt` + `CareCard.kt`; web `site/stores.js`.

## 12. Build order (each one its own update)

1. **Earn:** sand dollars, levels, the daily care list, goals and the Care card. (Everything still free to add.)
2. **Fish store models:** the four Blender stores and their pictures.
3. **Buy:** the stores with prices, stock, deals and locked animals; My tank's Add life became the Shop button (Tee).
4. **Sell:** the Sell tab, offers, wanted lists, coral fragging, selling raised baby fish.
5. **Away time:** the full catch-up, hunger while away and the welcome-back card.

## 13. Later ideas (not in the first version)

* Buying equipment (a bigger skimmer, a refugium, a better light) and tank upgrades with sand dollars.
* Pest control and supplies as store products (prices from the pest thread's research): Berghia nudibranchs, pack of
  5, 65 (level 2); Aiptasia-X 25, Joe's Juice 10, kalkwasser paste 5 (level 1); an aiptasia laser 150 (level 4); a
  mesh lid about 40 at Big Blue (firefish and fairy wrasses jump).
* A store "order" quest: "Big Blue wants 3 zoa frags by Friday" for a big payout.
* Seasonal store events (a holiday sale, a rare fish week).

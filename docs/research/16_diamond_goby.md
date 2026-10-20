# Diamond goby and the moving sand bed (research 16)

Research update: 2026-10-13. These notes support SIMULATION §16. The diamond goby was first added as a sandbox species; since 2026-10 it is in the owner's tank (see video/fish_diamond_goby.md). Each note says whether it is **observed** (from a species account or a study) or a **design estimate** (a provisional model value).

## 1. Identity and size

*Valenciennea puellaris* (Gobiidae; the trade names are diamond goby, diamond watchman goby, orange-spotted sleeper goby, and maiden goby).

- **Size.** FishBase gives a maximum of 20 cm SL. Fishes of Australia gives 18 cm SL. Hobby accounts give 5–7 in (13–18 cm), with 6 in typical in aquaria. The model uses 16 cm maximum and 12 cm typical. [FishBase](https://www.fishbase.se/summary/7246); [Fishes of Australia](https://fishesofaustralia.net.au/Home/species/177); [LiveAquaria](https://www.liveaquaria.com/product/220/?pcatid=220); [aquariumsource](https://www.aquariumsource.com/diamond-goby).
- **Colour.** Pearly white to pale grey. Orange spots and dashes run along the side, with a bold lower row. The head has blue dashes and spots, and the cheek has a blue-spotted stripe. [Fishes of Australia](https://fishesofaustralia.net.au/Home/species/177); [LiveAquaria](https://www.liveaquaria.com/product/220/?pcatid=220).
- **Shape.** An elongate sleeper goby with two separate dorsal fins and a rounded to lanceolate tail. This is what `diamond_goby.glb` models.

## 2. How it feeds

**Observed.** *Valenciennea* engulf mouthfuls of sand and expel the sand through the gill openings. Gill-raker structures retain small crustaceans, worms and other invertebrates. [Wikipedia: Valenciennea](https://en.wikipedia.org/wiki/Valenciennea); [fishprofiles](https://fishprofiles.com/profiles/marine/Blennoid_Gobioid/Valenciennea_puellaris).

**Observed (hobby).** It sifts all day, moving from spot to spot. It keeps the top layer of the sand turned and clean, and "can move most of the sand in a tank in a day". [fishprofiles](https://fishprofiles.com/profiles/marine/Blennoid_Gobioid/Valenciennea_puellaris); [LiveAquaria](https://www.liveaquaria.com/product/220/?pcatid=220).

**Design estimates:**
- mouthful 0.35 cm³;
- scoop 0.6 s, chew 1.5–3.5 s;
- 3–8 mouthfuls per station, then a hop of 8–25 cm;
- forages within 35 cm of the burrow.

None of these is measured. The behaviour layer checks them only as patterns, through `SandBedTest`.

**Observed (2026-10-20 update).** Aquarium trials on *V. sexguttata* and *V. strigata* found the sorting is done inside the
head: a specialised epibranchial lobe, the pharyngeal jaws and dense papillose taste buds in the oropharyngeal cavity
separate small prey from the heavier sand grains, and the prey is selected by size, shape and density. [Univ. Copenhagen
news](https://www1.bio.ku.dk/english/news/news/what-is-going-on-in-the-coral-food-web/). In the best-studied sediment
sifters (geophagine cichlids) one feeding event has three parts: a stereotyped strike into the substrate, a variable
rhythmic winnowing stage with rapid reversals of water flow inside the mouth, and a stereotyped ejection of the cleaned
sediment through the gill openings. [Weller et al. 2017, *Satanoperca daemon*](https://hiweller.rbind.io/publication/weller-dirt-sifting-2017/).
No high-speed kinematics are published for *Valenciennea*; the sim borrows the cichlid phase structure.

**How the sim draws one mouthful** (`DiamondGobyBrain.Sift`, design estimates):
- strike 0.35 s: a 1.2 cm lunge forward and down to ~35° nose-down, mouth wide open, the snout 3.5 mm into the bed;
- bite 0.25 s: the mouth closes on the sand;
- lift 0.45 s and winnow 1.5–3.5 s: head slightly nose-up just over the sand, mouth almost shut with a small 2.5 Hz jaw
  pump, gill covers working, fines seeping from both gill slits;
- eject: three gill pumps 0.16 s apart pour the sand out of both gill openings, then a 1–2 cm shuffle to the next bite.
The body is drawn as one smooth curve from the head (the skull at the head's pitch, the tail easing back to nearly
level, never in the sand), and the head only moves forward or up and down.

## 3. Burrows and pairs

**Observed.** Wild fish live in monogamous pairs in a shallow burrow they dig under rubble or a rock. FishBase gives a depth range of 2–84 m. [FishBase](https://www.fishbase.se/summary/7246); [Fishes of Australia](https://fishesofaustralia.net.au/Home/species/177).

**Observed (hobby).** In aquaria it digs under rocks and retreats there at night and when frightened. Mated pairs get along. Two unpaired adults fight. [fishprofiles](https://fishprofiles.com/profiles/marine/Blennoid_Gobioid/Valenciennea_puellaris); [aquariumsource](https://www.aquariumsource.com/diamond-goby).

**In the sim.** The pair shares a burrow and feeds within 14 cm of each other (a design estimate). The `diamond_goby_pair` warning fires when a third goby is added.

## 4. Starvation without sand fauna

**Observed (hobby, repeated across sources).** It needs a mature, deep sand bed with abundant pods and worms. It "will starve" in a new or small tank, and it depletes the bed's fauna over weeks. Fish that learn to take sinking frozen foods do better. Feed several times a day. Advice on minimum tank size ranges from 30 gal (LiveAquaria) to 55 gal (aquariadise). The sim warns below 200 L. [aquariadise](https://aquariadise.com/diamond-goby/); [LiveAquaria](https://www.liveaquaria.com/product/220/?pcatid=220); [aquariumsource](https://www.aquariumsource.com/diamond-goby).

**In the sim:**
- The sand-fauna index (`sandPreyIndex`) feeds the goby, and the `SAND_SIFTER` pod-predator row (1,500 pod-eq a day, the mandarin's order) draws the bed down.
- In the 227 L test tank with no prepared food, it holds condition for about 10 days, then thins. On a sterile bed it is thin within the fortnight.
- 60 % of fish accept prepared food. These are design estimates, and the timing claim is not measured.

## 5. Sand on corals

**Observed (hobby).** The sand blown out of the gills lands on corals placed low on the sand bed, and those corals can be smothered or irritated. [aquariadise](https://aquariadise.com/diamond-goby/); [aquariumsource](https://www.aquariumsource.com/diamond-goby).

**In the sim.** A puff dusts any coral within 7 cm that sits less than 8 cm above the bed, and the coral retracts (`disturb`). Interactions: zoanthids and mushrooms are rated M, GSP is rated L.

## 6. Sand grain physics

- **Grain sizes** of the reef sands in common use: CaribSea Special Grade 1–2 mm, Fiji Pink 0.5–1.5 mm, Bahamas Oolite 0.25–1 mm. The sim's "sand" class is 0.5 mm, "fine" is 0.2 mm, and "silt" is 63 µm (the sand/silt boundary). [BulkReefSupply / CaribSea](https://www.bulkreefsupply.com/special-grade-arag-alive-live-reef-sand-caribsea.html).
- **Settling velocity.** Ferguson, R. I. & Church, M. (2004), "A simple universal equation for grain settling velocity", *J. Sediment. Res.* 74:933–937, with C1 = 18 and C2 = 1 (natural grains). Inputs: aragonite 2.93 g/cm³, seawater 1.023 g/cm³ and ν = 0.95 × 10⁻⁶ m²/s at 25 °C.
- **Detritus floc.** Modelled as 0.3 mm at 1.10 g/cm³, which settles at ≈ 0.3 cm/s (≈ 260 m/day). This is inside the 16–368 m/day range measured for marine snow by Alldredge & Gotschalk (1988), *Limnol. Oceanogr.* 33:339–351.
- **Angle of repose** of wet sand ≈ 32° (tan 0.62). This is the typical value for medium sand and is not measured on aragonite.

## 7. Open questions

- No measured mouthful rate, mouthful volume, or prey yield per mouthful for *V. puellaris*.
- No measured figure for how fast a single goby depletes the fauna of a given sand area. The 1,500 pod-eq/day intake is borrowed from the mandarin.
- Burrow geometry and how often the burrow is rebuilt in aquaria.

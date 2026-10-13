# Brittle star and Sally Lightfoot crab: bodies, gaits and behaviour (research 17)

Research update: 2026-10-13. These notes support SIMULATION §19 (the crawler limb rig) and the rebuilt `brittle_star.glb` and `sally_lightfoot.glb`. They extend `video/invert_brittle_star.md`, `video/invert_sally_crab.md`, research 11 and research 15 (§14, §15). YouTube was not reachable from the build machine, so the motion notes come from published kinematics and written observations rather than from watching clips.

Confidence tags: **[H]** measured in a study or consistent across several good sources; **[M]** one good source, or a close relative's data applied to the tank animal; **[L]** hobby report or design estimate.

## 1. Which animals these are

- **Brittle star.** The tank animal is sold as a "black brittle star". The usual trade animal under that name is *Ophiocoma erinaceus* (Ophiocomidae), which is uniformly black above and has long, stout arm spines. The species card keeps the id `ophiocoma_sp` because no close photograph of the tank animal exists (`tank_full.jpg` does not show it). [M] [mexican-fish.com](https://mexican-fish.com/black-brittle-star/); [fishkeeper](https://www.fishkeeper.co.uk/fish/marine/miscellaneous-invertebrates/brittle-star); research 15 §15.
- **Sally Lightfoot.** The owner's reference photo (`reference/ref_sally_lightfoot.jpg`) shows the features of *Percnon gibbesi* (Plagusiidae):
  - an extremely flat, rounded maroon carapace with a green-yellow midline and paired radiating lines;
  - long, flattened maroon legs with yellow-gold blotches and yellow joint rings;
  - small red-orange claws with pale tips.

  This is the reef-aquarium "Sally Lightfoot". *Grapsus grapsus*, the Galápagos crab of the same name, is a different animal. [H] [Wikipedia: Percnon gibbesi](https://en.wikipedia.org/wiki/Percnon_gibbesi); [seanature](https://www.seanature.co.uk/pmnhs_percnon.html); [LiveAquaria](https://www.liveaquaria.com/products/sally-lightfoot-crab).

## 2. Black brittle star (*Ophiocoma erinaceus*; behaviour from *O. echinata* and relatives)

### 2.1 Body

- **Proportions.** The disc is about 25 mm across. Each arm is 3–4 disc diameters long (up to 100 mm), which gives a span of about 22 cm. The model has a disc 0.112 of the span and arms 0.45 of the span from the disc-arm junction (the core constants `ROOT_R` 0.05 and `ARM_LEN` 0.45). [M] [mexican-fish.com](https://mexican-fish.com/black-brittle-star/); [Wikipedia: O. echinata](https://en.wikipedia.org/wiki/Ophiocoma_echinata).
- **Disc.**
  - The disc is pentagonal and notched between the arms.
  - Its surface is covered in coarse granules.
  - Each arm base has a pair of radial shields. [H] [Devaney, Micronesica](https://micronesica.org/sites/default/files/devaney-8.pdf).
- **Arms.**
  - Each arm is a chain of short vertebral segments, with dorsal arm plates separated by dark joints.
  - The arms are robust and slightly flattened, and they taper to a whip tip.
  - The arm spines are long and stout, and they shorten toward the tip. [H] [Devaney](https://micronesica.org/sites/default/files/devaney-8.pdf).
- **Colour.**
  - The upper side is charcoal black all over. Some trade animals show faint lighter bands every few segments, more distally.
  - Underneath, some animals have pale marks on the ventral arm plates.
  - The tube feet are orange-red. [M] [mexican-fish.com](https://mexican-fish.com/black-brittle-star/).
- **Arm flexibility.** The arms bend along their whole length but cannot twist much. When walking, an arm changes length by less than 20–30 % (Astley 2012). [H]

### 2.2 Rowing (Astley 2012, *O. echinata*: 13 animals, 56 cycles)

[Astley 2012, J Exp Biol 215:1923](https://journals.biologists.com/jeb/article/215/11/1923/11010/Getting-around-when-you-re-round-quantitative) ([pdf](https://blogs.uakron.edu/astleylab/wp-content/uploads/sites/1471/2018/10/Astley-2012-Getting-around-when-youre-round.pdf)).

- **Two gaits.**
  - **Rowing** (75 % of cycles): the centre arm points along the travel. The two arms either side of it make mirrored power strokes, and the two rear arms trail.
  - **Reverse rowing** (25 %): the centre arm trails and the other four push. [H]
- **Speed and rhythm.**
  - Speed is 1.3 ± 0.5 cm/s rowing and 1.5 ± 0.6 cm/s reverse rowing.
  - The cycle runs at 0.23 ± 0.08 Hz, and the animal moves 5.7 cm per cycle.
  - The disc moves for 42–46 % of each cycle and is still for the rest. [H]
- **Arm sweep.**
  - Each forearm sweeps about 45° per stroke (+3° protraction, −30° retraction).
  - The centre arm moves 25–32°.
  - The hind arms move about 13°.
  - The left and right forearms are within ±13 % of a cycle of each other. [H]
- **Turning.** The disc does not rotate. To change direction, the animal makes a different arm the centre arm during a static disc phase. The gait mode (rowing or reverse rowing) stays the same within a bout. [H]
- **Control.**
  - In *Ophiarachna incrassata*, two forearms do the propulsion and the hind arms add little (Kano et al. 2019, [Front Neurorobot 13:104](https://www.frontiersin.org/articles/10.3389/fnbot.2019.00104/full)).
  - In *Ophiactis brachyaspis*, the leading arm is the second neighbour of a stimulated arm, and the back arms row weakly (Wakita et al., [bioRxiv 616383](https://www.biorxiv.org/content/10.1101/616383v1.full.pdf)).
  - A detached arm does not walk on its own (Kano et al. 2019). [H]
- **Bending waves.** The arms bend most near the disc, and bending waves pass from root to tip while an arm searches (Kano 2019; Astley 2012 video stills). [M]

### 2.3 Day and night

- **Day.** By day, *Ophiocoma* hide under rock and rubble with only 1–3 arm tips out of the crevice. [H] [Micronesica 19, 1983](https://micronesica.org/sites/default/files/habitat_and_feeding_observations_on_species_of_ophiocoma_micronesica_vol._19_no._1-2_dec._1983.pdf); research 11.
- **Night.** They come out to forage at night, or with the tide on reef flats (Oak & Scheibling 2006, *O. scolopendrina*). [H]
- **In the tank.** Aquarists see the same thing: the star is out at lights-off or when food goes in. [L] [fishkeeper](https://www.fishkeeper.co.uk/fish/marine/miscellaneous-invertebrates/brittle-star).

### 2.4 Food

- **Response to food (Chartok 1983, Micronesica).**
  - About 2 minutes after food went in, *O. anaglyptica* became active.
  - The arms reach out first, and the disc follows.
  - An arm tip loops round a food item.
  - The tube feet pass a bolus along the arm to the mouth. One particle travelled 10 cm in 8–15 s. [H] [Micronesica 19](https://micronesica.org/sites/default/files/habitat_and_feeding_observations_on_species_of_ophiocoma_micronesica_vol._19_no._1-2_dec._1983.pdf).
- **Other ways of feeding.**
  - Arms raised into the current collect particles in a mucus net (Fontaine 1965, *Ophiocomina nigra*, [JMBA 45](https://www.cambridge.org/core/journals/journal-of-the-marine-biological-association-of-the-united-kingdom/article/feeding-mechanisms-of-the-ophiuroid-ophiocomina-nigra/DC61711E2E261101F169A7E9934B2595)).
  - *O. scolopendrina* skims the surface film with its arms. [M]

### 2.5 Escape

- A touched star pulls its arms in and runs for shelter quickly, often by reverse rowing. [H] [BIOS 85:187 (2014)](https://bioone.org/journals/bios/volume-85/issue-4/0005-3155-85.4.187/Effect-of-arm-loss-on-escape-speed-of-two-epibenthic/10.1893/0005-3155-85.4.187.full).
- Losing an arm reduces escape speed only a little. [M]

### 2.6 Autotomy and regrowth

- **How common.** Up to 47 % of wild *O. echinata* have arms missing or regrowing. [H] [Wikipedia: O. echinata](https://en.wikipedia.org/wiki/Ophiocoma_echinata).
- **What happens.** A star that is bitten or pinned can drop the distal part of an arm and escape. [H]
- **Regrowth.**
  - Regrowth is slow: about 0.4 mm a day in *O. scolopendrina* (Soong 1997).
  - Regrowing three arms of *O. echinata* takes about 2 years. [M]

### 2.7 How the sim uses this

| Finding | Where in the sim |
|---|---|
| Rowing (75 %) and reverse rowing (25 %), 0.23 Hz cycle, disc moves 45 % of the cycle | `BrittleArms` `REVERSE_SHARE`, `MotionProfile.rowHz`, `DUTY`, the disc pulse |
| Forearm sweep about 45°, hind arms about 13°, contralateral lag | `FORE_SWEEP` 0.78 rad, `HIND_SWEEP` 0.22 rad, 0.04-cycle lag |
| A turn is a new centre arm and the disc never rotates | lead switching in the static phase; `Steering.crawl` parallel transport |
| Walking speed 1.3 cm/s, burst about 4 cm/s | `MotionProfile` walk 1.5 cm/s, burst 4.0 cm/s |
| Hidden by day with 1–3 tips out | `HideUnder`, `outArms` = 1–3 per animal, toward the cave mouth |
| Arms first, then the disc, about 2 min after food | `RaceToFood` scent delay, then 5–10 s with only the arms out |
| Arm loops food and passes it to the mouth (10 cm in 8–15 s) | `ArmFeed`, tip curl from `feedLoop` |
| Fast retreat when touched | `Retract`, with all arms withdrawn at 6/s |
| Autotomy when bitten; regrowth 0.4 mm/day | `onAttacked` (p 0.5), `BrittleArms.shed`, `regrow` |

## 3. Sally Lightfoot (*Percnon gibbesi*)

### 3.1 Body

- **Size and shape.**
  - Carapace length is 11–38 mm. The largest male measured CL 38.2 mm and CW 39.9 mm, so the carapace is a little wider than long.
  - The carapace is extremely flat. [H] (Fanelli & Azzurro; [Wikipedia](https://en.wikipedia.org/wiki/Percnon_gibbesi)).
- **Legs.**
  - The walking legs are long and flattened, with a row of spines on the leading edge of each merus.
  - They have yellow rings at the joints. [H] [Wikipedia](https://en.wikipedia.org/wiki/Percnon_gibbesi); [seanature](https://www.seanature.co.uk/pmnhs_percnon.html).
- **Claws.** The chelae are small and equal. [H]
- **Colour (owner photo).**
  - The carapace is maroon with a green-yellow midline and paired radiating lines.
  - The legs are maroon with yellow-gold blotches and yellow at the joints.
  - The claws are red-orange with pale tips. [H]
- **Leg count.** There are four pairs of walking legs and one pair of chelipeds. [H]

### 3.2 Gait

- **Footfall pattern.** Crabs walking sideways step in an alternating tetrapod: L1 R2 L3 R4 alternates with R1 L2 R3 L4, and the chelae stay off the ground (Barnes 1975, *Uca pugnax*, [J Comp Physiol 96](https://link.springer.com/article/10.1007/BF00612697)). [H]
- **Leading and trailing legs.**
  - The legs on the side the crab walks toward (leading) flex and pull.
  - The legs on the other side (trailing) extend and push (Clarac & Coulmance 1971, *Carcinus*). [H]
- **Footfall order.** Footfalls run from back to front within each side (Herreid & Full 1986, a land hermit crab). [M]
- **Speeding up.** Crabs speed up mostly by stepping faster. In ghost crabs, stride frequency scales with mass^−0.15. [M] ([Heglund & Taylor 1988](https://www.originalwisdom.com/wp-content/uploads/bsk-pdf-manager/2019/10/Heglund-and-Taylor_1988_Speed-and-stride-frequency-and-energy-cost-per-stride-how-do-they-change-with-body-size-and-gait.pdf)).
- **Sim values (design estimates).** A stride is 0.45 carapace widths, at 1.5–7 Hz, with a 0.06-cycle rear-to-front lag. [L]

### 3.3 Food

- **Diet.**
  - Stomach contents, chela shape and gastric mill all show a strictly herbivorous diet in the Sicily Strait (Puccio et al. 2006, [Hydrobiologia](https://www.researchgate.net/publication/225304425_Feeding_Habits_of_Percnon_gibbesi_H_Milne_Edwards_1853_in_the_Sicily_Strait)).
  - Elsewhere, 43 % of gut contents were animal matter (Cannicci et al. 2004).
  - Diet follows what is available ([Ecol Evol 2025](https://onlinelibrary.wiley.com/doi/10.1002/ece3.71686)). [H]
- **How it feeds.** The crab scrapes biofilm with the tips of its chelae and tears at algae, "rapidly tugging", with the two claws working in turn. [H] (Sciberras & Schembri 2008, [pdf](https://www.um.edu.mt/library/oar/bitstream/123456789/5456/4/Biology%20and%20interspecific%20interactions%20of%20the%20alien%20crab%20Percnon%20gibbesi%20in%20the%20Maltese%20Islands.pdf); [seanature](https://www.seanature.co.uk/pmnhs_percnon.html)).
- **In the tank.** It is a hair-algae grazer and scavenges dropped food. Reports of it catching fish are anecdotal, which is why `crab_predation` stays a sandbox toggle. [L] research 15 §14.

### 3.4 Activity and shyness

- **When it is active.** Activity peaks at dusk and sunset (Cannicci et al. 2004; Deudero et al. 2005). Density on boulders doubles in the late afternoon (Sciberras & Schembri 2008). [H]
- **Shelter.** It lives in boulder fields and crevices. It runs into a crevice at the slightest disturbance and climbs rock faces. In the tank it also climbs the glass. [H]/[L]
- **Rivals.** It loses encounters with *Pachygrapsus* crabs. [M]

### 3.5 Molting

- Like all crabs, it molts. After a molt it stays hidden for a day or two while the new shell hardens. [M] research 11.
- The sim keeps it more hidden for 48 h after a molt, through `softShell()`. [L]

### 3.6 How the sim uses this

| Finding | Where in the sim |
|---|---|
| Alternating tetrapod, half a cycle apart, rear-to-front lag | `CrabLegs` group = (pair + side) mod 2, `METACHRONAL` 0.06 |
| Leading legs pull and trailing legs push | sign of the extension stroke from the travel against the body's right |
| Speeds up by stepping faster | stride Hz = speed / (0.45 L), clamped to 1.5–7 |
| Alternating claw strokes while grazing and scavenging | claws 8/9 half a cycle apart, `PICK_HZ` 1.6, `pickStrokes` |
| Shy: darts into a crevice, then stays hidden and wary | `Dart` (min 8 s), the 60 s wary boost to `HideCrevice`, tucked legs |
| Climbs rock and glass, grazes the glass at night | `allowGlass`, `glassSpot()` 15 % at night |
| Soft after a molt | `softShell()` 48 h, adds to the hide score |

## 4. Open questions

- Get a close photograph of the tank's brittle star, showing the disc and the ventral arm plates, to confirm *O. erinaceus*.
- Measure the crab's stride frequency from video. It is an estimate until then.
- Find out whether the tank's *Percnon* ever takes live tankmates. The default stays herbivore and scavenger, with predation available only as the sandbox toggle.

# Life stages and sex change: Tee's fish

Research for the juvenile / adult forms and sex roles in the sim (core `sim/LifeStages.kt`, species sheet
`lifeStages` blocks, app `render/LifeStageVisuals.kt`, textures from `tools/blender/life_stages.py`).

**Tags.** **[M]** a published or institutional source (cited by key, list at the end). **[L]** our estimate or a
hobby/retail account; used where nothing better exists, and tunable. **[P pNN]** what Tee's 2026-10-13 photos show
(`reference/photos_2026-10-13/pNN.jpg`).

All lengths are total length (TL). "Stage from" values are the `fromCm` thresholds in the sheets; the visual blend is
centred on each threshold and `blendCm` wide, so a growing fish fades from one pattern to the next over weeks to
months of sim time (growth runs at `growthCmPerYear` in `Simulation.growth`).

## Summary

| Fish | Stages in the sim (from cm) | Visible change with stage | Sex system | Visible change with sex | Sex-change speed in the sim |
|---|---|---|---|---|---|
| Ocellaris clown (orange) | juvenile 0, adult 4 | thin black bar edging, paler orange → full black edges | protandrous | size only (female largest) | 28 d (existing) |
| Ocellaris clown (black / Darwin) | juvenile 0, adult 4 | orange-brown → black body, orange face | protandrous | size only | 28 d |
| Blue tang | juvenile 0, subadult 9, adult 15 | brighter blue, deeper/rounder body → deeper royal blue, longer body | gonochoristic | none | — |
| Pajama cardinal | juvenile 0, adult 3.5 | lighter body, small dark spots → red-orange spots | gonochoristic (♂ mouthbroods) | ♂ larger mouth | — |
| Cleaner wrasse | juvenile 0, adult 3.5 | black with neon blue dorsal stripe → white/blue with black stripe widening to the tail | protogynous, bidirectional | none in colour | ♀→♂ 16 d, ♂→♀ 30 d |
| Engineer goby | juvenile 0, subadult 8, adult 15 | black with pale lengthwise stripes, stubby, schooling → saddles and cream bands → mottled brown, eel-like, burrow-bound | unknown | unknown | — |
| Falco hawkfish | juvenile 0, adult 3.5 | paler, fewer spots [L] | protogynous, bidirectional | none | ♀→♂ 30 d, ♂→♀ 60 d [L] |
| Lawnmower blenny | juvenile 0, adult 4.5 | paler, clearer bars [L] | gonochoristic | ♂ long anal spines; courtship spots | — |
| Tailspot blenny | juvenile 0, adult 3.5 | paler [L] | gonochoristic | ♂ brighter in courtship | — |
| Indigo dottyback (Electric Indigo, *P. fridmani × sankeyi*) | juvenile 0, adult 3.5 | black band less defined [L] | protogynous, bidirectional (genus) | none (monochromatic) | ♀→♂ 37 d, ♂→♀ 72 d |
| Blue star leopard wrasse | juvenile 0, initial phase 4 | white with orange mottling → orange-red, white spots, black-blue belly | protogynous | terminal ♂: green head bands, yellow saddle, red blotches | ♀→♂ 14 d [L] |
| Diamond goby | juvenile 0, adult 5 | long 3rd dorsal spine < 40 mm SL; fainter spots [L] | gonochoristic, monogamous | none | — |

---

## 1. Ocellaris clownfish, *Amphiprion ocellaris* (orange "Nemo" and black "Darwin")

| | Juvenile | Adult |
|---|---|---|
| Size | < ~4 cm | 4 cm → female up to 11 cm |
| Colour | The white bars appear during the larval-to-juvenile transition, always head to tail; a 15-day-old juvenile already shows the head and trunk bars with the tail bar forming [M S10, S11]. Black bar edging thin, orange paler [L]. | Three white bars with black edges, orange body [M FishBase]. |
| Black (Darwin) morph | Starts orange-brown and darkens [L S14]; retailers report the black filling in over ~12–18 months, by ~5 cm, and some fish keep an orange face [L]. | Black body, orange face/snout, white bars. |
| Behaviour | Settles into a host and a size-ranked group [M S13]. | Pair; the female guards the host, the male tends the eggs. |

**Sex.** Protandrous: every fish matures as a male; the largest fish in the group is the breeding female and the second
largest the breeding male; if the female is lost the male changes to female and the next fish moves up [M S13; FishBase
via research 02 §1C: sex change at ~4.4 cm]. Nothing changes in colour; the female is simply the largest. **Speed:**
the brain and behaviour change first; gonads lag by months [M S12 (Rhodes lab)]. First gonadal signs appear after 1–2
weeks and histological change takes about 3 months in subadults [M, ICAR/CMFRI studies summarised in S12].

**Sim rule.** Stage from length (juvenile < 4 cm, blend 3–5 cm). Sex from the seed role, else the larger is female
(`Reproduction.clownIsFemale`); with the female gone the largest male changes over 28 days (`Reproduction.SEX_CHANGE_DAYS`,
kept: it stands for the behavioural switch, which is quick; the gonadal lag does not show). Breeding needs ♀ ≥ 6 cm,
♂ ≥ 4.5 cm (existing). Textures: `clown_orange_juvenile`, `clown_darwin_juvenile` (orange-brown with patchy darkening).

## 2. Blue tang, *Paracanthurus hepatus*

| | Juvenile (< 9 cm) | Subadult (9–15 cm) | Adult (≥ 15 cm) |
|---|---|---|---|
| Colour | Royal blue with the black "palette", yellow tail; brighter, paler blue [L]. In the Indian Ocean form, young fish are a washed-out version of the solid blue Pacific form and the yellow belly comes with size [M S16]. | As adult. | Deeper royal blue; Indian Ocean adults have a bright yellow belly [M S16]. |
| Shape | Rounder, deeper body, relatively large eye [L]. | — | Compressed oval, longer [M Wikipedia/FishBase shape]. |
| Behaviour | Juveniles and subadults stay in groups near isolated *Pocillopora eydouxi* heads and dive into the branches when alarmed [M S15]. | same | Loose aggregations in the water column [M S15]. |

One source says juveniles are "bright yellow with blue spots near the eyes" [S17, Animal Diversity Web]; that matches
the yellow tang (*Zebrasoma*), not *Paracanthurus*, and contradicts photographs of young *P. hepatus*. **Rejected.**
The old spec (ASSETS §2.2) gave the juvenile a yellow belly; Tee's tang has none [P p09, p10]: it is the Pacific form,
and the stage textures drop the yellow belly at every size.

**Sex.** Gonochoristic, no external dimorphism [M FishBase "male/unsexed"; research 02 §2F]. No sex change.

**Sim rule.** Juvenile < 9 cm (blend 7.5–10.5 cm), body depth ×1.05; adult from 15 cm, depth ×0.96. Juveniles play
dead far more often (existing `TangBrain` rule, now keyed to the stage). Textures `tang_juvenile`, `tang_adult` (the
GLB's embedded juvenile texture with the yellow belly is no longer drawn).

## 3. Pajama cardinalfish, *Sphaeramia nematoptera*

| | Juvenile (< 3.5 cm) | Adult |
|---|---|---|
| Colour | Lighter body, smaller spots [M FishBase via research 02 §3A]; the spots are dark/black in small juveniles and turn red as they grow [L S28, retailer]. | Yellow head, dark band, silvery rear with red-orange spots, red eye. |

**Sex.** Gonochoristic; the male mouthbroods and has the larger mouth [M research 02 §3A, FishBase]. **Sim rule.**
Existing pair logic (`Reproduction.cardinalBreeding`, maturity 5 cm). Texture `cardinal_juvenile`.

## 4. Bluestreak cleaner wrasse, *Labroides dimidiatus*

| | Juvenile (< 3.5 cm) | Adult |
|---|---|---|
| Colour | Black with a neon blue stripe along the upper side, snout to tail [M S6, Reef Life Survey]; mimicked by the juvenile false cleaner *Aspidontus taeniatus* [M]. | Pale head, blue rear body, black stripe widening to the tail [M S6]. Blue saturation rises with size [M Frontiers 2023]. |
| Change | Small fish in juvenile colours switched to adult colours when isolated and back again when chased by an adult female; adult females attack small cleaners in adult colours more [M S8]. | — |
| Behaviour | Juveniles clean too and dance at visitors like adults; stations can be held by a group of juveniles [M S5]. | Pairs or a male with a harem of females hold stations [M S5]. |

**Sex.** Protogynous: all start female; the dominant female becomes male if the male disappears. Length at sex change
8.8 cm, sex reversal completed in 14–18 days, and bidirectional change is confirmed [M S5 FishBase]; a subordinate male
paired with a dominant male changes back to female [M S6]. No colour difference between the sexes [M research 02 §4F].

**Sim rule.** Juvenile < 3.5 cm (blend 2.5–4.5 cm). Juveniles search for clients within 0.6 × the adult radius
(`WrasseBrain.JUVENILE_SEARCH`, [L]: they hold close to a station). Harem: the largest of two or more becomes male over
14 days (`Reproduction.wrasseHarem`, existing; sheet value 16 d for reference); with two males and no female the
smaller changes back over 30 days [L] (`SexRoles.reverse`). Texture `wrasse_juvenile`.

## 5. Engineer goby (convict blenny), *Pholidichthys leucotaenia*

| | Juvenile (< 8 cm) | Subadult (8–15 cm) | Adult (≥ 15 cm) |
|---|---|---|---|
| Colour | Black with a white/cream stripe from the eye to the tail and a pale dorsal stripe [M S2, S4]; a Batesian mimic of the venomous striped eel catfish *Plotosus lineatus* [M S1, S2]. | At 49–108 mm the dark stripes break into rows of dark spots [M S3]: dark saddles split by pale bands. | Barred or spotted [M S1]; brown dark areas, whitish/yellowish/pale yellow-green light areas [M S3]. |
| Shape | Small and stubbier [L]. | — | Eel-like, very elongate [M S3]; up to 34 cm (FishBase) or more. |
| Behaviour | Tight schools "in formation to look like a single large body", under ledges or around coral heads [M S1]; leave the burrow by day to feed on plankton up to 50 m away and return at dusk; hang from the tunnel roof by mucus threads at night [M S4, citing Kaufmann 2005 / Clark]. | Larger juveniles in rocky areas [M S3]. | Secretive; stay in the tunnels, which can total ~6 m, and move sand out by the mouthful [M S1, S3, S4]. |

Colour change happens "over the course of the first year in captivity" at ~1 cm/month [M S4 citing McLean 2022, hobby].

**Sex.** Reproduction is undescribed: spawning mode and development are "mostly unknown" [M S3]. No sex is modelled
(`sexSystem UNKNOWN`; the request's "gobies may be bidirectional" applies to true gobies, see §11, and this fish is not
a goby).

**Tee's fish [P p02].** The goby lying on the sand in p02 is not a striped juvenile: dark chocolate-black saddles
above and below the lateral line, split by cream-yellow bands that sometimes join across it, cream mottling on the
face. That is the stripes-breaking-into-spots stage [M S3], so a **subadult**. Against the aragonite grains (≈ 3 mm)
it is roughly 10-13 cm long [L]. The preset (`DefaultStock`, 10 cm "juvenile with black/yellow stripes") now says
subadult and keeps 10 cm; the seed (`tee_tank.json`, was 8 cm JUVENILE) now says 10 cm, SUBADULT. 13 cm was tried, but
the larger goby draws enough cleaner-wrasse visits to push `RealismAcceptanceTest.c03` (tang cleaning minutes and
inspection length) out of range on two seeds, so the size stays at the low end of the estimate.

**Sim rule.** Juvenile < 8 cm (depth ×1.25, width ×1.1), subadult 8–15 cm (the GLB as built), adult ≥ 15 cm (depth
×0.85, width ×0.95), blend 4 cm. Adult behaviour (burrow-bound, rare night patrol) from the sheet's adult stage =
`GobyBrain.ADULT_CM` 15 cm. Striped juveniles hover in a tighter group (slot offsets ×0.55, separation 0.9 cm) than
subadults. Textures `engineer_goby_juvenile` (two pale stripes), `_subadult` (broad dark blocks with a dark head and
one cream band behind the eye, offset cream bands, from p02), `_adult` (mottled brown). Fins are staged too: the GLB
fin sheet (yellow ticks at the dorsal base) stays as the subadult; the juvenile gets plain black fins and the adult
mottled brown ones (`engineer_goby_fin_<form>.png`). In p02 the dorsal fin reads translucent dark rather than ticked,
so the GLB's subadult fin ticks may be stronger than Tee's fish [P p02, L].

## 6. Falco hawkfish, *Cirrhitichthys falco*

| | Juvenile (< 3.5 cm) | Adult |
|---|---|---|
| Colour | Same pattern, paler with fewer/smaller spots [L]. | White with dense orange-red spots and blotches. |

**Sex.** Haremic: each male's territory holds the territories of 2–3 females and he monopolises mating [M S18 Kadota
2009; Kadota et al. 2011]. Males are the largest fish in every harem and come from females (protogyny); over a 3-year
field survey sex change was confirmed **both ways**: female→male in 3 groups and male→female in 2, the reverse change
happening when all of a male's females disappeared [M S18]. No colour difference between the sexes [L]. Timing in the
field is not given [L: 30 d forward, 60 d reverse].

**Sim rule.** `SexRoles`: mature fish (≥ 4.5 cm) start female; with two or more and no male the largest changes to
male over 30 days; with two males and no female the smaller changes back over 60 days; a lone fish keeps its sex
(no social cue). Texture `hawkfish_juvenile`.

## 7. Lawnmower blenny, *Salarias fasciatus*

| | Juvenile (< 4.5 cm) | Adult |
|---|---|---|
| Colour | No source describes juvenile colour; paler and clearer bars assumed [L]. | Olive to brown, irregular dark bars, many white spots [M FishBase via S19 summary]. |

**Sex.** Gonochoristic with low dimorphism: males have elongated anal spines, no other external difference
[M S19 Awata et al. 2010]. In spawning condition the male shows orange spots in the dorsal fin [L hobby]. Breeding
season April–June in Okinawa [M S19]. **Sim rule.** No sex modelled visibly. Texture `lawnmower_blenny_juvenile`.

## 8. Tailspot blenny, *Ecsenius stigmatura*

Juvenile < 3.5 cm, paler [L]. Gonochoristic; males guard the eggs in a hole and brighten in courtship [L ADW
Blenniidae, hobby care guides]. **Sim rule.** No sex modelled visibly. Texture `tailspot_blenny_juvenile`.

## 9. Indigo dottyback: Electric Indigo, *Pseudochromis fridmani × P. sankeyi*

**Identity.** The captive-bred "Indigo" is ORA's hybrid *P. fridmani × P. sankeyi*; the "Electric Indigo" line has three
colour zones (light blue to turquoise top, sharply defined black band, white underside) and a caudal filament
[M S21]. Tee's fish matches the Electric Indigo (lavender-blue back, black band snout→eye→tail, white belly;
`reference/ref_dottyback.jpg`, ASSETS §2.9). The sheet id stays `pseudochromis_indigo`.

| | Juvenile (< 3.5 cm) | Adult |
|---|---|---|
| Colour | Same three zones, black band less sharply defined [L]. | As above; colour changes with mood/comfort [M S21]. |

**Sex.** In *Pseudochromis* most species are protogynous and sex change is bidirectional: in pairs of two females one
became male, in pairs of two males one became female, in all three species tested; female→male took 18–56 days,
male→female 52–93 days; in the dichromatic species colour followed sex, and colour does not predict the ability
[M S20 Wittenrich & Munday 2005]. Neither parent of the hybrid is dichromatic [L].

**Sim rule.** Mature ≥ 4 cm, start female; pair of females → larger becomes male over 37 days; pair of males → smaller
becomes female over 72 days (midpoints of S20). Texture `dottyback_juvenile`.

## 10. Blue star leopard wrasse, *Macropharyngodon bipartitus*

| | Juvenile (< 4 cm [L]) | Initial phase (female) | Terminal phase (male) |
|---|---|---|---|
| Colour | White with orange mottling and a pair of black spots on the dorsal and anal fins [M S22]. | Orange-red with white spots of variable size; a large black area over chest and belly holding a bright iridescent blue network and spots [M S22]. | Dull orange-red with dark-edged green diagonal bands on the head, a light yellow to orange area beneath the soft dorsal onto the peduncle, blue-edged dark reddish blotches forming an irregular band on the rear flank [M S22]. |
| Behaviour | Solitary or in pairs, females sometimes in small groups; picks small benthic invertebrates; burrows into the sand to escape danger (and at night) [M S22, S23]. | same | same; distinct pairing during breeding [M S23]. |

Max 13 cm TL [M S23]. **Tee's fish [P p06]:** orange-brown with white-blue spots and a black ventral patch with blue
markings: initial phase (female), ≈ 7–8 cm [L].

**Sex.** Protogynous (labrid; terminal males from females) [M S22 phases; L for this species' timing]. Speed by analogy
with the bluehead wrasse: after the terminal male was removed the largest female behaved as a male within minutes, the
colour change began within a day and was clear within four days, sperm after ~8 days [M S24 Warner & Swearer 1991];
dominant females court like males within ~2 days [M S31]. Sim: 14 days for the full colour change [L].

**Sim rule.** `macropharyngodon_bipartitus.json` `lifeStages`: juvenile 0, initial (form `female`) from 4 cm, blend 1.5,
protogynous, maturity 6 cm, change 14 days, `maleForm "male"` (the renderer mixes the female texture toward the male one
by the change progress). Its brain is `BrainKind.LEOPARD_WRASSE`, so `SexRoles` (not `Reproduction.wrasseHarem`)
drives the change. Textures (`tools/blender/life_stages.py`, model `leopard_wrasse`): `juvenile` and `male` are painted
stage forms (`textures/stages/leopard_wrasse_<form>.png`); the `female` base is the GLB's own photo-based texture
(`fish_species.paint_leopard_wrasse`, Tee's fish [P p06]), which replaced the literature-based female painter.

## 11. Diamond goby, *Valenciennea puellaris*

| | Juvenile (< 5 cm) | Adult |
|---|---|---|
| Shape | The third dorsal spine, the longest, is filamentous in juveniles under 40 mm SL (≈ 5 cm TL) [M S25]. | Pale grey body with rows of large orange spots (Pacific) [M S25]. |
| Colour | Fainter spots [L]. | — |
| Behaviour | — | Pairs share shallow burrows under rubble; monogamy obligate and social [M S25]; the female lays on the burrow ceiling and both parents guard [M S26]. |

**Sex.** No external difference between the sexes [M S26]; sex is told by the genital papilla in *V. strigata*
[M Choosri et al. 2025]. Bidirectional sex change is documented in true gobies such as *Gobiodon* and *Trimma*
[M S27 Munday et al. 1998], but not in *Valenciennea*, so none is modelled. **Sim rule.** Juvenile < 5 cm. Texture
`diamond_goby_juvenile`. The juvenile dorsal filament would need a fin change in the GLB [TODO, not done].

---

## Tee's tank against the research

| Fish | Preset length | Stage | Evidence |
|---|---|---|---|
| Clown "Nemo" | 8.0 cm | adult, female | role FEMALE |
| Clown "Marlin" | 6.5 cm | adult, male | role MALE |
| Blue tang | 7.5 cm | juvenile, no yellow belly | [P p09, p10] |
| Pajama cardinal | 6.0 cm | adult | |
| Cleaner wrasse | 8.0 cm | adult, female (alone, stays) | [P p06, p10, p11]: adult pattern |
| Engineer goby | 10 cm (photo ≈ 10-13 cm [L]) | **subadult** | [P p02] |
| Falco hawkfish | 6.5 cm | adult, female (alone) | |
| Lawnmower blenny | 10 cm | adult | |
| Tailspot blenny | 5.5 cm | adult | |
| Indigo dottyback | 6.0 cm | adult, female (alone) | |
| Leopard wrasse | 8.5 cm | initial phase (female) | [P p06] |
| Diamond goby | 10 cm | adult | [P p07, p08]: adult spotting |

Fish added from the sandbox at the juvenile stock size (0.3 × typical adult, floor 3 cm) land in the juvenile stage for
every species except the engineer goby, whose 10.5 cm is already in the stripes-breaking blend (§5): realistic for the
size, by design.

## Implementation map

- Data: `lifeStages` in `core/src/main/resources/species/*.json` (and the sim fixtures); `LifeStageSpec.PENDING` is the
  fallback for a sheet without a block (empty now).
- Core: `LifeStages` (stage, blend, sex role, card label), `SexRoles` (protogynous and reverse changes not covered by
  `Reproduction`), `LifeFormChannel` (per-snapshot `LifeForm` by organism id; a side channel because `SimTypes.kt` is
  being changed elsewhere: TODO move into `OrganismPose`).
- Behaviour hooks: tang play-dead (`TangBrain.juvenile`), goby adult mode and juvenile schooling (`GobyBrain`), cleaner
  wrasse juvenile search radius (`WrasseBrain`).
- Render: `LifeStageVisuals` + `fish.frag` `uAlbedoStage`/`uStageMix` + `fish.vert` `uStageShape`; textures in
  `app/src/main/assets/textures/stages/` with `manifest.json` (body `forms`/`base`, and optional `fins` forms; the
  engineer goby is the only species with staged fins).

## Sources

- **S1** FishBase, *Pholidichthys leucotaenia*. https://www.fishbase.se/summary/4433
- **S2** Australian Museum, "BBC Life: convict fish footage". https://australian.museum/learn/news/blog/bbc-life-convict-fish-footage/
- **S3** Monterey Bay Aquarium, Engineer goby. https://aquarium.org/animals/engineer-goby/
- **S4** Wikipedia, *Pholidichthys leucotaenia* (citing Kaufmann 2005, National Geographic; McLean 2022). https://en.wikipedia.org/wiki/Pholidichthys_leucotaenia
- **S5** FishBase, *Labroides dimidiatus* (length at sex change 8.8 cm; reversal 14–18 d; bidirectional). https://fishbase.se/summary/SpeciesSummary.php?id=5459
- **S6** Fishes of Australia, Common Cleanerfish. https://fishesofaustralia.net.au/home/species/250
- **S8** "The advantage of juvenile coloration in reef fishes" (*Labroides*), ScholarSpace. https://scholarspace.manoa.hawaii.edu/items/a666738f-59bf-4459-9c6f-b38ae9a2c8df
- **S10** Salis P. et al. 2018. Ontogenetic and phylogenetic simplification during white stripe evolution in clownfishes. BMC Biology 16:90. https://doi.org/10.1186/s12915-018-0559-7
- **S11** CNRS press release on S10 (15-day-old *A. ocellaris*). https://cnrs.fr/en/press/clown-fish-whence-white-stripes
- **S12** Rhodes lab (Univ. Illinois): male-to-female change happens first in the brain; gonads months to years later. https://rhodeslab.beckman.illinois.edu/files/2019/04/active-feminization-of-the-brain.pdf
- **S13** Florida Museum, Clown anemonefish. https://www.floridamuseum.ufl.edu/discover-fish/species-profiles/clown-anemonefish/
- **S14** ORA, Black Ocellaris Clownfish (and retailer FAQs). https://www.orafarm.com/product/black-ocellaris-clownfish/
- **S15** FishBase, *Paracanthurus hepatus*. https://www.fishbase.se/summary/Paracanthurus-hepatus.html
- **S16** Reef Builders 2011, "Yellow belly blue tang". https://reefbuilders.com/2011/10/11/yellow-belly-blue-tang
- **S17** Animal Diversity Web, *Paracanthurus hepatus* (juvenile yellow claim, rejected). https://animaldiversity.org/accounts/Paracanthurus_hepatus
- **S18** Kadota T. 2009. Mating system and sexual pattern of hawkfishes, Kuchierabu-jima (thesis summary, Hiroshima Univ.). https://www.hiroshima-u.ac.jp/system/files/12436/95.pdf ; Kadota et al. 2011, Environ. Biol. Fish. https://doi.org/10.1007/s10641-011-9817-z
- **S19** Awata S. et al. 2010. Reproductive biology of *Salarias fasciatus*, Ichthyological Research. https://link.springer.com/article/10.1007/s10228-009-0142-x
- **S20** Wittenrich M.L. & Munday P.L. 2005. Bi-directional sex change in coral reef fishes from the family Pseudochromidae: an experimental evaluation. Zoological Science 22:797–803. https://researchonline.jcu.edu.au/7274/
- **S21** ORA, Electric Indigo Dottyback (*P. fridmani × sankeyi*). https://www.orafarm.com/product/electric-indigo-dottyback/
- **S22** Seatizens (Seychelles), *Macropharyngodon bipartitus* (after Lieske & Myers). https://seatizens.sc/species/macropharyngodon-bipartitus-smith-1957/
- **S23** FishBase, *Macropharyngodon bipartitus*. https://www.fishbase.se/summary/Macropharyngodon-bipartitus.html
- **S24** Warner R.R. & Swearer S.E. 1991. Social control of sex change in the bluehead wrasse. Biol. Bull. 181:199–204.
- **S25** FishBase, *Valenciennea puellaris*. https://www.fishbase.se/Summary/Valenciennea-puellaris
- **S26** Breeders' Registry, *Valenciennea puellaris* spawning report. https://breedersregistry.org/?p=700
- **S27** Munday P.L., Caley M.J. & Jones G.P. 1998. Bi-directional sex change in a coral-dwelling goby. Behav. Ecol. Sociobiol. 43:371–377.
- **S28** Sea and Reef, Pajama cardinalfish (juvenile spot colour). https://www.seaandreef.com/marine-ornamental-fish/cardinals/pajama-cardinalfish
- **S31** "Priming / gating" of behavioural sex change in the bluehead wrasse, J. Exp. Biol. 221: jeb180901 (2018). https://cob.silverchair.com/jeb/article-pdf/221/23/jeb180901/1905087/jeb180901.pdf
- Choosri S., Phuangsanthia W., Pratoomyot J. 2025. *Valenciennea strigata* sexing by the genital papilla. Food Agric. Sci. Technol. 11(1). https://ph02.tci-thaijo.org/index.php/stej/article/view/252972

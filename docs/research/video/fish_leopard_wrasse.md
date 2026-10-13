# Blue star leopard wrasse (Macropharyngodon bipartitus)

Sim hook: `BrainKind.LEOPARD_WRASSE` · Adult size: 13 cm TL max, owner's fish ~8-9 cm [L] · Tank count: 1 (female / initial phase)

Also sold as vermiculate leopard wrasse, splendid leopard wrasse, divided wrasse. Research update 2026-10-13.

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| H1 | Owner clip, leopard wrasse close-up (portrait, 5.4 s) | uploads `b81b87e7….mov` | 0-2.75 s: female pattern up close, hovering and picking head-down among the Kenya tree branches, pectoral rowing; dottyback, cleaner wrasse and blue tang pass |
| H2 | Owner clip, whole tank (7.3 s) | uploads `89d077ba….mov` | 6 s: spotted fish low near the front sand beside the cleaner wrasse |
| P | Tee's close-up photos, 2026-10-13 (`/mnt/project-files/reeftank/reference/photos_2026-10-13/`) | p06, p09 | p06: full side view of the leopard wrasse on the rock (pattern reference for the texture); p09: rear flank under the tang |
| S1 | Seatizens species page (Seychelles) | https://seatizens.sc/species/macropharyngodon-bipartitus-smith-1957/ | juvenile/female/male colours, 13 cm TL, 1-30 m, burrows in sand to avoid predators |
| S2 | fishprofiles.com | https://fishprofiles.com/profiles/marine/Labroids/Macropharyngodon_bipartitus | 13 cm; wild diet amphipods, copepods, forams, small snails; sleeps buried; ≥ 3 in (7.6 cm) sand; copper intolerant; 24-28 °C |
| S3 | LiveAquaria product page | https://www.liveaquaria.com/product/304/?pcatid=304 | female/juvenile pearly white-orange mottling with light blue spots and large blue-black belly spot; male neon green/blue broken stripes |
| S4 | Fenner, "Leopards for the Reef", TFH | https://www.tfhmagazine.com/articles/saltwater/leopards-for-the-reef-full-article | burrows at night or when frightened; new fish may stay in the sand for days (circadian re-entrainment after shipping); fine sugar-sized sand; protogynous; hawkfish and dottybacks may compete; add leopards first; starve easily |
| S5 | LiveAquaria blog, leopard wrasses | https://www.liveaquaria.com/blogs/species-spotlight/leopard-wrasse-of-the-genus-macropharyngodon | "constantly on the prowl" for small inverts; bobbing-and-weaving methodical swim; ≥ 2 in sand; hawkfish, tangs, dwarf angels bully them; jumper |
| S6 | Tatom-Naecker & Westneat, SICB abstract P2-191 (Halichoeres bivittatus, proxy) | https://sicb.org/?p=15256 | sand dive: head-first plunge then undulation, 1.05-3.06 s; phase 1 body ~perpendicular to the sand, phase 2 parallel |
| S7 | Halichoeres trimaculatus sand/light rhythm study, Chronobiol. Int. 34(8):1014-1025 (2017) (proxy) | https://sciwatch.kiost.ac.kr/handle/2020.kiost/38955 | buries at night; light exposure at morning emergence entrains the daily rhythm; without light cues emergence drifts later each day |
| S8 | Wainwright, Bellwood & Westneat 2002, Env. Biol. Fish. 65:47-62 | https://fishlab.ucdavis.edu/wp-content/uploads/sites/397/2020/06/Wainwright-et-al-2002.pdf | labrids swim on the pectorals: rowing (low aspect ratio fin, slow manoeuvring) to flapping (high AR, fast) |
| S9 | FishBase M. meleagris (congener) | https://fishbase.se/summary/4984 | genus eats mainly gastropods, hard-shelled prey and foraminiferans |

Cite as `[H1 1.25 s]` or `[P p06]` (Tee's photos 2026-10-13). Tags: **[H]** seen in the owner's footage or photos, **[M]** stated by a source, **[L]** estimate. The photos beat the literature and the video stills for colour.

## Colour and pattern (what the model paints)
| Part | Observation | Cite |
|---|---|---|
| Body | orange-brown (rusty amber, darker on the back), densely covered in small pale-blue dots from the nape to the tail base | [P p06, p09] [H]; female "orange-red with white spots of variable size" [S1] [M] |
| Chest / belly | a black blotch behind the pectoral, from just behind the head to about mid-body, up to just below mid-side, covered by a bright blue-white mesh (pale network around black islands) | [P p06] [H]; "large black area over chest and abdomen … bright iridescent blue network" [S1] [M] |
| Head | yellow-tan with dark lines (a line from the mouth through the eye, one along the jaw, finer mesh over the cheek) and a pale patch behind the eye; no blue dots | [P p06] [H] |
| Dorsal / anal / caudal | orange-brown like the body, with pale-blue dots; caudal rounded | [P p06] [H] |
| Pectorals | clear, almost invisible; rowing | [P p06] [H]; [H1] [H] |
| Video stills | under the tank lights the body reads more golden and the head greener [H1 1.25-2.25 s]; the photos are the colour reference | [H1] [H] |
| Male (not in tank) | duller orange-red, green dark-edged diagonal bands on the head breaking into spots, yellow-orange patch under the soft dorsal | [S1] [M] |
| Juvenile | white with orange mottling, a pair of black spots on dorsal and anal fins | [S1] [M] |
| Body shape | deep-bodied, compressed, small terminal mouth, steep head profile; depth ≈ 0.38 SL | [P p06] [H] / [L] |

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | labriform: pectoral rowing, body nearly stiff, tail held folded and used only to steer and for bursts | `Gait.LABRIFORM`, U(p-c) 2.0 BL/s, row body amp 0.02 | [H1] [H]; [S8] [M] |
| Cruise speed | slow methodical stalking between picks | 0.8 BL/s cruise | [S5] [M] / [L] |
| Burst / startle | darts into the sand (head-first dive) rather than out into open water | burst 5 BL/s, dive 1-3 s | [S4][S6] [M] |
| Fin beat | pectorals 3-5 Hz when hovering | pecHzHover 3.0, pecHzMax 5.5 | [L] |
| Body posture & pitch | hovers head-down 30-60° while inspecting branches, picks, then levels off; bobbing weave | pick pitch −0.6 rad, bob 0.06 rad @ 1.6 Hz | [H1 0-2 s] [H]; [S5] [M] |
| Idle / hover | hangs in place within a body length of the Kenya tree, turning on the spot | | [H1] [H] |
| Turning | tight pivots on the pectorals | 260°/s | [H1] [L] |
| Sand dive | head-first plunge with fast low-amplitude undulation while ~perpendicular to the sand, then larger slow waves parallel to it until covered; 2-5 cm deep | 1.05-3.06 s dive; buried 2-5 cm; leaves a small crater with a rim | [S6] [M]; depth [L] |
| Colour changes | colours fade when unhappy | desat 0.3 when stressed | [S2] [M] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | lower-mid water among coral branches and rock, never far from sand | zone 0.05-0.55, home 40 cm around the Kenya tree | [H1][H2] [H] |
| Feeding | picks almost continuously: pods, amphipods, forams, tiny snails off rock, coral branches and sand | pick every 3-6 s while foraging (10-20 picks/min); intake scaled by rock prey index | [H1] [H]; [S2][S5][S9] [M]; rate [L] |
| Prepared food | many individuals learn frozen mysis/cyclops; thin fish rarely recover | 70 % accept prepared food; starves in ~2-3 weeks on pod-poor rock | [S4][S5] [M]; numbers [L] |
| Day / night routine | dives into the sand as the main lights go out; emerges in the morning after the lights come on, with a lag | dive at dusk light < 0.25; emerge 15-60 min after lights-on | [S4] [M]; [S7] [M proxy]; lag [L] |
| Stress burial | stays buried for days after introduction; dives when chased or startled | after arrival: buried 1-4 days, surfacing only briefly; big startle → dive 5-20 min | [S4] [M]; times [L] |
| Social / interactions | peaceful, ignores corals and most inverts; small snails and worms are prey; hawkfish and dottyback may harass it; one per tank (females can turn male) | wary of HAWK / DOTTYBACK; no aggression to others | [S4][S5] [M] |
| Reaction to parameters | sensitive to water chemistry swings; intolerant of copper; jumper | hardiness 0.4; copper lethal | [S2][S4][S5] [M] |
| Distinctive tells | orange-brown body peppered with pale-blue dots; black belly blotch with a blue-white mesh; yellow-tan head with dark lines; constant head-down picking in the coral; vanishes into the sand at lights-out | | [P p06] [H1] [H] |

## Priority fixes for Opus
1. Head-down hover-and-pick among the Kenya tree, many picks a minute.
2. Sand dive at lights-out (crater + small mound), invisible until after lights-on.
3. Hides in the sand for days after it is added.

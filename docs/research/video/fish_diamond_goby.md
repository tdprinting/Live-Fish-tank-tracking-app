# Diamond goby (Valenciennea puellaris)

Sim hook: `BrainKind.DIAMOND_GOBY` · Adult size: 13-18 cm SL wild, owner's fish ~10 cm [L] · Tank count: 1 (now in the owner's tank preset)

Extends research 16 (`docs/research/16_diamond_goby.md`) and `sand_bioturbation.md`. Research update 2026-10-13.

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| H1 | Owner clip, whole tank (portrait, 10.8 s) | uploads `edf71e08….mov` | 7.0 s: the goby lying on the front sand in front of the rock, side view |
| H2 | Owner clip, whole tank (7.3 s) | uploads `89d077ba….mov` | 3-5 s: pale fish on the left front sand |
| P | Tee's close-up photos, 2026-10-13 (`/mnt/project-files/reeftank/reference/photos_2026-10-13/`) | p07, p08 | p08: full side view on the sand (pattern reference for the texture); p07: head-on from above near the rock |
| S1 | Fishes of Australia 177 | https://fishesofaustralia.net.au/Home/species/177 | "pale brownish to greyish", orange dashes along the upper side, a blue-edged orange stripe along the lower side, rows of pale blue dashes on the head; 18 cm SL; pairs in a shallow burrow under rubble |
| S2 | FishBase 7246 | https://www.fishbase.se/summary/7246 | 20 cm SL max, 2-84 m, pairs |
| S3 | fishprofiles.com | https://fishprofiles.com/profiles/marine/Blennoid_Gobioid/Valenciennea_puellaris | sifts sand all day; burrows under rock; starves in an immature tank |
| S4 | Reef Builders, V. helsdingenii spotlight (congener) | https://reefbuilders.com/2015/07/11/awesome-fish-spotlight-valenciennea-helsdingenii | sleeper gobies "up to 260 mouthfuls in a 10 minute window" (upper bound, ≈ 26/min) |
| S5 | Univ. Copenhagen news: gobies and the coral food web | https://www1.bio.ku.dk/english/news/news/what-is-going-on-in-the-coral-food-web/ | sand-sifting gobies cut meiofauna abundance by up to 46 % in aquarium trials |
| S6 | Wikipedia: Valenciennea | https://en.wikipedia.org/wiki/Valenciennea | engulf sand, expel it through the gill openings, gill rakers retain prey |
| S7 | aquariadise / aquariumsource (see research 16) | https://aquariadise.com/diamond-goby/ | sand on low corals; one or a mated pair; jumper |

## Colour and pattern (model check)
Cite `[P p0N]` = Tee's photos 2026-10-13; they are the colour reference and beat the literature.

| Part | Observation | Model before → after | Cite |
|---|---|---|---|
| Ground colour | pale lavender-white, a little more lilac on the back, bright belly | was cool white with a grey back; now lavender-white | [P p08] [H]; [S1] "pale brownish to greyish" [M] |
| Mid-side | a bold row of orange dashes (each ~0.03 L, spaced ~0.06 L) along the middle of the side | was a single low row of short dashes plus 55 random spots; now this row | [P p08] [H]; [S1] [M] |
| Upper side | a second, finer row of small orange dashes along the back | added | [P p08] [H] |
| Lower side | a continuous orange line from the cheek to the tail base | was dashes; now continuous (no visible blue edge in Tee's fish) | [P p08] [H]; [S1] "blue-edged orange stripe" [M] |
| Head | orange spots over the top of the head and behind the eye; blue iridescent spots and short stripes on the cheek under the eye, above the orange line | was a pale blue cheek band; now orange head spots + blue cheek spots/streaks | [P p08, p07] [H]; [S1] "pale blue dashes on the head" [M] |
| Eye | high-set, dark pupil with a gold-orange ring | kept | [P p08] [H] |
| Fins | translucent, faint orange spotting on the dorsals and caudal | alpha lowered (0.5 → 0.3-0.35), margins softened | [P p08] [H] |
| Shape | elongate, blunt rounded head, two separate dorsals, rests on the sand propped on the pelvic disc | kept | [P p08] [H]; [H1] [H] |

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | rests on the sand, short hover-hops: a few tail beats then settles (no swim bladder function) | `Gait.PERCH_HOP`, sink 2 cm/s (unchanged) | [H1] [H]; research 16 |
| Cruise speed | slow glide over the sand between stations | 0.8 BL/s | [L] |
| Burst / startle | darts into the burrow | 5 BL/s | [S3] [M] |
| Sifting cycle | scoop (head down) → hover and chew (gill flare) → puff out of the gills → shuffle 1-2 cm | 0.6 s scoop, 1.5-3.5 s chew; ~9 mouthfuls/min on average incl. hops; field upper bound 26/min | [S4][S6] [M]; [L] |
| Posture | head slightly up while resting, nose-down to scoop | | [H1] [H] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | front and middle sand, burrow under a rock | home 35 cm around the burrow | [H1][H2] [H]; [S1] [M] |
| Day / night routine | in the burrow at night | unchanged | [S1] [M] |
| Feeding | sifts all day; depletes meiofauna | `SAND_SIFTER` pod predator; [S5] supports a large drawdown | [S3][S5] [M] |
| Social / interactions | ignores tankmates; the sand puffs bury low corals | unchanged (research 16) | [S7] [M] |
| Distinctive tells | lavender-white goby on the sand with orange dash rows and a blue-spotted cheek; sand pouring out of the gills | | [P p08] [H1] [H] |

## Priority fixes for Opus
1. Repaint from Tee's photos: lavender-white, two rows of orange dashes, continuous orange lower line, orange head spots, blue cheek spots, translucent fins (done in this pass).
2. In the owner's tank preset (done in this pass).
3. Its moves on the bed (travel, burrow work, the dash into the burrow, lying in it) are kinematic glides that keep 3 mm off the rock, and its rock clearance follows its girth (3 cm cap, like the engineer goby), so it can reach a burrow mouth under the rock (done in this pass).

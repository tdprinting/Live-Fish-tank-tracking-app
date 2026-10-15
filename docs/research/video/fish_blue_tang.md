# Blue tang, juvenile (Paracanthurus hepatus)

Sim hook: `BrainKind.TANG` · Adult size: ~25-30 cm (juvenile 5-8 cm) [L] · Tank count: n/a (see sim)

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| S1 | Furrycritter juvenile blue tang | https://www.furrycritter.com/pages/life_stages/fish/blue_tang_juvenile.htm | young tangs lie on side motionless when startled/stressed |
| S2 | Nano-Reef "blue tang laying on side" | https://www.nano-reef.com/forums/topic/420062-blue-tang-laying-on-side/ | hobbyist reports; stress/ich watch |
| S3 | Do Tangs Play Dead? (enviroliteracy; species labelling unreliable) | https://enviroliteracy.org/animals/do-tangs-play-dead/ | resting at night, lights off |
| S4 | Tee's own tank (owner report, 2026-10-15) | thread "Night feeding video study" | Dori hides under rock or coral to sleep and when scared or nervous; now and then rubs on the rock and races across the tank: "they are playful fish" [H] |
| S5 | FishBase species summary, *Paracanthurus hepatus* | https://www.fishbase.se/summary/Paracanthurus-hepatus.html | lives in groups around branching coral heads and retreats into them when frightened [M] |
| S9 | Fulton et al. 2005 (proxy, labriform reef fishes) | https://researchonline.jcu.edu.au/2857/ | pectoral swimmers (Pomacentridae/Acanthuridae/Labridae) hold ~70% of max speed in field; fin aspect ratio predicts speed [M, proxy not species-specific] |

NOTE: No footage was watched. Wikimedia Commons video downloads (candidates found: clownfish CAS-Aquarium-Clownfish&Coral.ogv, Aquarium of Cattolica - Clownfishes.webm; Paracanthurus - August 14 2021.webm; Labroides dimidiatus cleaning Acanthurus mata - Gijon Aquarium) were blocked by HTTP 429 rate limiting from this server, and no usable Commons clip was found for the other species. YouTube rows V1-V3 remain queued for hand review. No rows upgraded to [H]; numbers stay [L] unless a source is cited.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | pectoral swimming (acanthuriform) with caudal for bursts | pec rowing cruise; tail 2-3 Hz fast | [L] |
| Cruise speed | continuous grazing swim | 1-1.5 BL/s | [L] |
| Burst / startle | very fast, then shelter | 8-10 BL/s | [L] |
| Tail / fin beat | pecs 3-5 Hz, small tail amplitude | | [L] |
| Body posture | level, disc body; plays dead: rolled 90 deg onto side, rigid | roll lerp to 90 deg over 0.4 s | [S1] [M] |
| Idle | hold at rock face, peck | | [L] |
| Turning | wide, 1 BL radius | | [L] |
| Colour | may pale/dark when stressed or resting | desaturate 20% while "dead" | [S3] [M] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | roams rockwork, wedges into crevice | home range whole tank, 1 favourite crevice | [L] |
| Day / night | rests on side at night | night: wedge in crevice, rolled 30-90 deg | [S3] [M] |
| Feeding | grazes algae/film continuously, takes mysis/pellets | bite every 2-4 s on rock | [L] |
| Social | solitary-juvenile tolerant; avoid aggressive dottyback | flee when chased | [L] |
| V1 | Dory Nemo Blue Hippo Tang Fish Play Dead & Lives (YouTube) | https://www.youtube.com/watch?v=atsSFjIBnoQ | title: blue tang playing dead (blue light) [M] |
| V2 | blue tang is breathing fast, hiding, laying on her side (YouTube) | https://www.youtube.com/watch?v=sDAZ-2xXNrQ | sick tang on side; velvet - not feigning [M] |
| V3 | Surgeonfish (Acanthurus coeruleus) Juvenile - Bonaire (YouTube) | https://www.youtube.com/watch?v=2d91klKL7-Y | wild juvenile Atlantic blue tang (different species) [M] |
| Reaction | startle -> motionless on side several seconds | trigger: sudden approach by larger fish; 3-8 s hold, then resume | [S1] [M] |
| Tells | lying on side, wedged in rock; electric-blue + yellow tail | | |

## Hiding and play (implemented, `ai/TangShelter.kt`)
| Behaviour | Rule | Cite |
|---|---|---|
| Shelters | Points with rock straight above within 14 cm and >= 50 % of the 26 cover rays hitting rock, found once from the rock SDF; spread >= 6 cm apart | [S4][S5], [L] |
| No clipping | Every pose is checked at nose, tail, dorsal and anal fin tips and both flanks (half-extents 0.48 / 0.27 / 0.08 of length); it enters along a straight line cleared for that exact pose, from a doorway with room to turn | Tee's no-clipping rule |
| Scared / nervous (`tanghide`) | After a hard tap, an attack or a predator in view (or fear/stress > 0.35): tucks under the nearest shelter away from the scare, rolled ~35 deg, a little pale, 1-4 min; peeks out a quarter body length every 10-25 s; glides out once calm | [S4][S5], [L] |
| Sleep | Its night bed is the shelter nearest the SLEEP_TANG anchor that fits its body rolled ~70 deg; it glides back out at dawn or when food wakes it | [S3][S4] |
| Flee | Darts to the doorway of the best shelter away from the threat, then hides | [S5] |
| Rub (`tangrub`) | Every ~8-25 min by day when calm, healthy and not hungry: flank to a steep rock face, rolled ~25 deg, slides 5 cm in ~1 s, pushes off. Separate from the itchy "flash" (`Flash`, ich/velvet only) | [S4], [L] |
| Race (`tangzoom`) | Every ~6-20 min by day when calm: a dash at 0.9 x burst speed along the open front lane to the far end, 30 % of the time straight back | [S4], [L] |

Not yet: hiding in coral. Corals have no collision shapes on main, so a tang can't tuck into the Kenya tree without
clipping; shelters are rock only until coral collision lands.

## Priority fixes for Opus
1. Add "play dead" state: roll to side, motionless 3-8 s after startle, at night as sleep pose.
2. Crevice wedging at night (rolled, rigid).
3. Pectoral-driven constant grazing swim along rock faces.

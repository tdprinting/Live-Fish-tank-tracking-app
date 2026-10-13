# Blue tang, juvenile (Paracanthurus hepatus)

Sim hook: `BrainKind.TANG` · Adult size: ~25-30 cm (juvenile 5-8 cm) [L] · Tank count: n/a (see sim)

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| S1 | Furrycritter juvenile blue tang | https://www.furrycritter.com/pages/life_stages/fish/blue_tang_juvenile.htm | young tangs lie on side motionless when startled/stressed |
| S2 | Nano-Reef "blue tang laying on side" | https://www.nano-reef.com/forums/topic/420062-blue-tang-laying-on-side/ | hobbyist reports; stress/ich watch |
| S3 | Do Tangs Play Dead? (enviroliteracy; species labelling unreliable) | https://enviroliteracy.org/animals/do-tangs-play-dead/ | resting at night, lights off |

NOTE: Video links (V1-V3) found via search listings only; titles/snippets seen, footage not viewed, so listed for hand review. Page-based claims are [M]; numbers are [L] estimates.

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

## Priority fixes for Opus
1. Add "play dead" state: roll to side, motionless 3-8 s after startle, at night as sleep pose.
2. Crevice wedging at night (rolled, rigid).
3. Pectoral-driven constant grazing swim along rock faces.

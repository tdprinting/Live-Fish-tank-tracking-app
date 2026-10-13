# Ocellaris clownfish (Amphiprion ocellaris)

Sim hook: `BrainKind.CLOWN` · Adult size: ~8-11 cm [L] · Tank count: 2 (orange + black morph)

## Sources
| # | Video / page | URL | What it shows |
|---|---|---|---|
| S1 | Top Shelf Aquatics care page | https://topshelfaquatics.com/pages/saltwater-fish/ocellaris-clownfish | no anemone needed; rock/hides reduce stress |
| S2 | ReefCentral "Clowns Hosting" | https://forums.reefcentral.com/threads/clowns-hosting.538834 | tank-raised clowns host in corals, powerheads or nothing |
| S3 | Nano-Reef soft coral hosting thread | https://www.nano-reef.com/forums/topic/20451-what-is-the-best-soft-coral-for-a-clown-fish-to-host-in/?page=0 | hosting in toadstool/hammer/frogspawn anecdotes |

NOTE: Video links (V1-V3) found via search listings only; titles/snippets seen, footage not viewed, so listed for hand review. Page-based claims are [M]; numbers are [L] estimates.
Cite as [S1]. Tags: **[H]** footage, **[M]** source states, **[L]** inferred.

## Animation spec (for the renderer)
| Aspect | Observation | Suggested sim value | Cite |
|---|---|---|---|
| Locomotion mode | pectoral-dominant (labriform) with caudal bursts, short hops | pectoral flap + small tail wag | [L] |
| Cruise speed | slow, hugs host | 0.5-1 BL/s | [L] |
| Burst / startle | fast dart back to host | 6-8 BL/s, 0.3 s | [L] |
| Tail / fin beat | pectorals constantly fanning | pecs 4-6 Hz; tail 1-2 Hz cruise | [L] |
| Body posture & pitch | slightly nose-down tilt near host, wobbly rolling gait | pitch -5..-15 deg; roll wobble +-5 deg | [L] |
| Idle / hover | hovers in/over host, fans pectorals | hover radius <1 BL of host | [L] |
| Turning | tight, pivots on pectorals | radius ~0.5 BL | [L] |
| Colour | stable; black morph has same shape | none needed | [L] |

## Behaviour spec (for the AI)
| Aspect | Observation | Suggested rule / numbers | Cite |
|---|---|---|---|
| Zone & territory | anemone optional; hosting is individual and unpredictable | anchor = soft coral (toadstool/hammer-type) else powerhead/rock overhang; wander radius 4-6 BL | [S1][S2][S3] [M] |
| Day / night | diurnal; sleeps in host | night: tuck into anchor, still | [L] |
| Feeding | picks plankton/pellets mid-water near host | dart out <=10 BL, return | [L] |
| Social | pair, larger = female; dominant chases subordinate; defends host from other fish | female bully-chases male briefly; both bob at intruders | [L] |
| V1 | Ocellaris Clownfish A Peaceful Life Inside a Sea Anemone (documentary) (YouTube) | https://www.youtube.com/watch?v=3h8vUH2bQ7g | title: ocellaris living among anemone tentacles in wild [M] |
| V2 | Keeping NEMO: Are Clownfish GOOD PETS? (YouTube) | https://www.youtube.com/watch?v=FycT31VLXg8 | snippet: ocellaris out and swimming most of the time [M] |
| V3 | Gracefully Dancing Clownfish (YouTube) | https://www.youtube.com/watch?v=o9wN5Jh8RJc | title only; clownfish swimming/dancing [M] |
| Reaction to parameters | no data | flow: stays lee side of host | [L] |
| Distinctive tells | host-wiggle (rubbing body in coral), rolling wobble gait, mid-water bobbing | see below | [L] |

## Priority fixes for Opus
1. Tether each clown to an anchor (soft coral) with a wiggle-into-host idle; without anemone, 3-4 BL leash.
2. Pectoral-rowing wobble with nose-down tilt, not a streamlined body-undulation swim.
3. Pair dominance: bigger one chases smaller one; both charge any intruder near the anchor then return.
